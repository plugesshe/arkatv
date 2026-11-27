package tv.arka.app.data

import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.Reader
import java.net.URLEncoder

class XtreamApi(private val source: Source) {

    val base: String = source.url.trim().trimEnd('/').let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
    }

    val epgUrl: String
        get() = "$base/xmltv.php?username=${enc(source.user)}&password=${enc(source.pass)}"

    fun liveUrl(id: String, format: String): String = "$base/live/${source.user}/${source.pass}/$id.$format"
    fun movieUrl(id: String, ext: String): String = "$base/movie/${source.user}/${source.pass}/$id.$ext"
    fun episodeUrl(id: String, ext: String): String = "$base/series/${source.user}/${source.pass}/$id.$ext"

    /** Replay of a past programme kept by the provider ("catch-up"). */
    fun replayUrl(id: String, startText: String, minutes: Int): String =
        "$base/timeshift/${source.user}/${source.pass}/$minutes/$startText/$id.ts"

    /** The programmes of a channel that can be replayed, most recent first; empty when it keeps none. */
    fun replayPrograms(id: String): List<ReplayProgram> = XtreamJson.replayPrograms(
        JSONObject(Http.getString(api("get_simple_data_table", "&stream_id=${enc(id)}"))),
        System.currentTimeMillis() / 1000,
    )

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun api(action: String?, extra: String = ""): String {
        val query = "username=${enc(source.user)}&password=${enc(source.pass)}"
        val act = if (action != null) "&action=$action" else ""
        return "$base/player_api.php?$query$act$extra"
    }

    /** Checks the credentials and returns what the provider says about the account. */
    fun authenticate(): AccountInfo {
        val body = Http.getString(api(null))
        val parsed: JSONObject? = try {
            JSONObject(body).optJSONObject("user_info")
        } catch (e: Exception) {
            null
        }
        val info = parsed ?: throw IOException("Réponse inattendue du serveur (URL incorrecte ?)")
        if (info.optString("auth") != "1") throw IOException("Identifiants refusés par le serveur")
        val account = XtreamJson.accountInfo(info, System.currentTimeMillis())
        val status = account.status
        if (status.isNotBlank() && !status.equals("Active", ignoreCase = true) && !status.equals("Trial", ignoreCase = true)) {
            throw IOException("Compte non actif ($status)")
        }
        return account
    }

    fun liveCategories(): List<Category> = categories("get_live_categories")
    fun vodCategories(): List<Category> = categories("get_vod_categories")
    fun seriesCategories(): List<Category> = categories("get_series_categories")

    private fun categories(action: String): List<Category> = readArray(api(action)) { m ->
        val id = m["category_id"] ?: return@readArray null
        Category(id, m["category_name"].orEmpty())
    }

    /** Hands each live channel over as soon as it is read from the response. */
    fun eachLiveStream(format: String, action: (Channel) -> Unit) {
        var fallbackNumber = 0
        readEach(api("get_live_streams")) { m ->
            val id = m["stream_id"] ?: return@readEach
            fallbackNumber++
            action(
                Channel(
                    id = id,
                    name = m["name"].orEmpty(),
                    logo = m["stream_icon"].orEmpty(),
                    categoryId = m["category_id"].orEmpty(),
                    epgId = m["epg_channel_id"].orEmpty(),
                    url = liveUrl(id, format),
                    number = m["num"]?.toIntOrNull() ?: fallbackNumber,
                )
            )
        }
    }

    fun eachMovie(action: (Vod) -> Unit) {
        readEach(api("get_vod_streams")) { m ->
            val id = m["stream_id"] ?: return@readEach
            val ext = m["container_extension"]?.ifBlank { null } ?: "mp4"
            action(
                Vod(
                    id = id,
                    kind = VodKind.MOVIE,
                    name = m["name"].orEmpty(),
                    poster = m["stream_icon"].orEmpty(),
                    categoryId = m["category_id"].orEmpty(),
                    url = movieUrl(id, ext),
                    rating = m["rating"].orEmpty(),
                    added = m["added"]?.toLongOrNull() ?: 0,
                )
            )
        }
    }

    fun eachSeries(action: (Vod) -> Unit) {
        readEach(api("get_series")) { m ->
            val id = m["series_id"] ?: return@readEach
            action(
                Vod(
                    id = id,
                    kind = VodKind.SERIES,
                    name = m["name"].orEmpty(),
                    poster = m["cover"].orEmpty(),
                    categoryId = m["category_id"].orEmpty(),
                    url = "",
                    rating = m["rating"].orEmpty(),
                    added = m["last_modified"]?.toLongOrNull() ?: 0,
                    plot = m["plot"].orEmpty(),
                    year = (m["releaseDate"] ?: m["release_date"]).orEmpty().take(4),
                )
            )
        }
    }

    fun movieDetails(id: String): VodDetails {
        val root = JSONObject(Http.getString(api("get_vod_info", "&vod_id=$id")))
        val info = root.optJSONObject("info") ?: return VodDetails()
        return VodDetails(
            plot = info.optString("plot").ifBlank { info.optString("description") },
            genre = info.optString("genre"),
            year = info.optString("releasedate").take(4),
            duration = info.optString("duration"),
            cast = info.optString("cast").ifBlank { info.optString("actors") },
            director = info.optString("director"),
            backdrop = backdrop(info),
        )
    }

    fun seriesDetails(id: String): VodDetails {
        val root = JSONObject(Http.getString(api("get_series_info", "&series_id=$id")))
        return XtreamJson.seriesDetails(root, ::episodeUrl)
    }

    private fun backdrop(info: JSONObject): String = XtreamJson.backdrop(info)

    private fun <T> readArray(url: String, map: (Map<String, String>) -> T?): List<T> {
        val out = ArrayList<T>()
        readEach(url) { fields -> map(fields)?.let(out::add) }
        return out
    }

    private fun readEach(url: String, action: (Map<String, String>) -> Unit) {
        Http.open(url).use { response ->
            val body = response.body ?: return
            XtreamJson.readEach(body.charStream(), action)
        }
    }
}

/** Pure parsing of Xtream Codes responses, kept apart from the network for testing. */
object XtreamJson {

    private val replayStart = Regex("(\\d{4}-\\d{2}-\\d{2}) (\\d{2}):(\\d{2})(:\\d{2})?")

    /** Providers send these texts in Base64; a few send them plain. */
    private fun decoded(text: String): String {
        if (text.isBlank()) return ""
        return try {
            val bytes = android.util.Base64.decode(text, android.util.Base64.DEFAULT)
            val plain = String(bytes, Charsets.UTF_8)
            if (plain.isBlank() || plain.contains('\uFFFD')) text else plain
        } catch (e: IllegalArgumentException) {
            text
        }
    }

    /** [root] is the answer to "get_simple_data_table". Only programmes already started and kept are returned. */
    fun replayPrograms(root: JSONObject, now: Long): List<ReplayProgram> {
        val listings = root.optJSONArray("epg_listings") ?: return emptyList()
        val out = ArrayList<ReplayProgram>()
        for (i in 0 until listings.length()) {
            val item = listings.optJSONObject(i) ?: continue
            if (item.optInt("has_archive", 0) != 1) continue
            val start = item.optLong("start_timestamp", 0)
            val end = item.optLong("stop_timestamp", 0)
            val match = replayStart.matchEntire(item.optString("start").trim()) ?: continue
            if (start <= 0 || end <= start || start > now) continue
            out.add(
                ReplayProgram(
                    title = decoded(item.optString("title")).trim().ifBlank { "Programme" },
                    description = decoded(item.optString("description")).trim(),
                    start = start,
                    end = end,
                    startText = match.groupValues[1] + ":" + match.groupValues[2] + "-" + match.groupValues[3],
                )
            )
        }
        return out.sortedByDescending { it.start }
    }

    /** [userInfo] is the "user_info" object of the authentication answer. */
    fun accountInfo(userInfo: JSONObject, now: Long): AccountInfo = AccountInfo(
        status = userInfo.optString("status").takeUnless { it == "null" }.orEmpty(),
        expiresAt = userInfo.optString("exp_date").toLongOrNull() ?: 0,
        maxConnections = userInfo.optString("max_connections").toIntOrNull() ?: 0,
        activeConnections = userInfo.optString("active_cons").toIntOrNull() ?: 0,
        checkedAt = now,
    )

    fun seriesDetails(root: JSONObject, episodeUrl: (id: String, ext: String) -> String): VodDetails {
        val info = root.optJSONObject("info") ?: JSONObject()
        val episodes = ArrayList<Episode>()
        val asObject = root.optJSONObject("episodes")
        val asArray = root.optJSONArray("episodes")
        if (asObject != null) {
            for (key in asObject.keys()) {
                val list = asObject.optJSONArray(key) ?: continue
                readEpisodes(list, key.toIntOrNull() ?: 0, episodes, episodeUrl)
            }
        } else if (asArray != null) {
            for (i in 0 until asArray.length()) {
                val nested = asArray.optJSONArray(i)
                if (nested != null) {
                    readEpisodes(nested, i + 1, episodes, episodeUrl)
                } else {
                    asArray.optJSONObject(i)?.let { readEpisode(it, 1, episodeUrl)?.let(episodes::add) }
                }
            }
        }
        return VodDetails(
            plot = info.optString("plot"),
            genre = info.optString("genre"),
            year = info.optString("releaseDate").ifBlank { info.optString("release_date") }.take(4),
            cast = info.optString("cast"),
            director = info.optString("director"),
            backdrop = backdrop(info),
            seasons = episodes.groupBy { it.season }.toSortedMap().mapValues { e -> e.value.sortedBy { it.number } },
        )
    }

    private fun readEpisodes(
        list: JSONArray,
        season: Int,
        out: MutableList<Episode>,
        episodeUrl: (String, String) -> String,
    ) {
        for (i in 0 until list.length()) {
            list.optJSONObject(i)?.let { readEpisode(it, season, episodeUrl)?.let(out::add) }
        }
    }

    private fun readEpisode(o: JSONObject, defaultSeason: Int, episodeUrl: (String, String) -> String): Episode? {
        val id = o.optString("id")
        if (id.isBlank()) return null
        val ext = o.optString("container_extension").ifBlank { "mp4" }
        val info = o.optJSONObject("info")
        return Episode(
            id = id,
            season = o.optString("season").toIntOrNull() ?: defaultSeason,
            number = o.optString("episode_num").toIntOrNull() ?: 0,
            title = o.optString("title"),
            url = episodeUrl(id, ext),
            plot = info?.optString("plot").orEmpty(),
            image = info?.optString("movie_image").orEmpty(),
            durationMin = durationMinutes(info),
        )
    }

    /** Episode length from "duration_secs" (seconds) or "duration" ("HH:MM:SS"). */
    private fun durationMinutes(info: JSONObject?): Int {
        if (info == null) return 0
        val seconds = info.optString("duration_secs").toIntOrNull()
        if (seconds != null && seconds > 0) return (seconds + 30) / 60
        val parts = info.optString("duration").split(':').mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 60 + parts[1]
            2 -> parts[0]
            else -> 0
        }
    }

    fun backdrop(info: JSONObject): String {
        val array = info.optJSONArray("backdrop_path")
        if (array != null && array.length() > 0) return array.optString(0)
        val single = info.optString("backdrop_path")
        return if (single.startsWith("http")) single else ""
    }

    fun <T> readArray(input: Reader, map: (Map<String, String>) -> T?): List<T> {
        val out = ArrayList<T>()
        readEach(input) { fields -> map(fields)?.let(out::add) }
        return out
    }

    /**
     * Streams a (possibly very large) JSON array of flat objects without loading it in memory.
     * The map given to [action] is reused for the next object: read it, do not keep it.
     */
    fun readEach(input: Reader, action: (Map<String, String>) -> Unit) {
        JsonReader(input).use { reader ->
            reader.isLenient = true
            if (reader.peek() != JsonToken.BEGIN_ARRAY) return
            reader.beginArray()
            val fields = HashMap<String, String>()
            while (reader.hasNext()) {
                if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                    reader.skipValue()
                    continue
                }
                fields.clear()
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    when (reader.peek()) {
                        JsonToken.STRING, JsonToken.NUMBER -> fields[name] = reader.nextString()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                action(fields)
            }
        }
    }
}
