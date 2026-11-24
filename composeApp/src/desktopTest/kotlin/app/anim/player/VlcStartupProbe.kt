package app.anim.player

import org.junit.Assume.assumeTrue
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import kotlin.system.measureTimeMillis
import kotlin.test.Test

/** Opt-in (ANIM_TIMING=1 + ANIM_VLC_HOME): cost of creating the VLC factory per player vs reusing it. */
class VlcStartupProbe {
    @Test fun factoryCost() {
        assumeTrue(System.getenv("ANIM_TIMING") == "1" && !System.getenv("ANIM_VLC_HOME").isNullOrBlank())
        println("TIMING prepareRuntime ms=" + measureTimeMillis { prepareDesktopRuntime() })
        repeat(3) { attempt ->
            var factory: MediaPlayerFactory? = null
            val created = measureTimeMillis { factory = MediaPlayerFactory(*VLC_FACTORY_ARGS) }
            val released = measureTimeMillis { factory!!.release() }
            println("TIMING vlcFactory#$attempt create ms=$created release ms=$released")
        }
    }
}
