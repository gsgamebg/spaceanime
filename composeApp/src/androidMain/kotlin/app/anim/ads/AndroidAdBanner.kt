package app.anim.ads

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Process-wide ad state: remote config (refreshed every 15 min and cached), the rewarded ad-free
 * pass, the episode counter for interstitials and the Start.io SDK. Nothing loads until the
 * remote config enables it and the consent flow allows ads.
 */
internal class AndroidAdsRuntime private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = AdsConfigClient()
    private var started = false
    private var activity = WeakReference<Activity>(null)
    private var interstitial: StartAppAd? = null
    private var rewardedAd: StartAppAd? = null

    var configuration by mutableStateOf(preferences.getString(KEY_CONFIG, null)?.let(::parseAdsConfiguration))
        private set
    var adFreeUntil by mutableLongStateOf(preferences.getLong(KEY_PASS_UNTIL, 0L))
        private set
    var rewardedReady by mutableStateOf(false)
        private set
    var startIoReady by mutableStateOf(false)
        private set

    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (true) {
                client.fetch()?.let { (raw, parsed) ->
                    preferences.edit().putString(KEY_CONFIG, raw).apply()
                    configuration = parsed
                }
                delay(CONFIG_REFRESH_INTERVAL_MS)
            }
        }
    }

    /** Initializes Start.io and preloads the full-screen formats. Call only after consent allows ads. */
    fun prepareStartIo(host: Activity) {
        activity = WeakReference(host)
        val config = configuration ?: return
        if (!config.enabled || config.provider != AdProvider.StartIo) return
        val appId = appContext.getString(app.anim.R.string.startio_app_id)
        if (appId.isBlank()) return
        if (!startIoReady) {
            StartAppSDK.setUserConsent(appContext, "pas", System.currentTimeMillis(), true)
            if ((appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) StartAppSDK.setTestAdsEnabled(true)
            StartAppSDK.init(appContext, appId, false)
            StartAppAd.disableSplash()
            StartAppAd.disableAutoInterstitial()
            startIoReady = true
        }
        if (config.interstitials && interstitial == null) loadInterstitial(host)
        if (config.rewarded && rewardedAd == null) loadRewarded(host)
    }

    private fun loadInterstitial(host: Activity) {
        val ad = StartAppAd(host)
        interstitial = ad
        ad.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
            override fun onReceiveAd(ad: Ad) = Unit
            override fun onFailedToReceiveAd(ad: Ad?) { scope.launch { delay(RETRY_MS); if (interstitial === ad) interstitial = null } }
        })
    }

    private fun loadRewarded(host: Activity) {
        val ad = StartAppAd(host)
        rewardedAd = ad
        rewardedReady = false
        ad.setVideoListener { grantPass() }
        ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
            override fun onReceiveAd(ad: Ad) { rewardedReady = true }
            override fun onFailedToReceiveAd(ad: Ad?) { rewardedReady = false; scope.launch { delay(RETRY_MS); if (rewardedAd === ad) rewardedAd = null } }
        })
    }

    fun onEpisodeOpened(adFree: Boolean) {
        val count = preferences.getInt(KEY_EPISODES, 0) + 1
        preferences.edit().putInt(KEY_EPISODES, count).apply()
        val config = configuration ?: return
        if (adFree || !config.interstitials || !interstitialDue(count, config.interstitialEveryEpisodes)) return
        val ad = interstitial ?: return
        if (ad.isReady && ad.showAd()) {
            interstitial = null
            activity.get()?.let(::loadInterstitial)
        }
    }

    fun showRewarded() {
        val ad = rewardedAd ?: return
        if (!ad.isReady) return
        if (ad.showAd()) {
            rewardedAd = null
            rewardedReady = false
            activity.get()?.let { host -> scope.launch { delay(RETRY_MS); if (configuration?.rewarded == true) loadRewarded(host) } }
        }
    }

    private fun grantPass() {
        val minutes = configuration?.rewardedPassMinutes ?: 120
        val until = System.currentTimeMillis() + minutes * 60_000L
        adFreeUntil = until
        preferences.edit().putLong(KEY_PASS_UNTIL, until).apply()
    }

    companion object {
        @Volatile private var instance: AndroidAdsRuntime? = null
        fun get(context: Context): AndroidAdsRuntime = instance ?: synchronized(this) {
            instance ?: AndroidAdsRuntime(context).also { instance = it }
        }
    }
}

@Composable
actual fun rememberAdsController(): AdsController {
    val context = LocalContext.current
    val runtime = remember(context.applicationContext) { AndroidAdsRuntime.get(context).also { it.start() } }
    val activity = context.findActivity()
    val consent = remember(context.applicationContext) { AndroidAdsConsentRegistry.get(context.applicationContext) }
    val config = runtime.configuration
    LaunchedEffect(activity, config) {
        if (activity != null && config?.enabled == true && config.provider == AdProvider.StartIo) {
            if (consent.refresh(activity).canRequestAds) runtime.prepareStartIo(activity)
        }
    }
    return remember(runtime) {
        object : AdsController {
            override fun adFreeMinutesLeft(): Int = ((runtime.adFreeUntil - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(0).let { if (runtime.adFreeUntil > System.currentTimeMillis() && it == 0) 1 else it }
            override val rewardedReady: Boolean get() = runtime.rewardedReady && runtime.configuration?.rewarded == true
            override val rewardedPassMinutes: Int get() = runtime.configuration?.rewardedPassMinutes ?: 120
            override fun onEpisodeOpened(adFree: Boolean) = runtime.onEpisodeOpened(adFree)
            override fun showRewarded() = runtime.showRewarded()
        }
    }
}

@Composable
actual fun AdBannerSlot(placement: AdPlacement) {
    val context = LocalContext.current
    val runtime = remember(context.applicationContext) { AndroidAdsRuntime.get(context).also { it.start() } }
    if (LocalAdFree.current) return
    val config = runtime.configuration ?: return
    if (!config.allows(placement)) return
    when (config.provider) {
        AdProvider.AdMob -> AdMobBanner(placement)
        AdProvider.StartIo -> StartIoBanner(runtime)
    }
}

@Composable
private fun StartIoBanner(runtime: AndroidAdsRuntime) {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return
    val consent = remember(context.applicationContext) { AndroidAdsConsentRegistry.get(context.applicationContext) }
    LaunchedEffect(activity) { if (consent.refresh(activity).canRequestAds) runtime.prepareStartIo(activity) }
    if (!consent.snapshot.canRequestAds || !runtime.startIoReady) return
    AndroidView(factory = { Banner(activity) }, modifier = Modifier.fillMaxWidth().height(50.dp))
}

@Composable
private fun AdMobBanner(placement: AdPlacement) {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return
    val consent = remember(context.applicationContext) {
        AndroidAdsConsentRegistry.get(context.applicationContext)
    }
    val consentSnapshot = consent.snapshot
    val scope = rememberCoroutineScope()

    LaunchedEffect(activity, consent) {
        consent.refresh(activity)
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (consentSnapshot.canRequestAds) {
            val unitId = when (placement) {
                AdPlacement.Home -> context.getString(app.anim.R.string.admob_home_banner_id)
                AdPlacement.Catalog -> context.getString(app.anim.R.string.admob_catalog_banner_id)
            }
            if (unitId.isNotBlank()) {
                val adSize = AdSize.BANNER
                val adView = remember(context, unitId) {
                    AdView(context).apply {
                        adUnitId = unitId
                        setAdSize(adSize)
                    }
                }
                DisposableEffect(adView) {
                    adView.loadAd(AdRequest.Builder().build())
                    onDispose { adView.destroy() }
                }
                AndroidView(
                    factory = { adView },
                    modifier = Modifier.fillMaxWidth().height(adSize.height.dp),
                )
            }
        }
        if (consentSnapshot.privacyOptionsRequired) {
            TextButton(onClick = { scope.launch { consent.showPrivacyOptions(activity) } }) {
                Text("Opções de privacidade")
            }
        }
    }
}

private const val PREFERENCES_NAME = "spaceanime_ads_v2"
private const val KEY_CONFIG = "config_json"
private const val KEY_PASS_UNTIL = "ad_free_until"
private const val KEY_EPISODES = "episodes_opened"
private const val CONFIG_REFRESH_INTERVAL_MS = 15 * 60 * 1000L
private const val RETRY_MS = 60_000L
