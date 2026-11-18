package app.anim.player

import com.sun.jna.NativeLibrary
import java.io.File

/** The portable package keeps VLC beside its bundled Java runtime. */
internal fun prepareDesktopRuntime() {
    val bundled = File(System.getProperty("java.home")).parentFile.resolve("vlc")
    val directory = System.getenv("ANIM_VLC_HOME")?.let(::File) ?: bundled
    if (directory.resolve("libvlc.dll").isFile) {
        NativeLibrary.addSearchPath("libvlccore", directory.absolutePath)
        NativeLibrary.addSearchPath("libvlc", directory.absolutePath)
        // Load the sibling dependency before libvlc, including when PATH has no VLC.
        NativeLibrary.getInstance(directory.resolve("libvlccore.dll").absolutePath)
    }
}
