package app.anim.sources

import app.anim.manga.chapters
import app.anim.manga.pages
import app.anim.manga.searchDex
import app.anim.manga.searchYomu
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.fail

/**
 * Live health check of every scraping source (ANIM_HEALTH=1), run on a schedule by
 * .github/workflows/source-health.yml. Each check goes end to end — listing, search, episodes or
 * chapters, and a playable stream or page images — so a layout change fails even when the site is up.
 * Writes a Markdown report to ANIM_HEALTH_REPORT and a JSON report ({"checks":[...]}) to ANIM_HEALTH_JSON
 * (both optional), and fails when any check fails.
 */
class SourceHealthProbe {
    private data class Check(val name: String, val ok: Boolean, val detail: String, val ms: Long)

    @Test fun sourcesRespond() = runBlocking {
        assumeTrue(System.getenv("ANIM_HEALTH") == "1")
        val checks = mutableListOf<Check>()
        suspend fun check(name: String, block: suspend () -> String) {
            var detail = ""
            var ok = false
            val ms = measureTimeMillis {
                try { detail = withTimeout(60_000) { block() }; ok = true }
                catch (e: Throwable) { detail = (e::class.simpleName ?: "Erro") + ": " + (e.message ?: "").take(160) }
            }
            checks += Check(name, ok, detail, ms)
            println("HEALTH ${if (ok) "OK  " else "FAIL"} $name (${ms} ms) $detail")
        }
        val repo = CachedSources(PortugueseSources(SourceHttp()))
        check("Em alta") { repo.trending().also { require(it.isNotEmpty()) { "lista vazia" } }.size.toString() + " títulos" }
        for (id in listOf("anitube", "animesdigital")) {
            check("$id · lançamentos") {
                val page = repo.discover(id)
                require(page.recent.isNotEmpty()) { "nenhum episódio recente (layout mudou?)" }
                "${page.recent.size} recentes"
            }
            var anime: AnimeResult? = null
            check("$id · busca") {
                val results = repo.search(id, "One Piece", false).ifEmpty { repo.search(id, "Naruto", false) }
                require(results.isNotEmpty()) { "busca sem resultados" }
                anime = results.first(); "${results.size} resultados"
            }
            var episode: Episode? = null
            check("$id · episódios") {
                val found = anime ?: error("sem anime da busca")
                val list = repo.episodes(found)
                require(list.isNotEmpty()) { "nenhum episódio" }
                episode = list.first(); "${list.size} episódios"
            }
            check("$id · vídeo") {
                val stream = repo.quickStream(anime ?: error("sem anime"), episode ?: error("sem episódio")) ?: error("nenhum servidor de vídeo respondeu")
                require(stream.source.url.startsWith("http")) { "URL de vídeo inválida" }
                stream.label
            }
        }
        for ((name, search) in listOf("Mangás · servidor 1" to ::searchDex, "Mangás · servidor 2" to ::searchYomu)) {
            check(name) {
                val manga = search("One Piece", 0).items.firstOrNull() ?: error("busca sem resultados")
                val chapter = chapters(manga, 0).items.firstOrNull() ?: error("nenhum capítulo")
                val images = pages(chapter)
                require(images.isNotEmpty()) { "capítulo sem páginas" }
                "${images.size} páginas"
            }
        }
        val failed = checks.filterNot { it.ok }
        System.getenv("ANIM_HEALTH_REPORT")?.takeIf { it.isNotBlank() }?.let { path ->
            File(path).writeText(buildString {
                appendLine(if (failed.isEmpty()) "Todas as fontes responderam." else "${failed.size} de ${checks.size} verificações falharam.")
                appendLine()
                appendLine("| Verificação | Resultado | Tempo | Detalhe |")
                appendLine("|---|---|---|---|")
                checks.forEach { appendLine("| ${it.name} | ${if (it.ok) "✅" else "❌"} | ${it.ms} ms | ${it.detail.replace("|", "/")} |") }
            })
        }
        System.getenv("ANIM_HEALTH_JSON")?.takeIf { it.isNotBlank() }?.let { path ->
            fun esc(text: String) = text.map { c -> when { c == '"' -> "\\\""; c == '\\' -> "\\\\"; c < ' ' -> " "; else -> c.toString() } }.joinToString("")
            File(path).writeText(checks.joinToString(prefix = "{\"checks\":[", postfix = "]}", separator = ",") {
                "{\"name\":\"${esc(it.name)}\",\"ok\":${it.ok},\"ms\":${it.ms},\"detail\":\"${esc(it.detail.take(200))}\"}"
            })
        }
        if (failed.isNotEmpty()) fail("Fontes com problema: " + failed.joinToString { it.name })
    }
}
