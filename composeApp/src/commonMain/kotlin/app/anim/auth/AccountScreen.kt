package app.anim.auth
import app.anim.profile.Profiles
import app.anim.notifications.NotificationPreferences
import app.anim.notifications.NotificationSubscription
import androidx.compose.runtime.Composable
import app.anim.premium.PixCharge
import app.anim.premium.PixOrderState
import app.anim.premium.VipStatus
expect class AccountState {
    val isAuthenticated: Boolean
    val email: String?
    val notificationStatusMessage: String?
    val avatarAsset: String
    val bannerAsset: String
    suspend fun saveProfile(name: String, bio: String)
    suspend fun saveArtwork(avatar: String, banner: String)
    suspend fun registerNotificationDevice(deviceId: String, pushToken: String, preferences: NotificationPreferences)
    suspend fun updateNotificationPreferences(deviceId: String, preferences: NotificationPreferences)
    suspend fun syncNotificationSubscriptions(subscriptions: List<NotificationSubscription>)
    suspend fun removeNotificationDevice(deviceId: String)
    suspend fun signOut(): Boolean
    /** VIP membership of the signed-in user (inactive when signed out or unknown). */
    val vip: VipStatus
    suspend fun refreshVip()
    /** Creates a Pix charge for [plan]; throws [app.anim.premium.VipException] with a message to show. */
    suspend fun createPix(plan: String): PixCharge
    suspend fun pixOrderState(orderId: String): PixOrderState
}
@Composable expect fun rememberAccountState(): AccountState
@Composable expect fun AccountScreen(state: AccountState, profiles: Profiles, onBack: () -> Unit, mandatory: Boolean = false)
