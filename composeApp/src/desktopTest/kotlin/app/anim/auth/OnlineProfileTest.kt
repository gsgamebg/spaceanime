package app.anim.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

class OnlineProfileTest {
    @Test fun `reads a profile or absence`() {
        assertNull(parseOnlineProfile("[]"))
        assertEquals(OnlineProfile("Gabriel", "Olá"), parseOnlineProfile("""[{"display_name":"Gabriel","biography":"Olá"}]"""))
        assertEquals(OnlineProfile("Gabriel", "Olá", "nova", "cosmos"), parseOnlineProfile("""[{"display_name":"Gabriel","biography":"Olá","avatar_asset_id":"nova","banner_asset_id":"cosmos"}]"""))
        assertEquals(OnlineProfile("Gabriel", "Olá"), parseOnlineProfile("""[{"display_name":"Gabriel","biography":"Olá","avatar_asset_id":"desconhecido","banner_asset_id":"desconhecido"}]"""))
    }
    @Test fun `rejects malformed or incompatible profile data`() {
        assertFails { parseOnlineProfile("""[{"display_name":"","biography":""}]""") }
        assertFails { parseOnlineProfile("""[{"display_name":"${"a".repeat(25)}","biography":""}]""") }
        assertFails { parseOnlineProfile("""[{"display_name":"User","biography":"${"b".repeat(141)}"}]""") }
        assertFails { parseOnlineProfile("""[{"display_name":"A","biography":""},{"display_name":"B","biography":""}]""") }
        assertFails { parseOnlineProfile("""[{"display_name":"A\nB","biography":""}]""") }
    }
}
