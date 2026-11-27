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
        profileScope = scopeOf(activeProfileId)
    }

    fun resetForTests() {
        prefs = null
        profileScope = ""
    }

    // ----- Profiles. The main profile keeps the historical keys, so data from earlier versions is its own.

    private var profileScope = ""

    private fun scopeOf(profileId: String): String = if (profileId == Profile.MAIN_ID) "" else "p${profileId}_"

    /** Key of a value that belongs to the active profile. */
    private fun scoped(key: String): String = profileScope + key

    var activeProfileId: String
        get() = p().getString("profile", Profile.MAIN_ID) ?: Profile.MAIN_ID
        set(value) {
            p().edit().putString("profile", value).apply()
            profileScope = scopeOf(value)
        }

    fun loadProfiles(): List<Profile> {
        val stored = readArray("profiles") { o -> Profile(o.optString("id"), o.optString("name"), o.optInt("color")) }
            .filter { it.id.isNotBlank() }
        return if (stored.any { it.isMain }) stored else listOf(Profile(Profile.MAIN_ID, "Principal", 0)) + stored
    }

    fun saveProfiles(list: List<Profile>) = writeArray("profiles", list) { profile ->
        JSONObject().put("id", profile.id).put("name", profile.name).put("color", profile.color)
    }

    /** Removes everything a deleted profile had stored. */
    fun deleteProfileData(profileId: String) {
        if (profileId == Profile.MAIN_ID) return
        val prefix = "enc_" + scopeOf(profileId)
        val editor = p().edit()
        p().all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
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

    /** Hours before a stored catalogue is refreshed when the app opens. 0 turns automatic refresh off. */
    var catalogRefreshHours: Int
        get() = p().getInt("refresh_hours", 12)
        set(value) = p().edit().putInt("refresh_hours", value).apply()

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
        val raw = readBlob(scoped("search_history")) ?: return emptyList()
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
        writeBlob(scoped("search_history"), array.toString())
    }

    private fun loadStrings(key: String): List<String> {
        val raw = readBlob(key) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.optString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveStrings(key: String, values: Collection<String>) {
        val array = JSONArray()
        values.forEach { array.put(it) }
        writeBlob(key, array.toString())
    }

    fun loadRecentChannels(sourceId: String): List<String> = loadStrings(scoped("recent_$sourceId"))
    fun saveRecentChannels(sourceId: String, ids: List<String>) = saveStrings(scoped("recent_$sourceId"), ids)

    /** Keys ("m:<id>", "e:<id>") of the films and episodes the profile has watched. */
    fun loadWatched(sourceId: String): Set<String> = loadStrings(scoped("watched_$sourceId")).toSet()
    fun saveWatched(sourceId: String, keys: Set<String>) = saveStrings(scoped("watched_$sourceId"), keys)

    fun loadFollowed(sourceId: String): List<Vod> = loadVods(scoped("followed_$sourceId"))
    fun saveFollowed(sourceId: String, list: List<Vod>) = saveVods(scoped("followed_$sourceId"), list)

    fun loadHiddenChannels(sourceId: String): Set<String> = loadStrings(scoped("hiddench_$sourceId")).toSet()
    fun saveHiddenChannels(sourceId: String, ids: Set<String>) = saveStrings(scoped("hiddench_$sourceId"), ids)

    /** Favourite channel ids in the order chosen by the user. */
    fun loadFavoriteOrder(sourceId: String): List<String> = loadStrings(scoped("fav_$sourceId")).distinct()

    fun loadHiddenCategories(sourceId: String): Set<String> = loadStrings(scoped("hidden_$sourceId")).toSet()
    fun saveHiddenCategories(sourceId: String, ids: Set<String>) = saveStrings(scoped("hidden_$sourceId"), ids)

    fun loadAccount(sourceId: String): AccountInfo? {
        val raw = readBlob("account_$sourceId") ?: return null
        return try {
            val o = JSONObject(raw)
            AccountInfo(
                status = o.optString("status"),
                expiresAt = o.optLong("exp"),
                maxConnections = o.optInt("max"),
                activeConnections = o.optInt("active"),
                checkedAt = o.optLong("checked"),
            )
        } catch (e: Exception) {
            null
        }
    }

    fun saveAccount(sourceId: String, info: AccountInfo) = writeBlob(
        "account_$sourceId",
        JSONObject().put("status", info.status).put("exp", info.expiresAt).put("max", info.maxConnections)
            .put("active", info.activeConnections).put("checked", info.checkedAt).toString(),
    )

    fun loadFavorites(sourceId: String): Set<String> {
        val raw = readBlob(scoped("fav_$sourceId")) ?: return emptySet()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.optString(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun saveFavorites(sourceId: String, value: Collection<String>) {
        val array = JSONArray()
        value.forEach { array.put(it) }
        writeBlob(scoped("fav_$sourceId"), array.toString())
    }

    fun loadMyList(sourceId: String): List<Vod> = loadVods(scoped("list_$sourceId"))

    fun saveMyList(sourceId: String, list: List<Vod>) = saveVods(scoped("list_$sourceId"), list)

    private fun loadVods(key: String): List<Vod> = readArray(key) { o ->
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

    private fun saveVods(key: String, list: List<Vod>) = writeArray(key, list) { v ->
        JSONObject().put("id", v.id).put("kind", v.kind.name).put("name", v.name).put("poster", v.poster)
            .put("cat", v.categoryId).put("url", v.url).put("rating", v.rating).put("year", v.year)
            .put("plot", v.plot)
    }

    fun loadProgress(sourceId: String): List<Progress> = readArray(scoped("progress_$sourceId")) { o ->
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

    fun saveProgress(sourceId: String, list: List<Progress>) = writeArray(scoped("progress_$sourceId"), list) { pr ->
        JSONObject().put("key", pr.item.key).put("title", pr.item.title).put("subtitle", pr.item.subtitle)
            .put("url", pr.item.url).put("poster", pr.item.poster).put("pos", pr.position)
            .put("dur", pr.duration).put("updated", pr.updated)
    }

    /** Forgets a source for every profile: its lists, history, preferences and sync dates. */
    fun clearSource(sourceId: String) {
        val perProfile = listOf("list", "progress", "fav", "recent", "hidden", "hiddench", "watched", "followed").map { "${it}_$sourceId" }
        val editor = p().edit()
        for (key in p().all.keys) {
            if (!key.startsWith("enc_")) continue
            val name = key.removePrefix("enc_")
            if (perProfile.any { name == it || (name.startsWith("p") && name.endsWith("_$it")) }) editor.remove(key)
        }
        editor.remove("enc_epgurl_$sourceId").remove("enc_account_$sourceId")
            .remove("sync_$sourceId").remove("epgsync_$sourceId")
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
