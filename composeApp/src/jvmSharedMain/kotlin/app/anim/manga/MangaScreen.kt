package app.anim.manga

import app.anim.design.ScreenHero
import app.anim.design.SpaceLoader
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import coil3.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anim.design.Artwork
import app.anim.design.SpaceColors
import app.anim.library.MangaReference
import app.anim.library.rememberMangaFollowStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class Manga(val id: String, val title: String, val cover: String, val source: String = "dex")
internal data class Chapter(val id: String, val label: String, val source: String = "dex", val mangaId: String = "")
internal data class PageResult<T>(val items: List<T>, val more: Boolean, val unavailable: List<String> = emptyList(), val unavailableChapters: Int = 0)
private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).followRedirects(false).build()
private fun JsonObject.obj(key: String) = this[key] as? JsonObject
private fun JsonObject.arr(key: String) = (this[key] as? JsonArray).orEmpty()
private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
private fun uuid(raw: String): String = UUID.fromString(raw).toString()
private fun readJson(url: HttpUrl): JsonObject {
    val request = Request.Builder().url(url).header("User-Agent", "SpaceAnime/0.7.7").header("Accept", "application/json").build()
    return client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("Serviço indisponível (${response.code})")
        val input = (response.body ?: error("Resposta vazia")).byteStream()
        val bytes = input.use { stream ->
            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val n = stream.read(buffer); if (n < 0) break; if (out.size() + n > 2_000_000) error("Resposta muito grande"); out.write(buffer, 0, n) }
            out.toByteArray()
        }
        Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
    }
}
private fun api(vararg paths: String): HttpUrl.Builder = "https://api.mangadex.org".toHttpUrl().newBuilder().apply { paths.forEach { addPathSegment(it) } }
internal fun mangaDexBrowseUrl(page: Int): HttpUrl = mangaDexQueryUrl(null, page)
private fun mangaDexSearchUrl(query: String, page: Int): HttpUrl = mangaDexQueryUrl(query, page)
private fun mangaDexQueryUrl(query: String?, page: Int): HttpUrl {
    require(page in 0..1000)
    return api("manga").apply {
        query?.let { addQueryParameter("title", it) }
        addQueryParameter("limit", "24")
        addQueryParameter("offset", (page * 24).toString())
        addQueryParameter("availableTranslatedLanguage[]", "pt-br")
        addQueryParameter("contentRating[]", "safe")
        addQueryParameter("contentRating[]", "suggestive")
        addQueryParameter("includes[]", "cover_art")
        if (query == null) addQueryParameter("order[followedCount]", "desc")
    }.build()
}
internal fun parseDexMangaResults(root: JsonObject, page: Int): PageResult<Manga> {
    require(page in 0..1000)
    val data = root.arr("data").mapNotNull { element ->
        val manga = element as? JsonObject ?: return@mapNotNull null
        val id = runCatching { uuid(manga.str("id") ?: "") }.getOrNull() ?: return@mapNotNull null
        val titleObj = manga.obj("attributes")?.obj("title") ?: return@mapNotNull null
        val title = titleObj.str("pt-br") ?: titleObj.str("en") ?: (titleObj.values.firstOrNull() as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val cover = manga.arr("relationships").mapNotNull { it as? JsonObject }.firstOrNull { it.str("type") == "cover_art" }?.obj("attributes")?.str("fileName")
        val image = cover?.takeIf { it.matches(Regex("[A-Za-z0-9._-]{1,150}")) }?.let { "https://uploads.mangadex.org/covers/$id/$it.256.jpg" }.orEmpty()
        Manga(id, title.take(180), image)
    }
    return PageResult(data, (root["total"] as? JsonPrimitive)?.intOrNull?.let { (page + 1) * 24 < it } ?: (data.size == 24))
}
private suspend fun searchDex(query: String, page: Int): PageResult<Manga> = withContext(Dispatchers.IO) {
    parseDexMangaResults(readJson(mangaDexSearchUrl(query, page)), page)
}
private suspend fun browseDex(page: Int): PageResult<Manga> = withContext(Dispatchers.IO) {
    parseDexMangaResults(readJson(mangaDexBrowseUrl(page)), page)
}
private suspend fun chapters(manga: Manga, page: Int): PageResult<Chapter> = if (manga.source == "yomu") yomuChapters(manga, page) else withContext(Dispatchers.IO) {
    val root = readJson(api("manga", uuid(manga.id), "feed").addQueryParameter("translatedLanguage[]", "pt-br").addQueryParameter("limit", "100").addQueryParameter("offset", (page * 100).toString())
        .addQueryParameter("order[chapter]", "asc").addQueryParameter("contentRating[]", "safe").addQueryParameter("contentRating[]", "suggestive").build())
    parseDexChapters(root, page)

}
internal fun parseDexChapters(root: JsonObject, page: Int): PageResult<Chapter> {
    var omitted = 0
    val data = root.arr("data").mapNotNull { value ->
        val item = value as? JsonObject ?: return@mapNotNull null
        val attr = item.obj("attributes") ?: return@mapNotNull null
        if (attr.str("translatedLanguage") != "pt-br") return@mapNotNull null
        if (attr.str("externalUrl") != null || (attr["pages"] as? JsonPrimitive)?.intOrNull == 0) { omitted++; return@mapNotNull null }
        val id = runCatching { uuid(item.str("id") ?: "") }.getOrNull() ?: return@mapNotNull null
        val number = attr.str("chapter") ?: "especial"
        val name = attr.str("title")?.takeIf { it.isNotBlank() }?.let { " · ${it.take(100)}" }.orEmpty()
        Chapter(id, "Capítulo $number$name")
    }
    return PageResult(data, (root["total"] as? JsonPrimitive)?.intOrNull?.let { (page + 1) * 100 < it } ?: false, unavailableChapters = omitted)
}
private suspend fun pages(chapter: Chapter): List<String> = if (chapter.source == "yomu") yomuPages(chapter) else withContext(Dispatchers.IO) {
    val root = readJson(api("at-home", "server", uuid(chapter.id)).addQueryParameter("forcePort443", "true").build())
    val base = root.str("baseUrl")?.toHttpUrlOrNull() ?: error("Servidor de imagens inválido")
    if (!base.isHttps || base.port != 443 || base.username.isNotEmpty() || base.password.isNotEmpty() || !base.host.endsWith(".mangadex.network")) error("Servidor de imagens não confiável")
    val info = root.obj("chapter") ?: error("Capítulo indisponível")
    val hash = info.str("hash")?.takeIf { it.matches(Regex("[a-fA-F0-9]{16,128}")) } ?: error("Capítulo inválido")
    val files = info.arr("dataSaver").map { (it as? JsonPrimitive)?.contentOrNull?.takeIf { name -> name.matches(Regex("[A-Za-z0-9._-]{1,200}")) } ?: error("Página inválida") }
    if (files.isEmpty() || files.size > 250) error("Páginas indisponíveis")
    files.map { base.newBuilder().addPathSegment("data-saver").addPathSegment(hash).addPathSegment(it).build().toString() }
}

private fun yomuApi(vararg paths: String): HttpUrl.Builder = "https://yomu.tauruus.com".toHttpUrl().newBuilder().apply { paths.forEach { addPathSegment(it) } }
private fun safeSlug(value: String): String? = value.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,180}")) }
private fun safeNumber(value: String): String? = value.takeIf { it.matches(Regex("[0-9]{1,8}(\\.[0-9]{1,4})?")) }
internal fun parseYomuSearch(root: JsonObject): PageResult<Manga> {
    val list = root.arr("series").mapNotNull { value ->
        val item = value as? JsonObject ?: return@mapNotNull null
        val slug = item.str("slug")?.let(::safeSlug) ?: return@mapNotNull null
        val title = item.str("title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        Manga(slug, title.take(180), item.str("cover").orEmpty(), "yomu")
    }
    return PageResult(list, (root["hasNextPage"] as? JsonPrimitive)?.booleanOrNull == true)
}
internal fun parseYomuChapters(root: JsonObject, slug: String): List<Chapter> = root.arr("chapters").mapNotNull { value ->
    val item = value as? JsonObject ?: return@mapNotNull null
    val number = item.str("number")?.removeSuffix(".0")?.let(::safeNumber) ?: return@mapNotNull null
    val title = item.str("title")?.takeIf { it.isNotBlank() }?.take(100) ?: "Capítulo $number"
    Chapter(number, title, "yomu", slug)
}
private suspend fun searchYomu(query: String, page: Int): PageResult<Manga> = withContext(Dispatchers.IO) {
    parseYomuSearch(readJson(yomuApi("list").addQueryParameter("q", query).addQueryParameter("page", (page + 1).toString()).build()))
}
private suspend fun search(query: String, page: Int): PageResult<Manga> = coroutineScope {
    val results = listOf<Pair<String, suspend () -> PageResult<Manga>>>("Space 1" to { searchDex(query, page) }, "Space 2" to { searchYomu(query, page) })
        .map { (name, fetch) -> async { try { name to Result.success(fetch()) } catch (e: CancellationException) { throw e } catch (e: Exception) { name to Result.failure(e) } } }.awaitAll()
    val successes = results.mapNotNull { it.second.getOrNull() }
    if (successes.isEmpty()) error("Servidores indisponíveis")
    PageResult(successes.flatMap { it.items }.distinctBy { it.source to it.id }, successes.any { it.more }, results.filter { it.second.isFailure }.map { it.first })
}
private suspend fun yomuChapters(manga: Manga, page: Int): PageResult<Chapter> = withContext(Dispatchers.IO) {
    val slug = safeSlug(manga.id) ?: error("Mangá inválido")
    val all = parseYomuChapters(readJson(yomuApi("manga", slug).build()), slug)
    PageResult(all.drop(page * 100).take(100), (page + 1) * 100 < all.size)
}
private suspend fun yomuPages(chapter: Chapter): List<String> = withContext(Dispatchers.IO) {
    val slug = safeSlug(chapter.mangaId) ?: error("Mangá inválido")
    val number = safeNumber(chapter.id) ?: error("Capítulo inválido")
    val raw = readJson(yomuApi("chapter", slug, number).build()).arr("pages")
    if (raw.isEmpty() || raw.size > 250) error("Páginas indisponíveis")
    val pages = raw.map { (it as? JsonPrimitive)?.contentOrNull?.let { url -> app.anim.design.safeArtworkUrl(url) } ?: error("Imagem não autorizada") }
    pages
}

@Composable
actual fun MangaScreen(searchRequest: String, scrollState: ScrollState, profileId: String) {
    val followStore = rememberMangaFollowStore(profileId)
    val follows by followStore.state.collectAsState()
    var input by remember { mutableStateOf(searchRequest) }
    var query by remember { mutableStateOf(searchRequest) }
    var manga by remember { mutableStateOf<Manga?>(null) }
    var chapter by remember { mutableStateOf<Chapter?>(null) }
    var page by remember { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var matches by remember { mutableStateOf(PageResult(emptyList<Manga>(), false)) }
    var listing by remember { mutableStateOf(PageResult(emptyList<Chapter>(), false)) }
    var images by remember { mutableStateOf(emptyList<String>()) }
    var imageIndex by remember { mutableIntStateOf(0) }
    var chapterFilter by remember { mutableStateOf("") }
    var newestFirst by remember { mutableStateOf(false) }
    var widePage by remember { mutableStateOf(false) }
    var verticalReader by remember { mutableStateOf(true) }
    LaunchedEffect(chapter, imageIndex) { if (chapter != null) scrollState.scrollTo(0) }
    LaunchedEffect(searchRequest) { if (searchRequest.isNotBlank()) { input = searchRequest; query = searchRequest; manga = null; chapter = null; page = 0 } }
    LaunchedEffect(query, manga, chapter, page, retry) {
        error = null
        loading = true
        try {
            when {
                chapter != null -> images = pages(chapter!!)
                manga != null -> listing = chapters(manga!!, page)
                query.isBlank() -> matches = browseDex(page)
                else -> matches = search(query, page)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { error = "Não foi possível carregar. Verifique sua conexão e tente novamente." }
        finally { loading = false }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHero("Mangás", "Leia capítulos em português e acompanhe os títulos que você segue.", "book-open")
        if (manga == null) {
            OutlinedTextField(value = input, onValueChange = { input = it.take(120) }, modifier = Modifier.fillMaxWidth(), label = { Text("Buscar mangá por título") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { query = input.trim(); page = 0; retry++ }))
            Button(onClick = { query = input.trim(); page = 0; retry++ }, enabled = input.isNotBlank()) { Text("Buscar mangás") }
        } else {
            TextButton(onClick = { if (chapter != null) chapter = null else { manga = null; page = 0; chapterFilter = "" } }) { Text("← Voltar") }
            Text(manga!!.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            val followedManga = MangaReference(manga!!.source, manga!!.id, manga!!.title, manga!!.cover)
            OutlinedButton(onClick = { followStore.toggleFollow(followedManga) }) {
                Text(if (follows.isFollowing(followedManga)) "Remover dos favoritos" else "Seguir mangá")
            }
            chapter?.let { Text(it.label, color = SpaceColors.secondary) }
        }
        follows.error?.let { Text(it, color = SpaceColors.error) }
        if (loading) SpaceLoader(size = 120.dp)
        error?.let { message ->
            Text(message, color = SpaceColors.error)
            TextButton(onClick = { retry++ }) { Text("Tentar novamente") }
        }
        when {
            chapter != null -> if (images.isEmpty()) Text("Nenhuma página disponível.") else {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { verticalReader = !verticalReader }) { Text(if (verticalReader) "Modo vertical · alterar para páginas" else "Modo por página · alterar para vertical") }
                    if (verticalReader) {
                        Text("Role dentro do leitor para avançar pelas páginas.", color = SpaceColors.onSurfaceVariant)
                        LazyColumn(modifier = Modifier.fillMaxWidth().height(620.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(images, key = { index, _ -> index }) { index, image -> MangaPageImage(image, index + 1, if (widePage) 1100 else 760) }
                        }
                        TextButton(onClick = { widePage = !widePage }) { Text(if (widePage) "Largura confortável" else "Ampliar") }
                    } else {
                    Row(Modifier.widthIn(max = 850.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { imageIndex-- }, enabled = imageIndex > 0) { Text("← Anterior") }
                        Text("${imageIndex + 1} / ${images.size}", color = Color.White)
                        Button(onClick = { imageIndex++ }, enabled = imageIndex < images.lastIndex) { Text("Próxima →") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Página", color = SpaceColors.onSurfaceVariant)
                        Slider(value = imageIndex.toFloat(), onValueChange = { imageIndex = it.toInt().coerceIn(0, images.lastIndex) }, valueRange = 0f..images.lastIndex.coerceAtLeast(1).toFloat(), enabled = images.size > 1, modifier = Modifier.weight(1f).widthIn(max = 350.dp))
                        TextButton(onClick = { widePage = !widePage }) { Text(if (widePage) "Largura confortável" else "Ampliar") }
                    }
                    MangaPageImage(images[imageIndex], imageIndex + 1, if (widePage) 1100 else 760)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { imageIndex-- }, enabled = imageIndex > 0) { Text("← Anterior") }
                        Button(onClick = { imageIndex++ }, enabled = imageIndex < images.lastIndex) { Text("Próxima página →") }
                    }
                    }
                }
            }
            manga != null -> {
                Text("Página ${page + 1} de capítulos · ${listing.items.size} disponíveis nesta página", color = SpaceColors.onSurfaceVariant)
                if (listing.unavailableChapters > 0) Text("${listing.unavailableChapters} capítulo(s) desta página usam leitor externo ou não têm páginas disponíveis neste servidor.", color = SpaceColors.secondary)
                Text("A quantidade depende dos capítulos em português publicados neste servidor; outras versões da obra podem ter listas diferentes.", color = SpaceColors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = chapterFilter, onValueChange = { chapterFilter = it.take(100) }, label = { Text("Filtrar capítulos desta página") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                TextButton(onClick = { newestFirst = !newestFirst }) { Text(if (newestFirst) "Ordem invertida nesta página" else "Ordem do servidor nesta página") }
                val filtered = listing.items.filter { it.label.contains(chapterFilter.trim(), ignoreCase = true) }.let { if (newestFirst) it.reversed() else it }
                if (filtered.isEmpty()) Text(if (chapterFilter.isNotBlank()) "Nenhum capítulo encontrado neste filtro." else "Nenhum capítulo em português brasileiro nesta página.")
                filtered.forEach { item -> Surface(onClick = { chapter = item; imageIndex = 0 }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color(0xFF151024)) { Text(item.label, Modifier.padding(18.dp), color = Color.White) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { page-- }, enabled = page > 0) { Text("Anterior") }
                    TextButton(onClick = { page++ }, enabled = listing.more) { Text("Próxima") }
                }
            }
            manga == null && chapter == null -> {
                if (query.isBlank()) Text("Mais populares no MangaDex · catálogo em português", color = SpaceColors.onSurfaceVariant)
                if (matches.unavailable.isNotEmpty()) Text("Alguns servidores não responderam: ${matches.unavailable.joinToString()}. Os resultados podem estar incompletos.", color = SpaceColors.secondary)
                if (matches.items.isEmpty() && !loading && error == null) Text(if (query.isBlank()) "O catálogo MangaDex está vazio no momento." else "Nenhum mangá encontrado com capítulos em português brasileiro.")
                matches.items.forEach { item ->
                    val reference = MangaReference(item.source, item.id, item.title, item.cover)
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color(0xFF151024)) {
                        Column(Modifier.padding(10.dp)) {
                            Row(
                                Modifier.fillMaxWidth().clickable { manga = item; page = 0 },
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Artwork(item.cover, item.title, Modifier.width(64.dp).height(90.dp))
                                Column {
                                    Text(item.title, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    Text(if (item.source == "yomu") "Servidor Space 2 · Abrir capítulos" else "Servidor Space 1 · Abrir capítulos", color = SpaceColors.secondary)
                                }
                            }
                            TextButton(onClick = { followStore.toggleFollow(reference) }) {
                                Text(if (follows.isFollowing(reference)) "Seguindo · remover" else "Seguir mangá")
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { page-- }, enabled = page > 0) { Text("Anterior") }
                    TextButton(onClick = { page++ }, enabled = matches.more) { Text("Próxima") }
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun MangaPageImage(image: String, number: Int, maxWidth: Int) {
    var ratio by remember(image) { mutableFloatStateOf(0.7f) }
    var failed by remember(image) { mutableStateOf(false) }
    var reload by remember(image) { mutableIntStateOf(0) }
    val safeUrl = remember(image) { app.anim.design.safeArtworkUrl(image) }
    Box(Modifier.widthIn(max = maxWidth.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF151024)), contentAlignment = Alignment.Center) {
        if (safeUrl == null || failed) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Não foi possível carregar a página $number.", color = Color.White)
                if (safeUrl != null) TextButton(onClick = { failed = false; reload++ }) { Text("Tentar novamente") }
            }
        } else key(image, reload) {
            AsyncImage(model = safeUrl, contentDescription = "Página $number", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
                onSuccess = { state ->
                    val width = state.result.image.width
                    val height = state.result.image.height
                    if (width > 0 && height > 0) ratio = (width.toFloat() / height).coerceIn(0.08f, 4f)
                },
                onError = { failed = true })
        }
    }
}
