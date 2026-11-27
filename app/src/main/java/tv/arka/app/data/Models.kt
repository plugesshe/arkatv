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

/** Orders offered when browsing films or series. PROVIDER keeps the order sent by the source. */
/** Optional narrowing of a catalogue page. Zero or empty means "no constraint". */
data class VodFilter(
    val minRating: Double = 0.0,
    val yearFrom: Int = 0,
    val yearTo: Int = 0,
    /** Stream ids to leave out (titles already watched). */
    val excludeIds: List<String> = emptyList(),
    /** "FR", "VOSTFR", "MULTI" or empty: read from the provider's own tags in the title. */
    val language: String = "",
    /** "4K", "FHD", "HD" or empty. */
    val quality: String = "",
) {
    val active: Boolean
        get() = minRating > 0 || yearFrom > 0 || yearTo > 0 || excludeIds.isNotEmpty() ||
            language.isNotEmpty() || quality.isNotEmpty()

    companion object {
        val NONE = VodFilter()
        const val MAX_EXCLUDED = 500
    }
}

enum class VodSort { PROVIDER, NAME, RECENT, YEAR, RATING, POPULAR }

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
    /** Language and quality as written by the provider ("FR 4K"), empty when it says nothing. */
    val tags: String = "",
    /** Wide picture, known once the title has been matched with TMDB. */
    val backdrop: String = "",
)

/**
 * A past (or running) programme the provider keeps for replay. [startText] is the start in the
 * provider's own clock, as its replay address expects it ("2026-11-27:18-00").
 */
class ReplayProgram(val title: String, val description: String, val start: Long, val end: Long, val startText: String)

/** A group of live channels made by the user; [ids] keeps the order in which they were added. */
data class ChannelGroup(val name: String, val ids: List<String>)

/** A home row built by ArkaTV from matched titles, instead of a provider category. */
class SmartRow(
    val title: String,
    val items: List<Vod>,
    val kind: VodKind,
    /** "g:<TMDB genre id>" or null: where "Tout" leads in the catalogue. */
    val categoryId: String?,
    val sort: VodSort,
)

/** Number of works per TMDB genre for one kind, merged versions counted once. */
class SmartCounts(val total: Int, val unmatched: Int, val byGenre: Map<Int, Int>)

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val url: String,
    val plot: String,
    val image: String,
    /** Length in minutes, 0 when the source does not say. */
    val durationMin: Int = 0,
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
    /** For an episode: the series it belongs to, so that the other episodes can be found again. */
    val seriesId: String = "",
)

fun Episode.toPlayItem(series: Vod): PlayItem = PlayItem(
    key = "e:$id",
    title = TitleCleaner.title(series.name),
    subtitle = "S$season · É$number" + TitleCleaner.episode(title).let { if (it.isNotBlank()) " · $it" else "" },
    url = url,
    live = false,
    poster = image.ifBlank { series.poster },
    seriesId = series.id,
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

/** A local viewer profile: its own favourites, list, history and search history. No account involved. */
/** [kids] marks a child profile: the categories locked by the parental control are hidden from it. */
data class Profile(val id: String, val name: String, val color: Int, val kids: Boolean = false) {
    val isMain: Boolean get() = id == MAIN_ID

    companion object {
        const val MAIN_ID = "default"
        const val MAX = 5
    }
}

/** What an Xtream provider says about the account. Zero means "not provided". */
data class AccountInfo(
    val status: String,
    val expiresAt: Long,
    val maxConnections: Int,
    val activeConnections: Int,
    val checkedAt: Long,
)

/** Most recently watched first, without duplicates, bounded. */
object RecentRules {
    const val MAX = 15

    fun push(current: List<String>, id: String): List<String> =
        (listOf(id) + current.filterNot { it == id }).take(MAX)
}

object PlayerSession {
    var queue: List<PlayItem> = emptyList()
    var index: Int = 0

    /** True when the first item must start from the beginning instead of resuming. */
    var startOver: Boolean = false
}
