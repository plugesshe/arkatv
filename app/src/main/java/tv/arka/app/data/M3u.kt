package tv.arka.app.data

object M3u {
    class Result(
        val liveCategories: List<Category>,
        val channels: List<Channel>,
        val vodCategories: List<Category>,
        val movies: List<Vod>,
        val epgUrl: String,
    )

    private val attribute = Regex("([\\w-]+)=\"([^\"]*)\"")
    private val videoExtensions = listOf(".mp4", ".mkv", ".avi", ".mov", ".m4v")

    fun load(url: String): Result {
        val channels = ArrayList<Channel>()
        val movies = ArrayList<Vod>()
        val liveCategories = LinkedHashSet<String>()
        val vodCategories = LinkedHashSet<String>()
        var epgUrl = ""

        var name = ""
        var logo = ""
        var group = ""
        var epgId = ""
        var pending = false

        Http.open(url).use { response ->
            val reader = response.body?.charStream()?.buffered() ?: return@use
            reader.forEachLine { raw ->
                val line = raw.trim()
                when {
                    line.startsWith("#EXTM3U") -> {
                        val attrs = attribute.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                        epgUrl = (attrs["url-tvg"] ?: attrs["x-tvg-url"]).orEmpty().substringBefore(',')
                    }
                    line.startsWith("#EXTINF") -> {
                        val attrs = attribute.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                        val lastQuote = line.lastIndexOf('"')
                        val comma = line.indexOf(',', if (lastQuote < 0) 0 else lastQuote)
                        name = if (comma >= 0) line.substring(comma + 1).trim() else attrs["tvg-name"].orEmpty()
                        if (name.isBlank()) name = attrs["tvg-name"].orEmpty()
                        logo = attrs["tvg-logo"].orEmpty()
                        group = attrs["group-title"].orEmpty().ifBlank { "Autres" }
                        epgId = attrs["tvg-id"].orEmpty()
                        pending = true
                    }
                    line.isEmpty() || line.startsWith("#") -> Unit
                    pending -> {
                        pending = false
                        val path = line.substringBefore('?').lowercase()
                        val isVod = "/movie/" in path || "/series/" in path || videoExtensions.any { path.endsWith(it) }
                        if (isVod) {
                            vodCategories.add(group)
                            movies.add(
                                Vod(
                                    id = line,
                                    kind = VodKind.MOVIE,
                                    name = name,
                                    poster = logo,
                                    categoryId = group,
                                    url = line,
                                    added = (1_000_000 - movies.size).toLong(),
                                )
                            )
                        } else {
                            liveCategories.add(group)
                            channels.add(
                                Channel(
                                    id = line,
                                    name = name,
                                    logo = logo,
                                    categoryId = group,
                                    epgId = epgId,
                                    url = line,
                                    number = channels.size + 1,
                                )
                            )
                        }
                    }
                }
            }
        }
        if (channels.isEmpty() && movies.isEmpty()) {
            throw java.io.IOException("Aucune chaîne trouvée dans cette playlist")
        }
        return Result(
            liveCategories = liveCategories.map { Category(it, it) },
            channels = channels,
            vodCategories = vodCategories.map { Category(it, it) },
            movies = movies,
            epgUrl = epgUrl,
        )
    }
}
