package tv.arka.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

    /** Changes whenever the stored catalogue changes, so screens that query it can refresh. */
    var catalogVersion by mutableIntStateOf(0)

    var liveCategories by mutableStateOf(listOf<Category>())
    var channels by mutableStateOf(listOf<Channel>())

    // Films and series stay in the database: only counts, non-empty categories and the
    // "recently added" rows are kept in memory. Everything else is read page by page.
    var vodCategories by mutableStateOf(listOf<Category>())
    var seriesCategories by mutableStateOf(listOf<Category>())
    var movieCount by mutableIntStateOf(0)
    var seriesCount by mutableIntStateOf(0)
    var latestMovies by mutableStateOf(listOf<Vod>())
    var latestSeries by mutableStateOf(listOf<Vod>())
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
    private val rowCache = HashMap<String, List<Vod>>()

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
        apply(null)
        epg = emptyMap()
    }

    private fun apply(summary: CatalogStore.Summary?) {
        liveCategories = summary?.liveCategories.orEmpty()
        channels = summary?.channels.orEmpty()
        vodCategories = summary?.vodCategories.orEmpty()
        seriesCategories = summary?.seriesCategories.orEmpty()
        movieCount = summary?.movieCount ?: 0
        seriesCount = summary?.seriesCount ?: 0
        latestMovies = summary?.latestMovies.orEmpty()
        latestSeries = summary?.latestSeries.orEmpty()
        rowCache.clear()
        detailsCache.clear()
    }

    private fun hasContent(): Boolean = channels.isNotEmpty() || movieCount > 0 || seriesCount > 0

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
        val catalogStore = store ?: return

        loadJob = scope.launch {
            val now = System.currentTimeMillis()

            // 1. Cached catalogue and guide first: the interface is usable without the network.
            if (switching) {
                try {
                    val format = Store.liveFormat
                    val cached = withContext(Dispatchers.IO) { catalogStore.loadSummary(source, format, ROW_SIZE) }
                    if (cached != null) {
                        apply(cached)
                        catalogVersion++
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
                    val discoveredEpgUrl = sync(source, catalogStore)
                    val format = Store.liveFormat
                    val fresh = withContext(Dispatchers.IO) { catalogStore.loadSummary(source, format, ROW_SIZE) }
                    if (mine != generation) return@launch
                    apply(fresh)
                    catalogVersion++
                    lastSync = now
                    Store.setLastSync(source.id, now)
                    if (discoveredEpgUrl.isNotBlank()) Store.setDiscoveredEpgUrl(source.id, discoveredEpgUrl)
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

    /**
     * Downloads the catalogue and stores it in one transaction. The full lists only live for the
     * duration of this call. Returns the guide URL found in an M3U header, if any.
     */
    private suspend fun sync(source: Source, catalogStore: CatalogStore): String {
        val fetched = fetch(source)
        withContext(Dispatchers.IO) { catalogStore.save(source, fetched.catalog) }
        return fetched.epgUrl
    }

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

    const val SEARCH_PAGE = 40
    const val GRID_PAGE = 60
    const val ROW_SIZE = 20

    /** [normalized] comes from [TextNormalizer.normalize]. Results are ranked and paginated. */
    suspend fun searchChannels(normalized: String, offset: Int): List<Channel> {
        val source = active ?: return emptyList()
        val catalogStore = store
        val format = Store.liveFormat
        val memory = channels
        return withContext(Dispatchers.IO) {
            try {
                checkNotNull(catalogStore).searchChannels(source, format, normalized, SEARCH_PAGE, offset)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Stored catalogue unavailable: fall back to the channels held in memory.
                val terms = TextNormalizer.terms(normalized)
                memory.asSequence().filter { TextNormalizer.matches(it.name, terms) }
                    .drop(offset).take(SEARCH_PAGE).toList()
            }
        }
    }

    suspend fun searchVods(kind: VodKind, normalized: String, offset: Int): List<Vod> {
        val source = active ?: return emptyList()
        val catalogStore = store ?: return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                catalogStore.searchVods(source, kind, normalized, SEARCH_PAGE, offset)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /** One page of films or series ([GRID_PAGE] items), optionally limited to a category. */
    suspend fun vodPage(kind: VodKind, categoryId: String?, sort: VodSort, offset: Int): List<Vod> =
        vodRange(kind, categoryId, sort, GRID_PAGE, offset)

    private suspend fun vodRange(kind: VodKind, categoryId: String?, sort: VodSort, limit: Int, offset: Int): List<Vod> {
        val source = active ?: return emptyList()
        val catalogStore = store ?: return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                catalogStore.vodPage(source, kind, categoryId, sort, limit, offset)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /** First items of a category for a home row; null until [categoryRow] has loaded it. */
    fun cachedRow(kind: VodKind, categoryId: String): List<Vod>? = rowCache["$kind:$categoryId"]

    suspend fun categoryRow(kind: VodKind, categoryId: String): List<Vod> {
        val version = catalogVersion
        val row = vodRange(kind, categoryId, VodSort.PROVIDER, ROW_SIZE, 0)
        if (version == catalogVersion) rowCache["$kind:$categoryId"] = row
        return row
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
