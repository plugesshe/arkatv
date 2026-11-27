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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.arka.app.data.db.ArkaDatabase
import tv.arka.app.data.db.CatalogStore
import java.io.IOException

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

    /** Live channel ids, most recently watched first. */
    var recentChannels by mutableStateOf(listOf<String>())

    /** Live category ids the user chose to hide. */
    var hiddenCategories by mutableStateOf(setOf<String>())

    /** Xtream account details, as last reported by the provider. */
    var account by mutableStateOf<AccountInfo?>(null)

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
        recentChannels = Store.loadRecentChannels(source.id)
        hiddenCategories = Store.loadHiddenCategories(source.id)
        account = Store.loadAccount(source.id)
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
                    val result = sync(source, catalogStore)
                    val discoveredEpgUrl = result.epgUrl
                    val format = Store.liveFormat
                    val fresh = withContext(Dispatchers.IO) { catalogStore.loadSummary(source, format, ROW_SIZE) }
                    if (mine != generation) return@launch
                    apply(fresh)
                    catalogVersion++
                    lastSync = now
                    Store.setLastSync(source.id, now)
                    if (discoveredEpgUrl.isNotBlank()) Store.setDiscoveredEpgUrl(source.id, discoveredEpgUrl)
                    result.account?.let {
                        account = it
                        Store.saveAccount(source.id, it)
                    }
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

    /**
     * Downloads the catalogue and writes it to the store as it arrives, in one transaction:
     * the full lists never sit in memory, and a failure leaves the previous catalogue untouched.
     * Returns the guide URL found in an M3U header, if any.
     */
    private class SyncResult(val epgUrl: String, val account: AccountInfo?)

    private suspend fun sync(source: Source, catalogStore: CatalogStore): SyncResult {
        // A provider without films or series must not block the channels. But once a kind has
        // been stored, failing to download it again fails the refresh rather than emptying it.
        val hadMovies = catalogStore.count(source, VodKind.MOVIE) > 0
        val hadSeries = catalogStore.count(source, VodKind.SERIES) > 0
        val format = Store.liveFormat
        return withContext(Dispatchers.IO) {
            var epgUrl = ""
            var info: AccountInfo? = null
            if (source.type != "xtream") {
                catalogStore.replaceBlocking(source) { writer ->
                    Http.open(source.url).use { response ->
                        val body = response.body ?: throw IOException("Playlist vide")
                        val header = M3u.parse(body.charStream(), writer::channel, writer::vod)
                        writer.liveCategories(header.liveCategories)
                        writer.movieCategories(header.vodCategories)
                        epgUrl = header.epgUrl
                    }
                }
            } else {
                val api = XtreamApi(source)
                info = api.authenticate()
                catalogStore.replaceBlocking(source) { writer ->
                    writer.liveCategories(api.liveCategories())
                    api.eachLiveStream(format, writer::channel)
                    optional(required = hadMovies) {
                        writer.movieCategories(api.vodCategories())
                        api.eachMovie(writer::vod)
                    }
                    optional(required = hadSeries) {
                        writer.seriesCategories(api.seriesCategories())
                        api.eachSeries(writer::vod)
                    }
                }
            }
            SyncResult(epgUrl, info)
        }
    }

    private inline fun optional(required: Boolean, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            if (required) throw e
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

    fun recordRecentChannel(channelId: String) {
        val source = active ?: return
        recentChannels = RecentRules.push(recentChannels, channelId)
        Store.saveRecentChannels(source.id, recentChannels)
    }

    fun setCategoryHidden(categoryId: String, hidden: Boolean) {
        val source = active ?: return
        hiddenCategories = if (hidden) hiddenCategories + categoryId else hiddenCategories - categoryId
        Store.saveHiddenCategories(source.id, hiddenCategories)
    }

    fun renameSource(source: Source, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val renamed = source.copy(name = clean)
        sources = sources.map { if (it.id == source.id) renamed else it }
        Store.saveSources(sources)
        if (active?.id == source.id) active = renamed
    }

    /** Moves a source up (negative [delta]) or down in the list. */
    fun moveSource(source: Source, delta: Int) {
        val from = sources.indexOfFirst { it.id == source.id }
        val to = from + delta
        if (from < 0 || to < 0 || to > sources.lastIndex) return
        val list = sources.toMutableList()
        list.add(to, list.removeAt(from))
        sources = list
        Store.saveSources(sources)
    }

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
