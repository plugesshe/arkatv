package tv.arka.app.data

/**
 * Providers decorate titles with their own tags: "FR - Teen Wolf (2011) (US)", "4K-D+ - Percy Jackson".
 * This turns them back into the title itself, and keeps the year when there is one.
 * Deliberately conservative: when in doubt the name is left as it is.
 */
object TitleCleaner {
    class Clean(
        val title: String,
        val year: String,
        val language: String = "",
        val quality: String = "",
        /** "HDR", "ATMOS" or both, when the provider writes them. */
        val extras: String = "",
    ) {
        /** "FR 4K HDR ATMOS", "VOSTFR", "" — only what the provider wrote, never guessed. */
        val tags: String get() = listOf(language, quality, extras).filter { it.isNotEmpty() }.joinToString(" ")
    }

    private val tokenSplit = Regex("[^A-Z0-9+]+")

    private fun languageOf(token: String): String = when (token) {
        "FR", "VF", "VFF", "VFQ", "TRUEFRENCH", "FRENCH" -> "FR"
        "VOSTFR", "MULTI", "VO", "EN", "DE", "ES", "IT", "NL", "PT", "AR", "TR" -> token
        else -> ""
    }

    private fun qualityOf(token: String): String = when {
        token.startsWith("4K") || token == "UHD" || token == "2160P" -> "4K"
        token == "FHD" || token == "1080P" -> "FHD"
        token == "HD" || token == "720P" -> "HD"
        token == "SD" -> "SD"
        else -> ""
    }

    // "FR - ", "4K-D+ - ", "4M-AMZ | ": a short upper-case code followed by a separator.
    private val prefix = Regex("^\\s*[A-Z0-9+]{2,6}(-[A-Z0-9+]{1,4})?\\s*[-|]\\s+")
    private val brackets = Regex("\\s*\\[[^\\]]*\\]")
    private val trailingYear = Regex("\\s*\\(((19|20)\\d{2})\\)\\s*$")
    private val trailingCountry = Regex("\\s*\\([A-Z]{2}\\)\\s*$")
    private val trailingQuality = Regex(
        "\\s+(4K|UHD|FHD|HD|SD|HEVC|H265|X265|MULTI|VOSTFR|VF|TRUEFRENCH|HDR10\\+|HDR10|HDR|DV|DOVI|ATMOS|DOLBY\\s+VISION|" +
            "DOLBY\\s+ATMOS|DDP5\\.1|DD5\\.1|TRUEHD)\\s*$",
    )
    private val spaces = Regex("\\s{2,}")
    private val episodeCode = Regex("^.*\\bS\\d{1,2}\\s*E\\d{1,3}\\b\\s*[-–:]*\\s*", RegexOption.IGNORE_CASE)

    fun clean(raw: String): Clean {
        var text = raw.trim()
        var year = ""
        // Everything removed as a tag is kept aside to read the language and the quality from it.
        val removed = StringBuilder()
        for (pass in 0 until 2) {
            val match = prefix.find(text) ?: break
            val shorter = text.substring(match.range.last + 1)
            if (shorter.length < 2) break
            removed.append(' ').append(match.value)
            text = shorter
        }
        brackets.findAll(text).forEach { removed.append(' ').append(it.value) }
        text = brackets.replace(text, "")
        for (pass in 0 until 4) {
            val before = text
            text = trailingCountry.replace(text, "")
            val match = trailingYear.find(text)
            if (match != null) {
                if (year.isEmpty()) year = match.groupValues[1]
                text = text.removeRange(match.range)
            }
            trailingQuality.find(text)?.let { removed.append(' ').append(it.value) }
            text = trailingQuality.replace(text, "")
            if (text == before) break
        }
        text = spaces.replace(text, " ").trim()
        // Never return an empty or one-letter title: the provider's name is better than nothing.
        if (text.length < 2) return Clean(raw.trim(), year)
        var language = ""
        var quality = ""
        var hdr = false
        var atmos = false
        for (token in removed.toString().uppercase().split(tokenSplit)) {
            if (language.isEmpty()) language = languageOf(token)
            if (quality.isEmpty()) quality = qualityOf(token)
            if (token == "HDR" || token == "HDR10" || token == "HDR10+" || token == "DV" || token == "DOVI" || token == "VISION") hdr = true
            if (token == "ATMOS") atmos = true
        }
        val extras = listOf(if (hdr) "HDR" else "", if (atmos) "ATMOS" else "").filter { it.isNotEmpty() }.joinToString(" ")
        return Clean(text, year, language, quality, extras)
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
