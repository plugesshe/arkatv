package tv.arka.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import tv.arka.app.data.security.SecretBox

/**
 * User data that must survive catalogue refreshes: sources, favourites, "Ma liste" and playback
 * progress. Every value is encrypted with [SecretBox] because stream and playlist URLs embed the
 * provider credentials. Values written in clear text by ArkaTV 0.1.0 are migrated on first start.
 */
object Store {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val created = context.getSharedPreferences("arkatv", Context.MODE_PRIVATE)
        SecretBox.init(created)
        prefs = created
        migrateLegacy(created)
    }

    fun resetForTests() {
        prefs = null
    }

    private fun p(): SharedPreferences = prefs ?: error("Store not initialised")

    /** ArkaTV 0.1.0 stored everything in clear text: re-encrypt it in place, losing nothing. */
    private fun migrateLegacy(prefs: SharedPreferences) {
        val editor = prefs.edit()
        var changed = false
        for ((key, value) in prefs.all) {
            val isBlob = key == "sources" || key.startsWith("list_") || key.startsWith("progress_")
            if (isBlob && value is String) {
                editor.putString("enc_$key", SecretBox.encrypt(value)).remove(key)
                changed = true
            } else if (key.startsWith("fav_") && value is Set<*>) {
                val array = JSONArray()
                value.forEach { array.put(it.toString()) }
                editor.putString("enc_$key", SecretBox.encrypt(array.toString())).remove(key)
                changed = true
            }
        }
        if (changed) editor.commit()
    }

    private fun readBlob(key: String): String? =
        p().getString("enc_$key", null)?.let { SecretBox.decrypt(it) }

    private fun writeBlob(key: String, value: String) {
        p().edit().putString("enc_$key", SecretBox.encrypt(value)).apply()
    }

    var activeSourceId: String?
        get() = p().getString("active", null)
        set(value) = p().edit().putString("active", value).apply()

    var liveFormat: String
        get() = p().getString("live_format", "ts") ?: "ts"
        set(value) = p().edit().putString("live_format", value).apply()

    fun lastSync(sourceId: String): Long = p().getLong("sync_$sourceId", 0)
    fun setLastSync(sourceId: String, time: Long) = p().edit().putLong("sync_$sourceId", time).apply()

    fun lastEpgSync(sourceId: String): Long = p().getLong("epgsync_$sourceId", 0)
    fun setLastEpgSync(sourceId: String, time: Long) = p().edit().putLong("epgsync_$sourceId", time).apply()

    /** Guide URL discovered inside an M3U playlist header, kept so the guide can refresh on its own. */
    fun discoveredEpgUrl(sourceId: String): String = readBlob("epgurl_$sourceId").orEmpty()
    fun setDiscoveredEpgUrl(sourceId: String, url: String) = writeBlob("epgurl_$sourceId", url)

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

    fun loadSearchHistory(): List<String> {
        val raw = readBlob("search_history") ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveSearchHistory(list: List<String>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        writeBlob("search_history", array.toString())
    }

    fun loadFavorites(sourceId: String): Set<String> {
        val raw = readBlob("fav_$sourceId") ?: return emptySet()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.optString(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun saveFavorites(sourceId: String, value: Set<String>) {
        val array = JSONArray()
        value.forEach { array.put(it) }
        writeBlob("fav_$sourceId", array.toString())
    }

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
        p().edit()
            .remove("enc_list_$sourceId").remove("enc_progress_$sourceId").remove("enc_fav_$sourceId")
            .remove("enc_epgurl_$sourceId").remove("sync_$sourceId").remove("epgsync_$sourceId")
            .apply()
    }

    private fun <T> readArray(key: String, map: (JSONObject) -> T): List<T> {
        val raw = readBlob(key) ?: return emptyList()
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
        writeBlob(key, array.toString())
    }
}
