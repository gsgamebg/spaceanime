package app.anim.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.anim.design.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

private data class Airing(val title: String, val image: String, val episode: Int, val time: ZonedDateTime)
private suspend fun loadSchedule(): List<Airing> = withContext(Dispatchers.IO) {
    val zone = ZoneId.systemDefault()
    val start = LocalDate.now(zone).atStartOfDay(zone).toEpochSecond()
    val end = LocalDate.now(zone).plusDays(7).atStartOfDay(zone).toEpochSecond()
    val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).followRedirects(false).build()
    val result = mutableListOf<Airing>()
    for (page in 1..5) {
        val query = "query (\$start: Int, \$end: Int, \$page: Int) { Page(page: \$page, perPage: 50) { pageInfo { hasNextPage } airingSchedules(airingAt_greater: \$start, airingAt_lesser: \$end, sort: TIME) { airingAt episode media { isAdult title { romaji english } coverImage { large } } } } }"
        val payload = buildJsonObject { put("query", query); put("variables", buildJsonObject { put("start", start); put("end", end); put("page", page) }) }.toString()
        val request = Request.Builder().url("https://graphql.anilist.co/").post(payload.toRequestBody("application/json".toMediaType())).build()
        val pageData = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Agenda indisponível")
            val body = response.body ?: error("Resposta vazia")
            val bytes = body.byteStream().use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) { val n = input.read(buffer); if (n < 0) break; if (out.size() + n > 512_000) error("Resposta grande"); out.write(buffer, 0, n) }
                out.toByteArray()
            }
            if (bytes.size > 512_000) error("Resposta grande")
            Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject["data"]?.jsonObject?.get("Page")?.jsonObject ?: error("Resposta inválida")
        }
        pageData["airingSchedules"]?.jsonArray.orEmpty().forEach { value ->
            val obj = value.jsonObject
            val media = obj["media"]?.jsonObject ?: return@forEach
            if (media["isAdult"]?.jsonPrimitive?.booleanOrNull != false) return@forEach
            val title = media["title"]?.jsonObject?.get("romaji")?.jsonPrimitive?.contentOrNull ?: return@forEach
            val timestamp = obj["airingAt"]?.jsonPrimitive?.longOrNull ?: return@forEach
            val episode = obj["episode"]?.jsonPrimitive?.intOrNull ?: return@forEach
            val image = media["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.contentOrNull.orEmpty()
            result += Airing(title.take(160), image, episode, Instant.ofEpochSecond(timestamp).atZone(zone))
        }
        if (pageData["pageInfo"]?.jsonObject?.get("hasNextPage")?.jsonPrimitive?.booleanOrNull != true) break
    }
    result
}

@Composable
actual fun CalendarScreen(onSearch: (String) -> Unit) {
    val today = remember { LocalDate.now() }
    var selected by remember { mutableStateOf(today) }
    var attempt by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var schedule by remember { mutableStateOf(emptyList<Airing>()) }
    LaunchedEffect(attempt) {
        loading = true; error = false
        try { schedule = loadSchedule() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { error = true }
        loading = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Calendário de exibição", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
        Text("Próximos 7 dias · horários no fuso do dispositivo. Previsões da AniList; a disponibilidade no catálogo pode variar.", color = SpaceColors.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items((0L..6L).map { today.plusDays(it) }) { date ->
                val label = if (date == today) "Hoje" else date.format(DateTimeFormatter.ofPattern("EEE dd/MM", Locale.forLanguageTag("pt-BR")))
                FilterChip(selected = date == selected, onClick = { selected = date }, label = { Text(label) })
            }
        }
        when {
            loading -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Carregando programação…") }
            error -> { Text("Não foi possível carregar o calendário."); TextButton(onClick = { attempt++ }) { Text("Tentar novamente") } }
            else -> {
                val entries = schedule.filter { it.time.toLocalDate() == selected }
                Text("${entries.size} exibições previstas", color = SpaceColors.onSurfaceVariant)
                if (entries.isEmpty()) Text("Nenhuma exibição encontrada para este dia.")
                BoxWithConstraints {
                    val count = (maxWidth.value / 280f).toInt().coerceIn(1, 4)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        entries.chunked(count).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                row.forEach { anime ->
                                    Surface(onClick = { onSearch(anime.title) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = Color(0xFF151024)) {
                                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Artwork(model = anime.image, contentDescription = anime.title, contentScale = ContentScale.Crop, modifier = Modifier.width(58.dp).height(84.dp))
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(anime.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                Text("Episódio ${anime.episode}", color = SpaceColors.secondary)
                                                Text(anime.time.format(DateTimeFormatter.ofPattern("HH:mm")), color = SpaceColors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                                repeat(count - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

