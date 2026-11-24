package app.anim.player

import app.anim.sources.CachedSources
import app.anim.sources.PortugueseSources
import app.anim.sources.SourceHttp
import app.anim.sources.preferredStream
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test

/** Opt-in (ANIM_TIMING=1 + ANIM_VLC_HOME): click → streams → VLC first frame, without a window. */
class PlaybackStartProbe {
    @Test fun clickToFirstFrame() = runBlocking {
        assumeTrue(System.getenv("ANIM_TIMING") == "1" && !System.getenv("ANIM_VLC_HOME").isNullOrBlank())
        prepareDesktopRuntime()
        val repo = CachedSources(PortugueseSources(SourceHttp()))
        for (id in listOf("anitube", "animesdigital")) {
            val t0 = System.currentTimeMillis()
            val anime = repo.search(id, "Sousou no Frieren", false).first()
            val episode = repo.episodes(anime).first()
            val tEpisodes = System.currentTimeMillis()
            val stream = repo.quickStream(anime, episode)!!
            val tStreams = System.currentTimeMillis()
            val source = stream.source.copy(startPositionMs = 0)
            val factory = MediaPlayerFactory(*VLC_FACTORY_ARGS, "--vout=dummy", "--aout=dummy")
            val player = factory.mediaPlayers().newMediaPlayer()
            val playing = CountDownLatch(1)
            player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) { if (newTime > 0) playing.countDown() }
                override fun error(mediaPlayer: MediaPlayer) { println("TIMING $id vlc error"); playing.countDown() }
            })
            val tPlay = System.currentTimeMillis()
            player.media().play(source.url, *desktopMediaOptions(source).toTypedArray())
            val ok = playing.await(40, TimeUnit.SECONDS)
            val tFrame = System.currentTimeMillis()
            println("TIMING $id search+episodes=${tEpisodes - t0} streams=${tStreams - tEpisodes} vlcFirstFrame=${tFrame - tPlay} ok=$ok label=${stream.label}")
            player.controls().stop(); player.release(); factory.release()
        }
    }
}
