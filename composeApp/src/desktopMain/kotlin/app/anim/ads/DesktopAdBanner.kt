package app.anim.ads

import androidx.compose.runtime.Composable

/** The Windows build shows no ads. */
@Composable
actual fun AdBannerSlot(placement: AdPlacement) = Unit

private object NoAds : AdsController {
    override fun adFreeMinutesLeft(): Int = 0
    override val rewardedReady: Boolean = false
    override val rewardedPassMinutes: Int = 0
    override fun onEpisodeOpened(adFree: Boolean) = Unit
    override fun showRewarded() = Unit
}

@Composable
actual fun rememberAdsController(): AdsController = NoAds
