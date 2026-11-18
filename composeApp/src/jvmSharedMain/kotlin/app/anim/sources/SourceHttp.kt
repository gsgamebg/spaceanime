package app.anim.sources

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SourcePage(val text: String, val url: String)
internal interface SourceTransport {
    suspend fun fetch(url: String, referer: String, formBody: String? = null): SourcePage
}

internal fun allowedSourceAddress(raw: String): Boolean {
    val url = raw.toHttpUrlOrNull() ?: return false
    return url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
        url.host in setOf("www.anitube.zip", "anitube.zip", "animesdigital.org", "www.animesdigital.org", "www.blogger.com", "api.anivideo.net")
}

internal class SourceHttp : SourceTransport {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
        .followSslRedirects(false)
        .addNetworkInterceptor { chain ->
            if (!allowedSourceAddress(chain.request().url.toString())) throw IOException("Untrusted source destination")
            chain.proceed(chain.request())
        }.build()

    override suspend fun fetch(url: String, referer: String, formBody: String?): SourcePage {
        val address = url.toHttpUrlOrNull() ?: throw SourceException("A fonte informou um endereço inválido.")
        if (!allowedSourceAddress(url)) throw SourceException("Endereço de servidor não autorizado.")
        val request = Request.Builder().url(address)
            .header("User-Agent", "Mozilla/5.0 Anim/0.3")
            .header("Referer", referer)
            .apply { if (formBody != null) post(formBody.toRequestBody("application/x-www-form-urlencoded;charset=UTF-8".toMediaType())) }
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(SourceException("Não foi possível acessar ${address.host}. Verifique a conexão e tente novamente."))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val page = response.use {
                            val body = it.body ?: throw SourceException("A fonte respondeu sem conteúdo.")
                            val output = java.io.ByteArrayOutputStream()
                            body.byteStream().use { input ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (output.size() + count > 4 * 1024 * 1024) throw SourceException("A resposta da fonte excedeu o limite de leitura.")
                                    output.write(buffer, 0, count)
                                }
                            }
                            val bytes = output.toByteArray()
                            val text = bytes.toString(body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
                            if (listOf("Just a moment", "cf-chl", "Checking your browser", "DDoS-Guard").any { marker -> text.contains(marker, true) }) throw SourceException("A fonte exige uma verificação no navegador e está indisponível no app.")
                            if (!it.isSuccessful) throw SourceException("${address.host} respondeu HTTP ${it.code}. Tente novamente mais tarde.")
                            SourcePage(text, it.request.url.toString())
                        }
                        if (continuation.isActive) continuation.resume(page)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(if (error is SourceException) error else SourceException("Não foi possível ler a resposta da fonte."))
                    }
                }
            })
        }
    }
}
