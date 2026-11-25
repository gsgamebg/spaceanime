package app.anim.premium

import app.anim.auth.parseVipStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PremiumPlansTest {
    /** Benefits the app actually delivers; anything else would be a false promise in a paid plan. */
    private val delivered = Regex("Sem anúncios|Selo VIP (Ouro|Platina|Ametista) no perfil e nos comentários|Moldura (dourada|platina|ametista animada) no avatar|Comentários em destaque")

    @Test fun plansOnlyPromiseDeliveredBenefits() {
        plans.forEach { plan ->
            assertTrue(SEM_ANUNCIOS in plan.benefits, "${plan.name} must remove ads")
            plan.benefits.forEach { assertTrue(delivered.matches(it), "Undelivered benefit: $it") }
        }
        assertEquals(setOf("ouro" to "10", "platina" to "20", "ametista" to "30"), plans.map { it.id to it.price }.toSet())
    }

    @Test fun vipStatusIsActiveOnlyWithKnownPlanAndDate() {
        fun parse(text: String) = parseVipStatus(Json.parseToJsonElement(text).jsonObject)
        val active = parse("""{"active":true,"plan":"ametista","expires_at":"2026-12-25T12:00:00+00:00"}""")
        assertTrue(active.active); assertEquals("ametista", active.plan); assertTrue(active.expiresLabel!!.endsWith("/2026"))
        assertTrue(parse("""{"active":true,"plan":"platina","expires_at":"2026-12-25 12:00:00.123+00"}""").active)
        assertFalse(parse("""{"active":true,"plan":"diamante","expires_at":"2026-12-25T12:00:00Z"}""").active)
        assertFalse(parse("""{"active":false,"plan":null,"expires_at":null}""").active)
    }
}
