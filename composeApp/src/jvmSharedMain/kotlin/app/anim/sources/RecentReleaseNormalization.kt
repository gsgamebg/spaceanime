package app.anim.sources

import java.text.Normalizer
import java.util.Locale

actual fun normalizeReleaseTitle(title: String): String =
    buildString {
        val normalized = Normalizer.normalize(title, Normalizer.Form.NFD).lowercase(Locale.ROOT)
        normalized.codePoints().forEach { codePoint ->
            val type = Character.getType(codePoint)
            if (type != Character.NON_SPACING_MARK.toInt() &&
                type != Character.COMBINING_SPACING_MARK.toInt() &&
                type != Character.ENCLOSING_MARK.toInt() &&
                Character.isLetterOrDigit(codePoint)
            ) {
                appendCodePoint(codePoint)
            }
        }
    }
