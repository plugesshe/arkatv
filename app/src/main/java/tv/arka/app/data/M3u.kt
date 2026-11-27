package tv.arka.app.data

import java.io.IOException
import java.io.Reader

object M3u {
    class Result(
        val liveCategories: List<Category>,
        val channels: List<Channel>,
        val vodCategories: List<Category>,
        val movies: List<Vod>,
        val epgUrl: String,
    )

    /** What a playlist says about itself once every entry has been handed over. */
    class Header(
        val liveCategories: List<Category>,
        val vodCategories: List<Category>,
        val epgUrl: String,
    )

    private val attribute = Regex("([\\w-]+)=\"([^\"]*)\"")
    private val videoExtensions = listOf(".mp4", ".mkv", ".avi", ".mov", ".m4v")

    /** Reads a whole playlist in memory. For tests: the app uses the streaming form below. */
    fun parse(input: Reader): Result {
        val channels = ArrayList<Channel>()
        val movies = ArrayList<Vod>()
        val header = parse(input, { channels.add(it) }, { movies.add(it) })
        return Result(header.liveCategories, channels, header.vodCategories, movies, header.epgUrl)
    }

    /**
     * Streams a playlist: each entry is handed to [onChannel] or [onMovie] as soon as it is read,
     * so the playlist is never held in memory as a whole.
     */
    fun parse(input: Reader, onChannel: (Channel) -> Unit, onMovie: (Vod) -> Unit): Header {
        val liveCategories = LinkedHashSet<String>()
        val vodCategories = LinkedHashSet<String>()
        var epgUrl = ""
        var channelCount = 0
        var movieCount = 0

        var name = ""
        var logo = ""
        var group = ""
        var epgId = ""
        var pending = false

        input.buffered().use { reader ->
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
                            onMovie(
                                Vod(
                                    id = line,
                                    kind = VodKind.MOVIE,
                                    name = name,
                                    poster = logo,
                                    categoryId = group,
                                    url = line,
                                    added = (1_000_000 - movieCount).toLong(),
                                )
                            )
                            movieCount++
                        } else {
                            liveCategories.add(group)
                            channelCount++
                            onChannel(
                                Channel(
                                    id = line,
                                    name = name,
                                    logo = logo,
                                    categoryId = group,
                                    epgId = epgId,
                                    url = line,
                                    number = channelCount,
                                )
                            )
                        }
                    }
                }
            }
        }
        if (channelCount == 0 && movieCount == 0) {
            throw IOException("Aucune chaîne trouvée dans cette playlist")
        }
        return Header(
            liveCategories = liveCategories.map { Category(it, it) },
            vodCategories = vodCategories.map { Category(it, it) },
            epgUrl = epgUrl,
        )
    }
}
