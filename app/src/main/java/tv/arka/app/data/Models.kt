package tv.arka.app.data

data class Source(
    val id: String,
    val type: String, // "xtream" or "m3u"
    val name: String,
    val url: String,
    val user: String = "",
    val pass: String = "",
    val epgUrl: String = "",
)

data class Category(val id: String, val name: String)

data class Channel(
    val id: String,
    val name: String,
    val logo: String,
    val categoryId: String,
    val epgId: String,
    val url: String,
    val number: Int,
)

enum class VodKind { MOVIE, SERIES }

data class Vod(
    val id: String,
    val kind: VodKind,
    val name: String,
    val poster: String,
    val categoryId: String,
    val url: String,
    val rating: String = "",
    val added: Long = 0,
    val plot: String = "",
    val year: String = "",
)

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val url: String,
    val plot: String,
    val image: String,
)

data class VodDetails(
    val plot: String = "",
    val genre: String = "",
    val year: String = "",
    val duration: String = "",
    val cast: String = "",
    val director: String = "",
    val backdrop: String = "",
    val seasons: Map<Int, List<Episode>> = emptyMap(),
)

/** Start and end are epoch seconds. */
data class Program(val start: Long, val end: Long, val title: String, val desc: String)

data class PlayItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val url: String,
    val live: Boolean,
    val poster: String = "",
    val epgId: String = "",
)

data class Progress(val item: PlayItem, val position: Long, val duration: Long, val updated: Long)

/** Everything a source exposes, in provider order. */
data class Catalog(
    val liveCategories: List<Category> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val vodCategories: List<Category> = emptyList(),
    val movies: List<Vod> = emptyList(),
    val seriesCategories: List<Category> = emptyList(),
    val series: List<Vod> = emptyList(),
) {
    val isEmpty: Boolean get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty()
}

object PlayerSession {
    var queue: List<PlayItem> = emptyList()
    var index: Int = 0
}
