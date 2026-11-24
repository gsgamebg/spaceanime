package app.anim.news

import app.anim.design.ScreenHero
import app.anim.design.SpaceLoader
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anim.design.Artwork
import app.anim.design.SpaceColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.util.concurrent.TimeUnit

internal data class NewsItem(val title: String, val summary: String, val url: String, val image: String, val date: String, val slug: String = "", val body: String = "")
internal fun parseNews(xml: String): List<NewsItem> = Jsoup.parse(xml, "", Parser.xmlParser()).select("item").mapNotNull { item ->
    val title = item.selectFirst("title")?.text()?.take(200)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
    val url = item.selectFirst("link")?.text()?.toHttpUrlOrNull() ?: return@mapNotNull null
    if (!url.isHttps || url.host !in setOf("jbox.com.br", "www.jbox.com.br") || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty()) return@mapNotNull null
    val desc = Jsoup.parseBodyFragment(item.selectFirst("description")?.text().orEmpty())
    val image = desc.selectFirst("img[src]")?.attr("src").orEmpty()
    val full = item.getElementsByTag("content:encoded").firstOrNull()?.text().orEmpty()
    val body = articleText(full)
    NewsItem(title, desc.text().take(240), url.toString(), image, item.selectFirst("pubDate")?.text().orEmpty().take(45), body = body)
}.distinctBy { it.url }.take(30)
private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun safeNewsLink(value: String): String? {
    val url = value.toHttpUrlOrNull() ?: return null
    val trusted = setOf("www.jbox.com.br", "jbox.com.br", "www.animenewsnetwork.com", "animenewsnetwork.com", "animecorner.me", "myanimelist.net", "www.crunchyroll.com", "www.otakuusamagazine.com", "otakuusamagazine.com", "www.animeherald.com", "animeherald.com", "comicbook.com", "www.comicbook.com")
    return url.takeIf { it.isHttps && it.port == 443 && it.host in trusted && it.username.isEmpty() && it.password.isEmpty() }?.toString()
}
internal fun nextNewsCursor(json: String): String? {
    val meta = Json.parseToJsonElement(json).jsonObject["meta"] as? JsonObject ?: return null
    val cursor = meta.str("nextCursor").takeIf { it.length in 1..512 && it.matches(Regex("[A-Za-z0-9_+=/-]+")) }
    if (cursor != null) return cursor
    if ((meta["hasMore"] as? JsonPrimitive)?.booleanOrNull != true) return null
    val offset = (meta["offset"] as? JsonPrimitive)?.intOrNull ?: return null
    val limit = (meta["limit"] as? JsonPrimitive)?.intOrNull ?: return null
    return if (offset in 0..100000 && limit in 1..100) "offset:${offset + limit}" else null
}
private fun articleText(html: String): String {
    val doc = Jsoup.parseBodyFragment(html.take(200_000))
    doc.select("script,style,iframe,nav,footer").remove()
    return doc.select("p,h2,h3,li").map { it.text().trim() }.filter { it.isNotBlank() }.joinToString("\n\n").take(20000)
        .ifBlank { doc.text().take(20000) }
}
internal fun parseApiList(json: String): List<NewsItem> {
    val root = Json.parseToJsonElement(json).jsonObject
    return (root["data"] as? JsonArray).orEmpty().mapNotNull { value ->
        val obj = value as? JsonObject ?: return@mapNotNull null
        val slug = obj.str("slug").takeIf { it.matches(Regex("[a-zA-Z0-9_-]{1,200}")) } ?: return@mapNotNull null
        val title = obj.str("title").take(200).takeIf { it.isNotBlank() } ?: return@mapNotNull null
        NewsItem(title, obj.str("excerpt").take(600), safeNewsLink(obj.str("link")).orEmpty(), obj.str("image"), obj.str("date").take(30), slug)
    }.distinctBy { it.slug }.take(30)
}
private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).followRedirects(false).build()
private fun fetch(url: HttpUrl): String {
    val request = Request.Builder().url(url).header("User-Agent", "SpaceAnime/0.7.9").build()
    return client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("Consulta indisponível")
        val out = java.io.ByteArrayOutputStream()
        (response.body ?: error("Resposta vazia")).byteStream().use { stream ->
            val buf = ByteArray(8192)
            while (true) { val n = stream.read(buf); if (n < 0) break; if (out.size() + n > 2_000_000) error("Resposta grande demais"); out.write(buf, 0, n) }
        }
        out.toString("UTF-8")
    }
}
private data class NewsBatch(val articles: List<NewsItem>, val partial: Boolean, val cursor: String?)
private suspend fun loadNews(query: String, cursor: String? = null): NewsBatch = coroutineScope {
    val local = async(Dispatchers.IO) { if (cursor != null) emptyList() else runCatching { parseNews(fetch("https://www.jbox.com.br/feed/".toHttpUrl())) }.getOrNull() }
    val remote = async(Dispatchers.IO) {
        runCatching {
            val url = (if (query.isBlank()) "https://aninews.vercel.app/api/news".toHttpUrl().newBuilder()
                else "https://aninews.vercel.app/api/search".toHttpUrl().newBuilder().addQueryParameter("q", query))
                .addQueryParameter("limit", "20").apply { if (cursor?.startsWith("offset:") == true) addQueryParameter("offset", cursor.substringAfter(":")) else if (cursor != null) addQueryParameter("cursor", cursor) }.build()
            val json = fetch(url)
            parseApiList(json) to nextNewsCursor(json)
        }.getOrNull()
    }
    val feed = local.await()
    val api = remote.await()
    if ((feed == null && api == null) || (cursor != null && api == null)) error("Consulta indisponível")
    val items = (feed.orEmpty().filter { query.isBlank() || (it.title + " " + it.summary).contains(query, true) } + api?.first.orEmpty()).distinctBy { it.title.lowercase() }
    NewsBatch(items, feed == null || api == null, api?.second)
}
private suspend fun loadBody(slug: String): String = withContext(Dispatchers.IO) {
    require(slug.matches(Regex("[a-zA-Z0-9_-]{1,200}")))
    val url = "https://aninews.vercel.app".toHttpUrl().newBuilder().addPathSegments("api/news").addPathSegment(slug).build()
    val data = Json.parseToJsonElement(fetch(url)).jsonObject["data"] as? JsonObject ?: error("Matéria indisponível")
    articleText(data.str("content").ifBlank { data.str("body") }).takeIf { it.isNotBlank() } ?: error("Matéria sem conteúdo")
}
@Composable actual fun NewsScreen(scrollState: ScrollState) {
    val uri = LocalUriHandler.current
    var articles by remember { mutableStateOf(emptyList<NewsItem>()) }
    var selected by remember { mutableStateOf<NewsItem?>(null) }
    var body by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var requested by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var detailLoading by remember { mutableStateOf(false) }
    var detailUnavailable by remember { mutableStateOf(false) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var paging by remember { mutableStateOf(false) }
    var pagingError by remember { mutableStateOf(false) }
    var pageRequest by remember { mutableIntStateOf(0) }
    var savedPosition by remember { mutableIntStateOf(0) }
    var openError by remember { mutableStateOf(false) }
    LaunchedEffect(selected) { if (selected != null) scrollState.scrollTo(0) else scrollState.scrollTo(savedPosition) }
    LaunchedEffect(pageRequest) {
        if (pageRequest == 0) return@LaunchedEffect
        val cursor = nextCursor ?: return@LaunchedEffect
        paging = true; pagingError = false
        try { val result = loadNews(requested, cursor); articles = (articles + result.articles).distinctBy { it.slug.ifBlank { it.url } }; nextCursor = result.cursor }
        catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { pagingError = true }
        finally { paging = false }
    }
    LaunchedEffect(requested, retry) {
        loading = true; error = false
        try { val result = loadNews(requested); articles = result.articles; partial = result.partial; nextCursor = result.cursor }
        catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { error = true }
        loading = false
    }
    LaunchedEffect(selected) {
        val item = selected ?: return@LaunchedEffect
        detailUnavailable = false
        body = item.body
        if (body.isBlank() && item.slug.isNotBlank()) {
            detailLoading = true
            try { body = loadBody(item.slug) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { detailUnavailable = true }
            finally { detailLoading = false }
        } else if (body.isBlank()) detailUnavailable = true
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (selected != null) {
            val item = selected!!
            TextButton(onClick = { selected = null }) { Text("← Voltar às notícias") }
            Text(item.title, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            if (item.date.isNotBlank()) Text(item.date.take(10), color = SpaceColors.onSurfaceVariant)
            if (item.image.isNotBlank()) Artwork(item.image, item.title, Modifier.fillMaxWidth().height(240.dp))
            if (detailLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            else {
                Text(body.ifBlank { item.summary }.ifBlank { "Conteúdo indisponível no momento." }, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                if (detailUnavailable) {
                    Text("O texto completo não está disponível no aplicativo no momento.", color = SpaceColors.onSurfaceVariant)
                    safeNewsLink(item.url)?.let { address -> TextButton(onClick = { openError = runCatching { uri.openUri(address) }.isFailure }) { Text("Abrir matéria completa") } }
                    if (openError) Text("Não foi possível abrir a matéria.", color = SpaceColors.error)
                }
            }
        } else {
            ScreenHero("Notícias", "As novidades do universo anime e mangá, atualizadas ao longo do dia.", "newspaper")
            OutlinedTextField(value = query, onValueChange = { query = it.take(120) }, modifier = Modifier.fillMaxWidth(), label = { Text("Buscar notícias") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { requested = query.trim(); retry++ }))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { requested = query.trim(); retry++ }) { Text("Buscar") }
                TextButton(onClick = { retry++ }) { Text("Atualizar") }
            }
            when {
                loading -> SpaceLoader("Carregando notícias…")
                error -> { Text("Não foi possível atualizar as notícias."); TextButton(onClick = { retry++ }) { Text("Tentar novamente") } }
                else -> {
                    if (partial) Text("Algumas notícias podem não estar disponíveis agora.", color = SpaceColors.onSurfaceVariant)
                    if (articles.isEmpty()) Text("Nenhuma notícia encontrada.")
                    articles.forEach { item ->
                        Surface(onClick = { savedPosition = scrollState.value; openError = false; selected = item }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color(0xFF151024)) {
                            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                if (item.image.isNotBlank()) Artwork(item.image, null, Modifier.width(110.dp).height(85.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    Text(item.summary, color = SpaceColors.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    if (item.date.isNotBlank()) Text(item.date.take(10), color = SpaceColors.secondary, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    if (paging) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (pagingError) Text("Não foi possível carregar mais notícias.", color = SpaceColors.error)
                    if (nextCursor != null) Button(onClick = { pageRequest++ }, enabled = !paging) { Text(if (pagingError) "Tentar novamente" else "Carregar mais notícias") }
                }
            }
        }
    }
}
