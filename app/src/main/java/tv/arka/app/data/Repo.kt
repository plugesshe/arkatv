package tv.arka.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.arka.app.data.db.ArkaDatabase
import tv.arka.app.data.db.CatalogStore

/**
 * App-wide state, offline-first: the last valid catalogue is shown from the local cache at once,
 * then refreshed in the background when it is stale. A failed refresh never discards the cache.
 * All state writes happen on the main thread.
 */
object Repo {
    private const val CATALOG_TTL_MS = 12 * 3600 * 1000L
    private const val EPG_TTL_MS = 6 * 3600 * 1000L

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var sources by mutableStateOf(listOf<Source>())
    var active by mutableStateOf<Source?>(null)

    /** True only while there is nothing to show yet. */
    var loading by mutableStateOf(false)

    /** True while the catalogue is being refreshed behind already displayed content. */
    var refreshing by mutableStateOf(false)

    /** Blocking error: nothing could be loaded and nothing is cached. */
    var error by mutableStateOf<String?>(null)

    /** Non-blocking error: the refresh failed, the cached catalogue stays in use. */
    var syncError by mutableStateOf<String?>(null)
    var lastSync by mutableLongStateOf(0L)
    var epgLoading by mutableStateOf(false)

    var liveCategories by mutableStateOf(listOf<Category>())
    var channels by mutableStateOf(listOf<Channel>())
    var vodCategories by mutableStateOf(listOf<Category>())
    var movies by mutableStateOf(listOf<Vod>())
    var seriesCategories by mutableStateOf(listOf<Category>())
    var series by mutableStateOf(listOf<Vod>())
    var epg by mutableStateOf(mapOf<String, List<Program>>())

    var favorites by mutableStateOf(setOf<String>())
    var myList by mutableStateOf(listOf<Vod>())
    var progress by mutableStateOf(listOf<Progress>())

    private var initialised = false
    private var store: CatalogStore? = null
    private var loadJob: Job? = null
    private var generation = 0
    private var loadedSourceId: String? = null
    private val detailsCache = HashMap<String, VodDetails>()

    fun init(context: Context) {
        if (initialised) return
        initialised = true
        store = CatalogStore(ArkaDatabase.create(context.applicationContext))
        sources = Store.loadSources()
        val wanted = Store.activeSourceId
        val first = sources.firstOrNull { it.id == wanted } ?: sources.firstOrNull()
        if (first != null) select(first)
    }

    fun select(source: Source) {
        active = source
        Store.activeSourceId = source.id
        favorites = Store.loadFavorites(source.id)
        myList = Store.loadMyList(source.id)
        progress = Store.loadProgress(source.id)
        open(source, force = false)
    }

    /** Manual refresh requested by the user. */
    fun reload() {
        active?.let { open(it, force = true) }
    }

    fun addSource(source: Source) {
        sources = sources + source
        Store.saveSources(sources)
        select(source)
    }

    fun removeSource(source: Source) {
        sources = sources.filter { it.id != source.id }
        Store.saveSources(sources)
        Store.clearSource(source.id)
        val catalogStore = store
        scope.launch {
            try {
                withContext(Dispatchers.IO) { catalogStore?.delete(source.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The cache of a removed source is unreachable anyway.
            }
        }
        if (active?.id == source.id) {
            loadJob?.cancel()
            generation++
            clear()
            loadedSourceId = null
            active = null
            loading = false
            refreshing = false
            epgLoading = false
            sources.firstOrNull()?.let { select(it) }
        }
    }

    private fun clear() {
        apply(Catalog())
        epg = emptyMap()
        detailsCache.clear()
    }

    private fun apply(catalog: Catalog) {
        liveCategories = catalog.liveCategories
        channels = catalog.channels
        vodCategories = catalog.vodCategories
        movies = catalog.movies
        seriesCategories = catalog.seriesCategories
        series = catalog.series
    }

    private fun hasContent(): Boolean = channels.isNotEmpty() || movies.isNotEmpty() || series.isNotEmpty()

    private fun open(source: Source, force: Boolean) {
        loadJob?.cancel()
        val mine = ++generation
        val switching = loadedSourceId != source.id
        if (switching) {
            clear()
            loadedSourceId = null
        }
        error = null
        syncError = null
        refreshing = false
        epgLoading = false
        loading = !hasContent()
        val catalogStore = store

        loadJob = scope.launch {
            val now = System.currentTimeMillis()

            // 1. Cached catalogue and guide first: the interface is usable without the network.
            if (switching && catalogStore != null) {
                try {
                    val format = Store.liveFormat
                    val cached = withContext(Dispatchers.IO) { catalogStore.load(source, format) }
                    if (cached != null) {
                        apply(cached)
                        epg = withContext(Dispatchers.IO) { catalogStore.loadEpg(source.id, now / 1000 - 2 * 3600) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // An unreadable cache is only a slower start: the network path below rebuilds it.
                }
            }
            if (mine != generation) return@launch
            loadedSourceId = source.id
            lastSync = Store.lastSync(source.id)
            val hasCache = hasContent()
            loading = !hasCache

            // 2. Refresh from the provider when asked, when nothing is cached, or when stale.
            if (force || !hasCache || now - lastSync > CATALOG_TTL_MS) {
                refreshing = hasCache
                try {
                    val fetched = fetch(source)
                    if (mine != generation) return@launch
                    apply(fetched.catalog)
                    detailsCache.clear()
                    lastSync = now
                    Store.setLastSync(source.id, now)
                    if (fetched.epgUrl.isNotBlank()) Store.setDiscoveredEpgUrl(source.id, fetched.epgUrl)
                    withContext(Dispatchers.IO) { catalogStore?.save(source, fetched.catalog) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val message = e.message ?: "Erreur de chargement"
                    if (mine == generation) {
                        if (hasCache) syncError = message else error = message
                    }
                } finally {
                    if (mine == generation) {
                        refreshing = false
                        loading = false
                    }
                }
            }
            if (mine != generation) return@launch
            loading = false

            // 3. Guide: optional, refreshed on its own schedule, never blocks the channels.
            val epgUrl = when {
                source.epgUrl.isNotBlank() -> source.epgUrl
                source.type == "xtream" -> XtreamApi(source).epgUrl
                else -> Store.discoveredEpgUrl(source.id)
            }
            val epgStale = now - Store.lastEpgSync(source.id) > EPG_TTL_MS
            if (epgUrl.isNotBlank() && channels.isNotEmpty() && (force || epg.isEmpty() || epgStale)) {
                val wanted = channels.mapNotNull { c -> c.epgId.lowercase().ifBlank { null } }.toSet()
                if (wanted.isNotEmpty()) {
                    epgLoading = true
                    try {
                        val guide = withContext(Dispatchers.IO) { Epg.load(epgUrl, wanted) }
                        if (mine != generation) return@launch
                        if (guide.isNotEmpty()) {
                            epg = guide
                            Store.setLastEpgSync(source.id, now)
                            withContext(Dispatchers.IO) { catalogStore?.saveEpg(source.id, guide) }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Keep whatever guide is already displayed.
                    } finally {
                        if (mine == generation) epgLoading = false
                    }
                }
            }
        }
    }

    private class Fetched(val catalog: Catalog, val epgUrl: String)

    private suspend fun fetch(source: Source): Fetched {
        if (source.type != "xtream") {
            val result = withContext(Dispatchers.IO) { M3u.load(source.url) }
            return Fetched(
                Catalog(
                    liveCategories = result.liveCategories,
                    channels = result.channels,
                    vodCategories = result.vodCategories,
                    movies = result.movies,
                ),
                result.epgUrl,
            )
        }
        val api = XtreamApi(source)
        val format = Store.liveFormat
        withContext(Dispatchers.IO) { api.authenticate() }
        return coroutineScope {
            val live = async(Dispatchers.IO) { api.liveCategories() to api.liveStreams(format) }
            val vod = async(Dispatchers.IO) {
                try {
                    api.vodCategories() to api.vodStreams()
                } catch (e: Exception) {
                    null
                }
            }
            val shows = async(Dispatchers.IO) {
                try {
                    api.seriesCategories() to api.series()
                } catch (e: Exception) {
                    null
                }
            }
            val liveResult = live.await()
            val vodResult = vod.await()
            val showsResult = shows.await()
            Fetched(
                Catalog(
                    liveCategories = liveResult.first,
                    channels = liveResult.second,
                    vodCategories = vodResult?.first.orEmpty(),
                    movies = vodResult?.second.orEmpty(),
                    seriesCategories = showsResult?.first.orEmpty(),
                    series = showsResult?.second.orEmpty(),
                ),
                "",
            )
        }
    }

    /** Switches Xtream live URLs between MPEG-TS and HLS without any network access. */
    fun applyLiveFormat(format: String) {
        Store.liveFormat = format
        val source = active ?: return
        if (source.type != "xtream") return
        val api = XtreamApi(source)
        channels = channels.map { it.copy(url = api.liveUrl(it.id, format)) }
    }

    fun programs(epgId: String): List<Program> =
        if (epgId.isBlank()) emptyList() else epg[epgId.lowercase()].orEmpty()

    fun nowPlaying(epgId: String, now: Long): Program? =
        programs(epgId).firstOrNull { it.start <= now && now < it.end }

    fun toggleFavorite(channelId: String) {
        val source = active ?: return
        favorites = if (channelId in favorites) favorites - channelId else favorites + channelId
        Store.saveFavorites(source.id, favorites)
    }

    fun inMyList(vod: Vod): Boolean = myList.any { it.id == vod.id && it.kind == vod.kind }

    fun toggleMyList(vod: Vod) {
        val source = active ?: return
        myList = if (inMyList(vod)) {
            myList.filterNot { it.id == vod.id && it.kind == vod.kind }
        } else {
            listOf(vod) + myList
        }
        Store.saveMyList(source.id, myList)
    }

    fun progressFor(key: String): Progress? = progress.firstOrNull { it.item.key == key }

    fun saveProgress(item: PlayItem, position: Long, duration: Long) {
        val source = active ?: return
        if (item.live || duration <= 0) return
        progress = ProgressRules.update(progress, item, position, duration, System.currentTimeMillis())
        Store.saveProgress(source.id, progress)
    }

    suspend fun details(vod: Vod): VodDetails {
        val key = "${vod.kind}:${vod.id}"
        detailsCache[key]?.let { return it }
        val source = active
        val fallback = VodDetails(plot = vod.plot, year = vod.year)
        if (source == null || source.type != "xtream") return fallback
        val loaded = withContext(Dispatchers.IO) {
            val api = XtreamApi(source)
            if (vod.kind == VodKind.SERIES) api.seriesDetails(vod.id) else api.movieDetails(vod.id)
        }
        val merged = loaded.copy(
            plot = loaded.plot.ifBlank { vod.plot },
            year = loaded.year.ifBlank { vod.year },
        )
        detailsCache[key] = merged
        return merged
    }
}

/** Rules for the "Reprendre la lecture" list, kept pure so they can be tested. */
object ProgressRules {
    const val MAX_ENTRIES = 40
    private const val MIN_POSITION_MS = 5_000L
    private const val FINISHED_RATIO = 0.95

    fun update(current: List<Progress>, item: PlayItem, position: Long, duration: Long, now: Long): List<Progress> {
        val others = current.filterNot { it.item.key == item.key }
        val finished = position > duration * FINISHED_RATIO
        return if (finished || position < MIN_POSITION_MS) {
            others
        } else {
            (listOf(Progress(item, position, duration, now)) + others).take(MAX_ENTRIES)
        }
    }
}
