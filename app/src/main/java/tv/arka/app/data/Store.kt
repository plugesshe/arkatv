package tv.arka.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Small JSON-in-SharedPreferences persistence for sources, favourites, list and progress. */
object Store {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.getSharedPreferences("arkatv", Context.MODE_PRIVATE)
    }

    private fun p(): SharedPreferences = prefs ?: error("Store not initialised")

    var activeSourceId: String?
        get() = p().getString("active", null)
        set(value) = p().edit().putString("active", value).apply()

    var liveFormat: String
        get() = p().getString("live_format", "ts") ?: "ts"
        set(value) = p().edit().putString("live_format", value).apply()

    fun loadSources(): List<Source> = readArray("sources") { o ->
        Source(
            id = o.optString("id"),
            type = o.optString("type"),
            name = o.optString("name"),
            url = o.optString("url"),
            user = o.optString("user"),
            pass = o.optString("pass"),
            epgUrl = o.optString("epg"),
        )
    }

    fun saveSources(list: List<Source>) = writeArray("sources", list) { s ->
        JSONObject().put("id", s.id).put("type", s.type).put("name", s.name).put("url", s.url)
            .put("user", s.user).put("pass", s.pass).put("epg", s.epgUrl)
    }

    fun loadSet(name: String): Set<String> = p().getStringSet(name, emptySet())?.toSet() ?: emptySet()

    fun saveSet(name: String, value: Set<String>) = p().edit().putStringSet(name, value).apply()

    fun loadMyList(sourceId: String): List<Vod> = readArray("list_$sourceId") { o ->
        Vod(
            id = o.optString("id"),
            kind = if (o.optString("kind") == "SERIES") VodKind.SERIES else VodKind.MOVIE,
            name = o.optString("name"),
            poster = o.optString("poster"),
            categoryId = o.optString("cat"),
            url = o.optString("url"),
            rating = o.optString("rating"),
            year = o.optString("year"),
            plot = o.optString("plot"),
        )
    }

    fun saveMyList(sourceId: String, list: List<Vod>) = writeArray("list_$sourceId", list) { v ->
        JSONObject().put("id", v.id).put("kind", v.kind.name).put("name", v.name).put("poster", v.poster)
            .put("cat", v.categoryId).put("url", v.url).put("rating", v.rating).put("year", v.year)
            .put("plot", v.plot)
    }

    fun loadProgress(sourceId: String): List<Progress> = readArray("progress_$sourceId") { o ->
        Progress(
            item = PlayItem(
                key = o.optString("key"),
                title = o.optString("title"),
                subtitle = o.optString("subtitle"),
                url = o.optString("url"),
                live = false,
                poster = o.optString("poster"),
            ),
            position = o.optLong("pos"),
            duration = o.optLong("dur"),
            updated = o.optLong("updated"),
        )
    }

    fun saveProgress(sourceId: String, list: List<Progress>) = writeArray("progress_$sourceId", list) { pr ->
        JSONObject().put("key", pr.item.key).put("title", pr.item.title).put("subtitle", pr.item.subtitle)
            .put("url", pr.item.url).put("poster", pr.item.poster).put("pos", pr.position)
            .put("dur", pr.duration).put("updated", pr.updated)
    }

    fun clearSource(sourceId: String) {
        p().edit().remove("list_$sourceId").remove("progress_$sourceId").remove("fav_$sourceId").apply()
    }

    private fun <T> readArray(key: String, map: (JSONObject) -> T): List<T> {
        val raw = p().getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let(map) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun <T> writeArray(key: String, list: List<T>, map: (T) -> JSONObject) {
        val array = JSONArray()
        list.forEach { array.put(map(it)) }
        p().edit().putString(key, array.toString()).apply()
    }
}
