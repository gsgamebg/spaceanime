package app.anim.ads

import androidx.compose.runtime.Composable

enum class AdPlacement { Home, Catalog }

data class AdsConfiguration(
    val enabled: Boolean = false,
    val homeBanner: Boolean = false,
    val catalogBanner: Boolean = false,
) {
    fun allows(placement: AdPlacement): Boolean =
        enabled && when (placement) {
            AdPlacement.Home -> homeBanner
            AdPlacement.Catalog -> catalogBanner
        }
}

internal fun adsMayBeRequested(
    consentRefreshSucceeded: Boolean,
    formCompleted: Boolean,
    canRequestAds: Boolean,
): Boolean = consentRefreshSucceeded && formCompleted && canRequestAds

@Composable
expect fun AdBannerSlot(placement: AdPlacement)
