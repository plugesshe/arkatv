package tv.arka.app.data

import androidx.compose.runtime.getValue
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

/** App-wide state. All writes happen on the main thread. */
object Repo {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var sources by mutableStateOf(listOf<Source>())
    var active by mutableStateOf<Source?>(null)

    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
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
    private var loadJob: Job? = null
    private val detailsCache = HashMap<String, VodDetails>()

    fun init() {
        if (initialised) return
        initialised = true
        sources = Store.loadSources()
        val wanted = Store.activeSourceId
        val first = sources.firstOrNull { it.id == wanted } ?: sources.firstOrNull()
        if (first != null) select(first)
    }

    fun select(source: Source) {
        active = source
        Store.activeSourceId = source.id
        favorites = Store.loadSet("fav_${source.id}")
        myList = Store.loadMyList(source.id)
        progress = Store.loadProgress(source.id)
        reload()
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
        if (active?.id == source.id) {
            loadJob?.cancel()
            clear()
            active = null
            loading = false
            sources.firstOrNull()?.let { select(it) }
        }
    }

    private fun clear() {
        liveCategories = emptyList()
        channels = emptyList()
        vodCategories = emptyList()
        movies = emptyList()
        seriesCategories = emptyList()
        series = emptyList()
        epg = emptyMap()
        detailsCache.clear()
    }

    fun reload() {
        val source = active ?: return
        loadJob?.cancel()
        clear()
        loading = true
        epgLoading = false
        error = null
        loadJob = scope.launch {
            var epgUrl = source.epgUrl
            try {
                if (source.type == "xtream") {
                    val api = XtreamApi(source)
                    val format = Store.liveFormat
                    withContext(Dispatchers.IO) { api.authenticate() }
                    coroutineScope {
                        val live = async(Dispatchers.IO) { api.liveCategories() to api.liveStreams(format) }
                        val vod = async(Dispatchers.IO) {
                            runCatching { api.vodCategories() to api.vodStreams() }.getOrNull()
                        }
                        val shows = async(Dispatchers.IO) {
                            runCatching { api.seriesCategories() to api.series() }.getOrNull()
                        }
                        live.await().let {
                            liveCategories = it.first
                            channels = it.second
                        }
                        vod.await()?.let {
                            vodCategories = it.first
                            movies = it.second
                        }
                        shows.await()?.let {
                            seriesCategories = it.first
                            series = it.second
                        }
                    }
                    if (epgUrl.isBlank()) epgUrl = api.epgUrl
                } else {
                    val result = withContext(Dispatchers.IO) { M3u.load(source.url) }
                    liveCategories = result.liveCategories
                    channels = result.channels
                    vodCategories = result.vodCategories
                    movies = result.movies
                    if (epgUrl.isBlank()) epgUrl = result.epgUrl
                }
                loading = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Erreur de chargement"
                loading = false
                return@launch
            }

            if (epgUrl.isNotBlank() && channels.isNotEmpty()) {
                epgLoading = true
                try {
                    val wanted = channels.mapNotNull { c -> c.epgId.lowercase().ifBlank { null } }.toSet()
                    if (wanted.isNotEmpty()) {
                        epg = withContext(Dispatchers.IO) { Epg.load(epgUrl, wanted) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The guide is optional: channels stay usable without it.
                } finally {
                    epgLoading = false
                }
            }
        }
    }

    fun programs(epgId: String): List<Program> =
        if (epgId.isBlank()) emptyList() else epg[epgId.lowercase()].orEmpty()

    fun nowPlaying(epgId: String, now: Long): Program? =
        programs(epgId).firstOrNull { it.start <= now && now < it.end }

    fun toggleFavorite(channelId: String) {
        val source = active ?: return
        favorites = if (channelId in favorites) favorites - channelId else favorites + channelId
        Store.saveSet("fav_${source.id}", favorites)
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
        val others = progress.filterNot { it.item.key == item.key }
        val finished = position > duration * 0.95
        progress = if (finished || position < 5_000) {
            others
        } else {
            (listOf(Progress(item, position, duration, System.currentTimeMillis())) + others).take(40)
        }
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
