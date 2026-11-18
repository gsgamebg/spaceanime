package app.anim.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

internal data class AdsConsentSnapshot(
    val canRequestAds: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
)

internal class AndroidAdsConsent(context: Context) {
    private val appContext = context.applicationContext
    private val consentInformation = UserMessagingPlatform.getConsentInformation(appContext)
    private val operationMutex = Mutex()
    private var refreshCompleted = false
    private var consentRefreshSucceeded = false
    private var adsInitialized = false

    var snapshot by mutableStateOf(AdsConsentSnapshot())
        private set

    suspend fun refresh(activity: Activity): AdsConsentSnapshot = operationMutex.withLock {
        if (refreshCompleted) return@withLock snapshot

        val refreshed = suspendCancellableCoroutine { continuation ->
            consentInformation.requestConsentInfoUpdate(
                activity,
                ConsentRequestParameters.Builder().build(),
                { if (continuation.isActive) continuation.resume(true) },
                { if (continuation.isActive) continuation.resume(false) },
            )
        }
        if (!refreshed) {
            snapshot = AdsConsentSnapshot(privacyOptionsRequired = requiresPrivacyOptions())
            return@withLock snapshot
        }
        consentRefreshSucceeded = true

        val formCompleted = loadRequiredForm(activity)
        val canRequestAds = adsMayBeRequested(
            consentRefreshSucceeded = true,
            formCompleted = formCompleted,
            canRequestAds = consentInformation.canRequestAds(),
        )
        if (canRequestAds) initializeAds()
        refreshCompleted = formCompleted
        snapshot = AdsConsentSnapshot(canRequestAds, requiresPrivacyOptions())
        snapshot
    }

    suspend fun showPrivacyOptions(activity: Activity): AdsConsentSnapshot = operationMutex.withLock {
        val formCompleted = suspendCancellableCoroutine { continuation ->
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
                if (continuation.isActive) continuation.resume(error == null)
            }
        }
        if (formCompleted) refreshCompleted = true
        val canRequestAds = adsMayBeRequested(
            consentRefreshSucceeded = consentRefreshSucceeded,
            formCompleted = formCompleted,
            canRequestAds = consentInformation.canRequestAds(),
        )
        if (canRequestAds) initializeAds()
        snapshot = AdsConsentSnapshot(canRequestAds, requiresPrivacyOptions())
        snapshot
    }

    private suspend fun loadRequiredForm(activity: Activity): Boolean =
        suspendCancellableCoroutine { continuation ->
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                if (continuation.isActive) continuation.resume(error == null)
            }
        }

    private suspend fun initializeAds() {
        if (adsInitialized) return
        suspendCancellableCoroutine { continuation ->
            MobileAds.initialize(appContext) {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
        adsInitialized = true
    }

    private fun requiresPrivacyOptions(): Boolean =
        consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
}

internal object AndroidAdsConsentRegistry {
    @Volatile
    private var current: AndroidAdsConsent? = null

    fun get(context: Context): AndroidAdsConsent = current ?: synchronized(this) {
        current ?: AndroidAdsConsent(context.applicationContext).also { current = it }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
