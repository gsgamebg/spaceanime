package app.anim.player

import org.junit.Assume.assumeTrue
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Opt-in native integration test: opens its own window and needs network + local VLC. */
class DesktopPlaybackSmokeTest {
    @Test fun realPlaybackPauseSeekVolumeAndRelease() {
        assumeTrue("Set ANIM_PLAYER_SMOKE=1 for native playback tests", System.getenv("ANIM_PLAYER_SMOKE") == "1")
        for (source in listOf(demoMp4, demoHls)) {
            lateinit var player: DesktopPlayer
            lateinit var frame: JFrame
            SwingUtilities.invokeAndWait {
                player = DesktopPlayer(source)
                frame = JFrame("Anim — teste de reprodução").apply {
                    contentPane.add(player.component)
                    setSize(640, 400)
                    isVisible = true
                }
            }
            try {
                player.start()
                awaitState(player) { it.playing && it.positionMs > 1000 && it.durationMs > 30000 }
                player.pause()
                awaitState(player) { !it.playing }
                val pausedAt = player.state.value.positionMs
                Thread.sleep(750)
                assertTrue(kotlin.math.abs(player.state.value.positionMs - pausedAt) < 500, "Paused position drifted")
                player.seekTo(20000)
                awaitState(player) { it.positionMs in 19000..21000 }
                player.setVolume(0.2f)
                awaitState(player) { it.volume == 0.2f }
                player.play()
                awaitState(player) { it.playing && it.positionMs > 21000 }
                println("PASS ${source.title}: duration=${player.state.value.durationMs}, resumed=${player.state.value.positionMs}")
            } finally {
                player.release()
                player.releaseComplete.get(15, TimeUnit.SECONDS)
                val closedState = player.state.value
                player.play()
                player.seekTo(0)
                Thread.sleep(300)
                assertEquals(closedState, player.state.value, "Released session changed state")
                SwingUtilities.invokeAndWait { frame.dispose() }
            }
        }
    }

    private fun awaitState(player: DesktopPlayer, condition: (PlaybackState) -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40)
        while (System.nanoTime() < deadline) {
            val state = player.state.value
            check(state.error == null) { state.error!! }
            if (condition(state)) return
            Thread.sleep(100)
        }
        error("Playback timed out: ${player.state.value}")
    }
}
