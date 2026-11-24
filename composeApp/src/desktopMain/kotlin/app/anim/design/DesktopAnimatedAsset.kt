package app.anim.design

import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.anim.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data

internal data class AnimationFrame(val image: ImageBitmap, val durationMs: Int)

/** Decoded once per path and kept: the loader appears often and must start instantly. */
private val frameCache = mutableMapOf<String, List<AnimationFrame>>()

@OptIn(ExperimentalResourceApi::class)
private suspend fun frames(path: String): List<AnimationFrame> = frameCache[path] ?: withContext(Dispatchers.Default) {
    decodeAnimation(Res.readBytes(path))
}.also { synchronized(frameCache) { frameCache[path] = it } }

/** Skia decodes every frame (resolving frame dependencies) into an RGBA bitmap, alpha preserved. */
internal fun decodeAnimation(bytes: ByteArray): List<AnimationFrame> {
    val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
    val count = codec.frameCount.coerceAtLeast(1)
    val durations = codec.framesInfo.map { it.duration }
    return (0 until count).map { index ->
        val bitmap = Bitmap().apply { allocPixels(codec.imageInfo) }
        codec.readPixels(bitmap, index)
        bitmap.setImmutable()
        AnimationFrame(bitmap.asComposeImageBitmap(), durations.getOrNull(index)?.takeIf { it > 0 } ?: 100)
    }
}

@Composable actual fun AnimatedAsset(path: String, contentDescription: String?, modifier: Modifier) {
    var decoded by remember(path) { mutableStateOf(frameCache[path].orEmpty()) }
    LaunchedEffect(path) { if (decoded.isEmpty()) decoded = runCatching { frames(path) }.getOrDefault(emptyList()) }
    var index by remember(path) { mutableIntStateOf(0) }
    val motion = LocalSpaceMotionEnabled.current
    LaunchedEffect(decoded, motion) {
        if (!motion || decoded.size < 2) return@LaunchedEffect
        while (true) {
            delay(decoded[index].durationMs.toLong().coerceAtLeast(20))
            index = (index + 1) % decoded.size
        }
    }
    decoded.getOrNull(index)?.let { Image(it.image, contentDescription, modifier, contentScale = ContentScale.Fit) }
}
