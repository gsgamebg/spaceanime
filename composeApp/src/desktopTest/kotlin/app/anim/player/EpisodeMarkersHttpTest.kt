package app.anim.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

class EpisodeMarkersHttpTest {
    private val key = EpisodeMarkerKey("anitube", "series/1", "episode/2")

    @Test fun validMarkerPayloadParsesOnlyCompleteIntervals() {
        val markers = parseEpisodeMarkers(
            """{"source_id":"anitube","content_id":"series/1","episode_id":"episode/2","intro_start_ms":null,"intro_end_ms":null,"outro_start_ms":900000,"outro_end_ms":960000}""",
            key,
        )

        assertEquals(null, markers?.intro)
        assertEquals(Segment(900000, 960000), markers?.outro)
    }

    @Test fun invalidMarkerPayloadIsRejected() {
        val halfInterval = """{"source_id":"anitube","content_id":"series/1","episode_id":"episode/2","intro_start_ms":1000,"intro_end_ms":null,"outro_start_ms":null,"outro_end_ms":null}"""
        val negativeInterval = """{"source_id":"anitube","content_id":"series/1","episode_id":"episode/2","intro_start_ms":-1,"intro_end_ms":1000,"outro_start_ms":null,"outro_end_ms":null}"""
        val reversedInterval = """{"source_id":"anitube","content_id":"series/1","episode_id":"episode/2","intro_start_ms":1000,"intro_end_ms":1000,"outro_start_ms":null,"outro_end_ms":null}"""
        val wrongKey = """{"source_id":"anitube","content_id":"another/series","episode_id":"episode/2","intro_start_ms":null,"intro_end_ms":null,"outro_start_ms":null,"outro_end_ms":null}"""

        assertFails { parseEpisodeMarkers(halfInterval, key) }
        assertFails { parseEpisodeMarkers(negativeInterval, key) }
        assertFails { parseEpisodeMarkers(reversedInterval, key) }
        assertFails { parseEpisodeMarkers(wrongKey, key) }
        assertFails { parseEpisodeMarkers("not-json", key) }
        assertFails { parseEpisodeMarkers("""{"source_id":"invalid","content_id":"series/1","episode_id":"episode/2","intro_start_ms":null,"intro_end_ms":null,"outro_start_ms":null,"outro_end_ms":null}""", key) }
    }

    @Test fun missingMarkerRowReturnsNoMarkers() {
        assertNull(parseEpisodeMarkers("null", key))
    }
}
