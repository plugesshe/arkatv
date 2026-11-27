package tv.arka.app.data

/**
 * Providers decorate titles with their own tags: "FR - Teen Wolf (2011) (US)", "4K-D+ - Percy Jackson".
 * This turns them back into the title itself, and keeps the year when there is one.
 * Deliberately conservative: when in doubt the name is left as it is.
 */
object TitleCleaner {
    class Clean(val title: String, val year: String)

    // "FR - ", "4K-D+ - ", "4M-AMZ | ": a short upper-case code followed by a separator.
    private val prefix = Regex("^\\s*[A-Z0-9+]{2,6}(-[A-Z0-9+]{1,4})?\\s*[-|]\\s+")
    private val brackets = Regex("\\s*\\[[^\\]]*\\]")
    private val trailingYear = Regex("\\s*\\(((19|20)\\d{2})\\)\\s*$")
    private val trailingCountry = Regex("\\s*\\([A-Z]{2}\\)\\s*$")
    private val trailingQuality = Regex("\\s+(4K|UHD|FHD|HD|SD|HEVC|MULTI|VOSTFR|VF|TRUEFRENCH)\\s*$")
    private val spaces = Regex("\\s{2,}")
    private val episodeCode = Regex("^.*\\bS\\d{1,2}\\s*E\\d{1,3}\\b\\s*[-–:]*\\s*", RegexOption.IGNORE_CASE)

    fun clean(raw: String): Clean {
        var text = raw.trim()
        var year = ""
        for (pass in 0 until 2) {
            val shorter = prefix.replaceFirst(text, "")
            if (shorter.length >= 2 && shorter != text) text = shorter else break
        }
        text = brackets.replace(text, "")
        for (pass in 0 until 4) {
            val before = text
            text = trailingCountry.replace(text, "")
            val match = trailingYear.find(text)
            if (match != null) {
                if (year.isEmpty()) year = match.groupValues[1]
                text = text.removeRange(match.range)
            }
            text = trailingQuality.replace(text, "")
            if (text == before) break
        }
        text = spaces.replace(text, " ").trim()
        // Never return an empty or one-letter title: the provider's name is better than nothing.
        return if (text.length < 2) Clean(raw.trim(), year) else Clean(text, year)
    }

    fun title(raw: String): String = clean(raw).title

    /** "FR - Teen Wolf (2011) (US) - S02E05 - Meute contre meute" → "Meute contre meute". */
    fun episode(raw: String): String {
        val text = raw.trim()
        if (!episodeCode.containsMatchIn(text)) return text
        return episodeCode.replaceFirst(text, "").trim()
    }

    /** A stored episode subtitle, "S2 · É5 · <provider title>", with its last part cleaned. */
    fun subtitle(raw: String): String {
        val parts = raw.split(" · ")
        if (parts.size < 3 || !parts[0].startsWith("S")) return raw
        val name = episode(parts.drop(2).joinToString(" · "))
        return if (name.isBlank()) parts.take(2).joinToString(" · ") else parts.take(2).joinToString(" · ") + " · " + name
    }
}
