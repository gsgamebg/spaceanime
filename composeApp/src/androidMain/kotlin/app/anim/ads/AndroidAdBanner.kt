package app.anim.ads

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
actual fun AdBannerSlot(placement: AdPlacement) {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    var configuration by remember(context) {
        mutableStateOf(readCachedConfiguration(preferences))
    }
    val client = remember { AdsConfigClient() }

    LaunchedEffect(context, client) {
        while (true) {
            try {
                val latest = client.fetch()
                if (latest != null) {
                    configuration = if (saveCachedConfiguration(preferences, latest)) latest else null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
            delay(CONFIG_REFRESH_INTERVAL_MS)
        }
    }

    if (configuration?.allows(placement) == true) {
        AndroidAdBanner(placement)
    }
}

@Composable
private fun AndroidAdBanner(placement: AdPlacement) {
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

    Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
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

private fun readCachedConfiguration(preferences: android.content.SharedPreferences): AdsConfiguration? {
    val values = preferences.all
    if (values.isEmpty()) return null
    if (values.keys != STORED_KEYS || values[KEY_SCHEMA] != CONFIG_SCHEMA_VERSION) return null
    val enabled = values[KEY_ENABLED] as? Boolean ?: return null
    val home = values[KEY_HOME] as? Boolean ?: return null
    val catalog = values[KEY_CATALOG] as? Boolean ?: return null
    return AdsConfiguration(enabled = enabled, homeBanner = home, catalogBanner = catalog)
}

private fun saveCachedConfiguration(
    preferences: android.content.SharedPreferences,
    configuration: AdsConfiguration,
): Boolean = preferences.edit()
    .putInt(KEY_SCHEMA, CONFIG_SCHEMA_VERSION)
    .putBoolean(KEY_ENABLED, configuration.enabled)
    .putBoolean(KEY_HOME, configuration.homeBanner)
    .putBoolean(KEY_CATALOG, configuration.catalogBanner)
    .commit()

private const val PREFERENCES_NAME = "spaceanime_ads_config"
private const val CONFIG_REFRESH_INTERVAL_MS = 15 * 60 * 1000L
private const val CONFIG_SCHEMA_VERSION = 1
private const val KEY_SCHEMA = "schema_version"
private const val KEY_ENABLED = "enabled"
private const val KEY_HOME = "home_banner"
private const val KEY_CATALOG = "catalog_banner"
private val STORED_KEYS = setOf(KEY_SCHEMA, KEY_ENABLED, KEY_HOME, KEY_CATALOG)
