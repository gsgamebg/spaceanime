package app.anim.player

/** Buffer tuning shared by both players: start fast, then keep enough ahead to ride out slow CDNs. */
object PlayerBuffering {
    /** Media needed before the first frame (ExoPlayer default 2.5s). */
    const val START_BUFFER_MS = 1_200
    /** Media needed to resume after a stall (default 5s); smaller = shorter freezes. */
    const val REBUFFER_MS = 2_500
    const val MIN_BUFFER_MS = 20_000
    const val MAX_BUFFER_MS = 60_000
    /** VLC network cache: enough to absorb jitter without a long start delay. */
    const val DESKTOP_NETWORK_CACHE_MS = 1_500
}

fun isHlsUrl(url: String): Boolean = url.substringBefore('?').substringBefore('#').endsWith(".m3u8", ignoreCase = true)

/** VLC per-media options: request headers, network cache and resume position. */
fun desktopMediaOptions(source: PlaybackSource): List<String> = buildList {
    source.headers.forEach { (name, value) ->
        add(if (name.equals("referer", ignoreCase = true)) ":http-referrer=$value" else ":http-user-agent=$value")
    }
    add(":network-caching=${PlayerBuffering.DESKTOP_NETWORK_CACHE_MS}")
    if (source.startPositionMs > 0) add(":start-time=${source.startPositionMs / 1000.0}")
}
