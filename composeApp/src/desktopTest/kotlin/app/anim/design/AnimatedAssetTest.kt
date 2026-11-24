package app.anim.design

import androidx.compose.ui.graphics.toPixelMap
import java.io.File
import kotlin.test.*

class AnimatedAssetTest {
    private fun resource(name: String) = File("src/commonMain/composeResources/files/$name").readBytes()

    @Test fun loadingAnimationDecodesAllFramesWithTransparentBackground() {
        val frames = decodeAnimation(resource("spaceanime-loading.webp"))
        assertEquals(8, frames.size)
        assertTrue(frames.all { it.durationMs in 20..1000 })
        val first = frames.first().image.toPixelMap()
        assertEquals(0f, first[0, 0].alpha, "corner must be transparent, not a black box")
        assertTrue(first[first.width / 2, first.height / 2].alpha > 0.9f, "character must stay opaque")
    }

    @Test fun playerLoadingImageKeepsAlpha() {
        val frame = decodeAnimation(resource("spaceanime-player-loading.webp")).single().image.toPixelMap()
        assertEquals(0f, frame[0, 0].alpha)
    }
}
