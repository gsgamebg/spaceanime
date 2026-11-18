package app.anim.premium

import kotlin.test.Test
import kotlin.test.assertFalse

class PremiumPlansTest {
    @Test
    fun placeholderPlansDoNotPromiseAdFreeAccess() {
        assertFalse(plans.any { plan -> plan.benefits.any { it == "Sem anúncios" } })
    }
}
