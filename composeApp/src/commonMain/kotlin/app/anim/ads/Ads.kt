package app.anim.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

enum class AdPlacement { Home, Catalog }
enum class AdProvider { AdMob, StartIo }

/** Remote ad switches (Supabase `app_runtime_config`). Everything defaults to off. */
data class AdsConfiguration(
    val enabled: Boolean = false,
    val homeBanner: Boolean = false,
    val catalogBanner: Boolean = false,
    val provider: AdProvider = AdProvider.AdMob,
    val interstitialEnabled: Boolean = false,
    val interstitialEveryEpisodes: Int = 3,
    val rewardedEnabled: Boolean = false,
    val rewardedPassMinutes: Int = 120,
) {
    fun allows(placement: AdPlacement): Boolean =
        enabled && when (placement) {
            AdPlacement.Home -> homeBanner
            AdPlacement.Catalog -> catalogBanner
        }

    /** Interstitials and rewarded videos are Start.io formats only. */
    val interstitials: Boolean get() = enabled && provider == AdProvider.StartIo && interstitialEnabled
    val rewarded: Boolean get() = enabled && provider == AdProvider.StartIo && rewardedEnabled
}

/** True when the user must not see ads: active VIP or a rewarded ad-free pass. Provided by App. */
val LocalAdFree = compositionLocalOf { false }

/** The app-wide [AdsController], provided by App (null in previews and tests). */
val LocalAdsController = compositionLocalOf<AdsController?> { null }

/** Whether this episode opening should show an interstitial (every [every] openings, never the first). */
fun interstitialDue(openedCount: Int, every: Int): Boolean = every > 0 && openedCount > 0 && openedCount % every == 0

internal fun adsMayBeRequested(
    consentRefreshSucceeded: Boolean,
    formCompleted: Boolean,
    canRequestAds: Boolean,
): Boolean = consentRefreshSucceeded && formCompleted && canRequestAds

/** Full-screen formats and the rewarded ad-free pass. A no-op on Windows. */
interface AdsController {
    /** Minutes left on the rewarded ad-free pass (0 when none). */
    fun adFreeMinutesLeft(): Int
    /** A rewarded video is enabled remotely and loaded, ready to show. */
    val rewardedReady: Boolean
    val rewardedPassMinutes: Int
    /** Counts an episode opening and shows an interstitial when due (unless [adFree]). */
    fun onEpisodeOpened(adFree: Boolean)
    /** Shows the rewarded video; completing it grants the ad-free pass. */
    fun showRewarded()
}

@Composable expect fun rememberAdsController(): AdsController

@Composable
expect fun AdBannerSlot(placement: AdPlacement)
