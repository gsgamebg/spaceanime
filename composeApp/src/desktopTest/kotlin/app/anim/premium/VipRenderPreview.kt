package app.anim.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.anim.design.LocalSpaceMotionEnabled
import app.anim.design.SpaceColors
import app.anim.design.SpaceVoid
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test

/** Opt-in visual preview: ANIM_RENDER_DIR=<dir> writes PNGs of the VIP page at desktop and phone widths. */
class VipRenderPreview {
    @Test fun renderVipPage() {
        val dir = System.getenv("ANIM_RENDER_DIR")
        assumeTrue(!dir.isNullOrBlank())
        val hero = ImageComposeScene(1200, 420, Density(1f)) {
            MaterialTheme(colorScheme = SpaceColors) {
                Column(Modifier.fillMaxSize().background(SpaceVoid).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    app.anim.design.ScreenHero("Explorar", "Busque em todos os servidores, filtre por áudio e salve na sua lista.", "search")
                    app.anim.design.ScreenHero("Mangás", "Leia capítulos em português e acompanhe os títulos que você segue.", "book-open")
                }
            }
        }
        File(dir, "hero.png").writeBytes(hero.render().encodeToData(EncodedImageFormat.PNG)!!.bytes); hero.close()
        for ((name, size) in listOf("vip-desktop" to (1400 to 1150), "vip-phone" to (420 to 2300))) {
            val scene = ImageComposeScene(size.first, size.second, Density(1f)) {
                CompositionLocalProvider(LocalSpaceMotionEnabled provides false) {
                    MaterialTheme(colorScheme = SpaceColors) {
                        Box(Modifier.fillMaxSize().background(SpaceVoid).padding(16.dp).verticalScroll(rememberScrollState())) { VipScreen() }
                    }
                }
            }
            val image = scene.render()
            File(dir, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            scene.close()
        }
    }
}
