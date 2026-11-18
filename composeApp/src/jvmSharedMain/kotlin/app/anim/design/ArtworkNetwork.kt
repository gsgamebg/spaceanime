package app.anim.design

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.ResponseBody
import okio.ForwardingSource
import okio.Buffer
import okio.buffer

private val artworkHosts = setOf("www.anitube.zip", "anitube.zip", "animesdigital.org", "www.animesdigital.org", "s4.anilist.co", "uploads.mangadex.org", "yomu.com.br", "yomu.tauruus.com", "cdn.myanimelist.net", "www.animenewsnetwork.com", "animecorner.me", "staticg.sportskeeda.com", "img1.ak.crunchyroll.com")
actual fun safeArtworkUrl(raw: String): String? {
    val url = raw.toHttpUrlOrNull() ?: return null
    return url.takeIf { it.isHttps && (it.host in artworkHosts || it.host.endsWith(".mangadex.network") || it.host.endsWith(".yomu.com.br") || (it.host in setOf("i0.wp.com", "i1.wp.com", "i2.wp.com") && it.encodedPath.startsWith("/www.jbox.com.br/"))) && it.port == 443 && it.username.isEmpty() && it.password.isEmpty() }?.toString()
}
actual fun configureArtwork() {
    SingletonImageLoader.setSafe { context ->
        val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS).followSslRedirects(false)
            .addNetworkInterceptor { chain ->
                if (safeArtworkUrl(chain.request().url.toString()) == null) throw IOException("Artwork host not allowed")
                val response = chain.proceed(chain.request())
                val body = response.body
                if ((body?.contentLength() ?: 0) > 8 * 1024 * 1024) { response.close(); throw IOException("Artwork too large") }
                if (body == null) response else {
                    val limited = object : ForwardingSource(body.source()) {
                        var total = 0L
                        override fun read(sink: Buffer, byteCount: Long): Long {
                            val count = super.read(sink, byteCount.coerceAtMost(65536))
                            if (count > 0) total += count
                            if (total > 8 * 1024 * 1024) { close(); throw IOException("Artwork too large") }
                            return count
                        }
                    }.buffer()
                    response.newBuilder().body(object : ResponseBody() {
                        override fun contentType() = body.contentType()
                        override fun contentLength() = body.contentLength()
                        override fun source() = limited
                    }).build()
                }
            }.build()
        ImageLoader.Builder(context).components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }.build()
    }
}
