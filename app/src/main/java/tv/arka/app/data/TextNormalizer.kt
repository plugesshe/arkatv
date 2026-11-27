package tv.arka.app.data

import java.text.Normalizer

/** Turns titles and queries into a comparable form: lower case, no accents, no punctuation. */
object TextNormalizer {
    private val marks = Regex("\\p{Mn}+")

    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(marks, "")
        val out = StringBuilder(decomposed.length)
        var pendingSpace = false
        for (c in decomposed) {
            if (Character.isLetterOrDigit(c)) {
                if (pendingSpace && out.isNotEmpty()) out.append(' ')
                pendingSpace = false
                out.append(c)
            } else {
                pendingSpace = true
            }
        }
        return out.toString()
    }

    fun terms(normalized: String): List<String> = normalized.split(' ').filter { it.isNotEmpty() }.take(6)

    fun matches(name: String, terms: List<String>): Boolean {
        val normalized = normalize(name)
        return terms.all { normalized.contains(it) }
    }
}
