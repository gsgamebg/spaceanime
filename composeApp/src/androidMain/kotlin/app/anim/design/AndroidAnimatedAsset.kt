package app.anim.design

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.anim.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import java.nio.ByteBuffer

private val bytesCache = mutableMapOf<String, ByteArray>()

/** Android 9+: native AnimatedImageDrawable (hardware-accelerated, alpha kept). Android 8: first frame. */
@OptIn(ExperimentalResourceApi::class)
@Composable actual fun AnimatedAsset(path: String, contentDescription: String?, modifier: Modifier) {
    var bytes by remember(path) { mutableStateOf(bytesCache[path]) }
    LaunchedEffect(path) {
        if (bytes == null) bytes = runCatching { Res.readBytes(path) }.getOrNull()?.also { synchronized(bytesCache) { bytesCache[path] = it } }
    }
    val data = bytes ?: return
    val motion = LocalSpaceMotionEnabled.current
    AndroidView(
        factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        modifier = modifier,
        update = { view ->
            view.contentDescription = contentDescription
            if (view.tag !== data) {
                view.tag = data
                val drawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(data)))
                } else BitmapDrawable(view.resources, BitmapFactory.decodeByteArray(data, 0, data.size))
                view.setImageDrawable(drawable)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                (view.drawable as? AnimatedImageDrawable)?.let { if (motion) it.start() else it.stop() }
            }
        },
        onRelease = { view ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) (view.drawable as? AnimatedImageDrawable)?.stop()
        },
    )
}
