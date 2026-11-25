package app.anim.premium

import androidx.compose.runtime.compositionLocalOf

/** Active VIP plan of the signed-in user (null when not VIP), provided by App for badges and frames. */
val LocalVipPlan = compositionLocalOf<String?> { null }

/** VIP membership as reported by the backend; [expiresLabel] is already formatted (dd/MM/yyyy). */
data class VipStatus(val active: Boolean = false, val plan: String? = null, val expiresLabel: String? = null)

/** A Pix charge for one VIP plan (30 days, single payment). */
data class PixCharge(
    val orderId: String,
    val plan: String,
    val amountCents: Int,
    val qrCode: String,
    /** PNG bytes of the QR code. */
    val qrPng: ByteArray,
    val ticketUrl: String?,
)

enum class PixOrderState { Pending, Paid, Expired, Failed }

/** User-facing failure while creating or checking a Pix charge. */
class VipException(message: String) : Exception(message)

/** Display name of a plan id (ouro, platina, ametista). */
fun vipPlanName(plan: String?): String = when (plan) {
    "ouro" -> "Ouro"
    "platina" -> "Platina"
    "ametista" -> "Ametista"
    else -> "VIP"
}
