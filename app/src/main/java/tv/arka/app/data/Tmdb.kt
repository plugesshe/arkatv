package tv.arka.app.data

import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

/**
 * Read-only access to The Movie Database, with the user's own key. Only cleaned titles and years
 * are sent; nothing about the user, the source or what is being watched.
 */
object Tmdb {
    private const val API = "https://api.themoviedb.org/3"
    private const val POSTER = "https://image.tmdb.org/t/p/w342"
    private const val BACKDROP = "https://image.tmdb.org/t/p/w1280"
    private const val MAX_CANDIDATES = 8

    class Match(
        val id: Int,
        val title: String,
        val year: String,
        val genres: List<Int>,
        val popularity: Double,
        val rating: Double,
        val votes: Int,
        val poster: String,
        val backdrop: String,
        val overview: String,
    )

    /** Genres used to build the home rows, in the order they are offered. Names as shown to the user. */
    val movieGenres = listOf(
        28 to "Action", 35 to "Comédie", 53 to "Thriller", 18 to "Drame", 878 to "Science-fiction",
        27 to "Horreur", 16 to "Animation", 10751 to "En famille", 12 to "Aventure", 80 to "Crime",
        10749 to "Romance", 14 to "Fantastique", 99 to "Documentaires",
    )
    val seriesGenres = listOf(
        18 to "Séries dramatiques", 35 to "Séries comiques", 10759 to "Séries d'action et d'aventure",
        10765 to "Séries de science-fiction et fantastique", 80 to "Séries policières",
        16 to "Séries d'animation", 9648 to "Séries à mystère", 99 to "Séries documentaires",
    )

    /** The same series genres under the short names used on the Séries page. */
    val seriesGenreNames = listOf(
        18 to "Drame", 35 to "Comédie", 10759 to "Action et aventure", 10765 to "Science-fiction et fantastique",
        80 to "Crime", 16 to "Animation", 9648 to "Mystère", 99 to "Documentaires",
    )

    /** One request, and a second one without the year when the year gave nothing. */
    fun search(token: String, movie: Boolean, title: String, year: String): Match? {
        val first = parseSearch(request(token, movie, title, year), movie, title, year)
        if (first != null || year.isBlank()) return first
        return parseSearch(request(token, movie, title, ""), movie, title, year)
    }

    private fun request(token: String, movie: Boolean, title: String, year: String): String {
        val url = StringBuilder(API).append(if (movie) "/search/movie" else "/search/tv")
            .append("?language=fr-FR&include_adult=false&query=").append(URLEncoder.encode(title, "UTF-8"))
        if (year.isNotBlank()) url.append(if (movie) "&year=" else "&first_air_date_year=").append(year)
        return Http.getString(url.toString(), bearer = token)
    }

    /**
     * Picks the result that is this title, or nothing. A wrong match is worse than none, so a
     * result is only accepted when its name agrees with the wanted one.
     */
    fun parseSearch(json: String, movie: Boolean, wantedTitle: String, wantedYear: String): Match? {
        val results = JSONObject(json).optJSONArray("results") ?: return null
        val wanted = TextNormalizer.normalize(wantedTitle)
        if (wanted.isEmpty()) return null
        val year = wantedYear.toIntOrNull()
        var exact: JSONObject? = null
        var exactWithYear: JSONObject? = null
        var close: JSONObject? = null
        for (i in 0 until minOf(results.length(), MAX_CANDIDATES)) {
            val item = results.optJSONObject(i) ?: continue
            val names = listOf(
                item.optString(if (movie) "title" else "name"),
                item.optString(if (movie) "original_title" else "original_name"),
            ).map { TextNormalizer.normalize(it) }.filter { it.isNotEmpty() }
            val itemYear = item.optString(if (movie) "release_date" else "first_air_date").take(4).toIntOrNull()
            val yearAgrees = year == null || (itemYear != null && abs(itemYear - year) <= 1)
            if (names.any { it == wanted }) {
                if (exact == null) exact = item
                if (yearAgrees && exactWithYear == null) exactWithYear = item
            } else if (close == null && year != null && yearAgrees && wanted.length >= 4 &&
                names.any { it.contains(wanted) || wanted.contains(it) && it.length >= 4 }
            ) {
                close = item
            }
        }
        val chosen = exactWithYear ?: exact ?: close ?: return null
        val id = chosen.optInt("id")
        if (id <= 0) return null
        val genres = chosen.optJSONArray("genre_ids")
        val poster = chosen.optString("poster_path").takeIf { it.startsWith("/") }
        val backdrop = chosen.optString("backdrop_path").takeIf { it.startsWith("/") }
        return Match(
            id = id,
            title = chosen.optString(if (movie) "title" else "name"),
            year = chosen.optString(if (movie) "release_date" else "first_air_date").take(4),
            genres = if (genres == null) emptyList() else (0 until genres.length()).map { genres.optInt(it) },
            popularity = chosen.optDouble("popularity", 0.0),
            rating = chosen.optDouble("vote_average", 0.0),
            votes = chosen.optInt("vote_count"),
            poster = if (poster != null) POSTER + poster else "",
            backdrop = if (backdrop != null) BACKDROP + backdrop else "",
            overview = chosen.optString("overview"),
        )
    }

    fun ratingText(rating: Double): String = String.format(Locale.US, "%.1f", rating)
}
