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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    var movieCategoryCounts by mutableStateOf(mapOf<String, Int>())
    var seriesCategoryCounts by mutableStateOf(mapOf<String, Int>())

    var profiles by mutableStateOf(listOf<Profile>())
    var activeProfile by mutableStateOf(Profile(Profile.MAIN_ID, "Principal", 0))

    /** False until the viewer has picked a profile in this session (only asked when there are several). */
    var profileChosen by mutableStateOf(false)
    var epg by mutableStateOf(mapOf<String, List<Program>>())

    var favorites by mutableStateOf(setOf<String>())

    /** The same favourites, in the order chosen by the user. */
    var favoriteOrder by mutableStateOf(listOf<String>())

    /** Live channel ids the user chose to hide. */
    var hiddenChannels by mutableStateOf(setOf<String>())
    var myList by mutableStateOf(listOf<Vod>())
    var progress by mutableStateOf(listOf<Progress>())

    /** Keys of the films ("m:<id>") and episodes ("e:<id>") marked as watched. */
    var watched by mutableStateOf(setOf<String>())

    /** Series the profile follows. */
    var followed by mutableStateOf(listOf<Vod>())

    /** Live channel ids, most recently watched first. */
    var recentChannels by mutableStateOf(listOf<String>())

    /** Live category ids the user chose to hide. */
    var hiddenCategories by mutableStateOf(setOf<String>())

    /** Programmes of [channel] the provider keeps for replay; null when it could not be asked. */
    suspend fun replayPrograms(channel: Channel): List<ReplayProgram>? {
        val source = active ?: return null
        if (source.type != "xtream") return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                XtreamApi(source).replayPrograms(channel.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    fun replayItem(channel: Channel, program: ReplayProgram): PlayItem? {
        val source = active ?: return null
        val minutes = ((program.end - program.start + 59) / 60).toInt().coerceIn(1, 24 * 60)
        return PlayItem(
            key = "r:${channel.id}:${program.start}",
            title = program.title,
            subtitle = channel.name + " · replay",
            url = XtreamApi(source).replayUrl(channel.id, program.startText, minutes),
            live = false,
            poster = channel.logo,
        )
    }

    // ----- Only what this device can play.

    /** What the device decodes; set once at start-up from what the system reports. */
    var device: DeviceProfile = DeviceProfile.UNKNOWN
    var hideIncompatible by mutableStateOf(true)

    /** Titles whose playback failed on this device because of a decoder ("m:<id>", "c:<id>"). */
    var unplayable by mutableStateOf(setOf<String>())

    private fun updateVisibility() {
        val catalogStore = store ?: return
        if (hideIncompatible) {
            catalogStore.hiddenPatterns = Playability.hiddenNamePatterns(device)
            catalogStore.hiddenIds = mapOf(CatalogStore.MOVIE to unplayable.filter { it.startsWith("m:") }.map { it.removePrefix("m:") }.toSet())
        } else {
            catalogStore.hiddenPatterns = emptyList()
            catalogStore.hiddenIds = emptyMap()
        }
    }

    /** Reads the catalogue again from the device after a change of what is shown. Nothing is downloaded. */
    private fun refreshFromStore() {
        val source = active ?: return
        val catalogStore = store ?: return
        scope.launch {
            try {
                val summary = withContext(Dispatchers.IO) { catalogStore.loadSummary(source, Store.liveFormat, ROW_SIZE) }
                if (active?.id == source.id && summary != null) {
                    apply(summary)
                    catalogVersion++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The lists keep what they had.
            }
        }
    }

    fun updateHideIncompatible(enabled: Boolean) {
        Store.hideIncompatible = enabled
        hideIncompatible = enabled
        updateVisibility()
        refreshFromStore()
    }

    /** Called by the player when a title could not be decoded: it leaves the lists of this device. */
    fun markUnplayable(key: String) {
        val source = active ?: return
        if (key in unplayable || !(key.startsWith("m:") || key.startsWith("c:"))) return
        unplayable = unplayable + key
        Store.saveUnplayable(source.id, unplayable)
        updateVisibility()
        if (hideIncompatible) refreshFromStore()
    }

    fun clearUnplayable() {
        val source = active ?: return
        unplayable = emptySet()
        Store.saveUnplayable(source.id, unplayable)
        updateVisibility()
        refreshFromStore()
    }

    private fun hiddenChannel(channel: Channel): Boolean =
        hideIncompatible && (("c:" + channel.id) in unplayable || Playability.isHiddenName(channel.name, device))

    /** False for a saved title (list, history) this device is known not to play. */
    fun playableHere(key: String, name: String): Boolean =
        !hideIncompatible || !(key in unplayable || Playability.isHiddenName(name, device))

    /** True for a title this device cannot decode, whatever the filter says: used to grey it out. */
    fun tooHeavy(tags: String): Boolean = Playability.isHiddenName(tags, device)

    fun visibleMyList(): List<Vod> = myList.filter { playableHere("m:" + it.id, it.name + " " + it.tags) }
    fun visibleProgress(): List<Progress> = progress.filter { it.item.key !in unplayable || !hideIncompatible }

    /** Channel groups made by the user for the active profile and source. */
    var channelGroups by mutableStateOf(listOf<ChannelGroup>())

    private fun saveGroups(groups: List<ChannelGroup>) {
        val source = active ?: return
        channelGroups = groups
        Store.saveChannelGroups(source.id, groups)
    }

    /** Adds the channel to the group, creating the group when it does not exist yet. */
    fun addToGroup(name: String, channelId: String) {
        val clean = name.trim().take(30)
        if (clean.isEmpty()) return
        val existing = channelGroups.firstOrNull { it.name.equals(clean, ignoreCase = true) }
        saveGroups(
            if (existing == null) {
                channelGroups + ChannelGroup(clean, listOf(channelId))
            } else {
                channelGroups.map { if (it === existing && channelId !in it.ids) it.copy(ids = it.ids + channelId) else it }
            }
        )
    }

    fun removeFromGroup(name: String, channelId: String) =
        saveGroups(channelGroups.map { if (it.name == name) it.copy(ids = it.ids - channelId) else it })

    fun deleteGroup(name: String) = saveGroups(channelGroups.filter { it.name != name })

    // ----- Catalogue enriched with TMDB (the user's own key).

    var tmdbToken by mutableStateOf("")
    var tmdbStatus by mutableStateOf("")
    var tmdbMatched by mutableIntStateOf(0)
    var tmdbChecked by mutableIntStateOf(0)
    var smartHome by mutableStateOf(true)

    /** Home rows built from matched titles; empty until enough of them are known. */
    var smartRows by mutableStateOf(listOf<SmartRow>())

    /** TMDB genres with their number of works, for the Films and Séries pages; empty until titles are matched. */
    var smartMovieGenres by mutableStateOf(listOf<Pair<Category, Int>>())
    var smartSeriesGenres by mutableStateOf(listOf<Pair<Category, Int>>())
    var smartMovieTotal by mutableIntStateOf(0)
    var smartSeriesTotal by mutableIntStateOf(0)

    /** A page of the catalogue organised by ArkaTV. [categoryId] is "g:<genre>" ("g:-1": not recognised) or null. */
    suspend fun smartPage(
        kind: VodKind,
        categoryId: String?,
        sort: VodSort,
        descending: Boolean?,
        offset: Int,
        filter: VodFilter = VodFilter.NONE,
    ): List<Vod> {
        val source = active ?: return emptyList()
        val catalogStore = store ?: return emptyList()
        val genre = categoryId?.removePrefix("g:")?.toIntOrNull()
        return withContext(Dispatchers.IO) {
            try {
                if (genre == UNMATCHED_GENRE) {
                    catalogStore.unmatchedPage(source, kind, GRID_PAGE, offset)
                } else {
                    catalogStore.smartPage(source, kind, genre, sort, descending, GRID_PAGE, offset, filter)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    const val UNMATCHED_GENRE = -1

    private fun genreList(genres: List<Pair<Int, String>>, counts: SmartCounts): List<Pair<Category, Int>> {
        val list = genres.mapNotNull { (id, name) ->
            val count = counts.byGenre[id] ?: 0
            if (count > 0) Category("g:$id", name) to count else null
        }
        return if (counts.unmatched > 0) list + (Category("g:$UNMATCHED_GENRE", "Non reconnus") to counts.unmatched) else list
    }

    /** Popular matched titles that have a wide picture, for the home hero. */
    var heroPicks by mutableStateOf(listOf<Vod>())
    private var tmdbJob: Job? = null

    fun updateTmdbToken(token: String) {
        val clean = token.trim()
        Store.tmdbToken = clean
        tmdbToken = clean
        tmdbStatus = ""
        tmdbJob?.cancel()
        if (clean.isEmpty()) {
            smartRows = emptyList()
            heroPicks = emptyList()
            smartMovieGenres = emptyList()
            smartSeriesGenres = emptyList()
        } else {
            startEnrichment()
        }
    }

    fun updateSmartHome(enabled: Boolean) {
        Store.smartHome = enabled
        smartHome = enabled
    }

    /**
     * Looks up, in the background and a few per second, the titles TMDB has not been asked about yet,
     * newest first, and rebuilds the home rows as matches come in. Stops when everything is known.
     */
    private fun startEnrichment() {
        val source = active ?: return
        val catalogStore = store ?: return
        val token = tmdbToken
        if (token.isBlank() || source.type != "xtream") return
        if (tmdbJob?.isActive == true) {
            scope.launch { refreshSmartRows(source, catalogStore) }
            return
        }
        tmdbJob = scope.launch {
            try {
                refreshSmartRows(source, catalogStore)
                // The same work often appears several times (4K, HD, languages): one request serves all.
                val known = java.util.concurrent.ConcurrentHashMap<String, Any>()
                val gate = Semaphore(TMDB_PARALLEL)
                var hiccups = 0
                var sinceRefresh = 0
                var working = true
                while (working && isActive) {
                    working = false
                    for (kind in listOf(VodKind.MOVIE, VodKind.SERIES)) {
                        val pending = withContext(Dispatchers.IO) { catalogStore.tmdbPending(source, kind, TMDB_BATCH) }
                        if (pending.isEmpty()) continue
                        working = true
                        tmdbStatus = "analyse en cours"
                        // Several titles are looked up at once. A title whose request fails is simply
                        // left for a later pass: a network hiccup never stops the whole analysis.
                        val failed = withContext(Dispatchers.IO) {
                            coroutineScope {
                                pending.map { title ->
                                    async {
                                        gate.withPermit {
                                            val clean = TitleCleaner.clean(title.name)
                                            val year = clean.year.ifBlank { title.year.take(4) }.takeIf { it.toIntOrNull() != null }.orEmpty()
                                            val key = kind.name + "|" + TextNormalizer.normalize(clean.title) + "|" + year
                                            try {
                                                val cached = known[key]
                                                val match = if (cached != null) {
                                                    cached as? Tmdb.Match
                                                } else {
                                                    val found = searchPatiently(token, kind == VodKind.MOVIE, clean.title, year)
                                                    known[key] = found ?: TMDB_NO_MATCH
                                                    found
                                                }
                                                catalogStore.saveTmdb(source, kind, title.streamId, match, System.currentTimeMillis())
                                                false
                                            } catch (e: Http.StatusException) {
                                                // A refused key stops everything. A request TMDB rejects for
                                                // good (a title it cannot parse) is recorded as not found, so
                                                // that it is never asked again. The rest is a hiccup.
                                                if (e.code == 401) throw e
                                                if (e.code in 400..499 && e.code != 429) {
                                                    catalogStore.saveTmdb(source, kind, title.streamId, null, System.currentTimeMillis())
                                                    false
                                                } else {
                                                    true
                                                }
                                            } catch (e: java.io.IOException) {
                                                true
                                            }
                                        }
                                    }
                                }.awaitAll().count { it }
                            }
                        }
                        if (failed == pending.size) {
                            // Nothing went through: the network is down or TMDB is refusing. Wait, then
                            // try again a limited number of times before leaving it for the next launch.
                            hiccups++
                            if (hiccups > TMDB_MAX_HICCUPS) throw java.io.IOException("réseau")
                            tmdbStatus = "réseau instable, nouvelle tentative"
                            delay(TMDB_HICCUP_PAUSE_MS)
                        } else {
                            hiccups = 0
                        }
                        sinceRefresh += pending.size
                        val counts = withContext(Dispatchers.IO) { catalogStore.tmdbCounts(source) }
                        tmdbMatched = counts.first
                        tmdbChecked = counts.second
                    }
                    if (sinceRefresh >= TMDB_REFRESH_EVERY || (smartRows.isEmpty() && sinceRefresh >= TMDB_FIRST_REFRESH)) {
                        sinceRefresh = 0
                        refreshSmartRows(source, catalogStore)
                    }
                }
                refreshSmartRows(source, catalogStore)
                tmdbStatus = "à jour"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Http.StatusException) {
                tmdbStatus = if (e.code == 401) "clé refusée par TMDB" else "TMDB a répondu ${e.code}, reprise au prochain lancement"
            } catch (e: Exception) {
                tmdbStatus = "interrompu (réseau), reprise au prochain lancement"
            }
        }
    }

    private suspend fun refreshSmartRows(source: Source, catalogStore: CatalogStore) {
        try {
            val built = withContext(Dispatchers.IO) {
                val counts = catalogStore.tmdbCounts(source)
                val rows = ArrayList<SmartRow>()
                fun add(title: String, items: List<Vod>, kind: VodKind, genreId: Int?, sort: VodSort) {
                    if (items.size >= SMART_ROW_MIN) rows.add(SmartRow(title, items, kind, genreId?.let { "g:$it" }, sort))
                }
                val trendingMovies = catalogStore.smartRow(source, VodKind.MOVIE, null, false, SMART_ROW_SIZE)
                val trendingSeries = catalogStore.smartRow(source, VodKind.SERIES, null, false, SMART_ROW_SIZE)
                add("Films du moment", trendingMovies, VodKind.MOVIE, null, VodSort.POPULAR)
                add("Séries du moment", trendingSeries, VodKind.SERIES, null, VodSort.POPULAR)
                add("Films les mieux notés", catalogStore.smartRow(source, VodKind.MOVIE, null, true, SMART_ROW_SIZE), VodKind.MOVIE, null, VodSort.RATING)
                add("Séries les mieux notées", catalogStore.smartRow(source, VodKind.SERIES, null, true, SMART_ROW_SIZE), VodKind.SERIES, null, VodSort.RATING)
                for ((id, name) in Tmdb.movieGenres) {
                    add(name, catalogStore.smartRow(source, VodKind.MOVIE, id, false, SMART_ROW_SIZE), VodKind.MOVIE, id, VodSort.POPULAR)
                }
                for ((id, name) in Tmdb.seriesGenres) {
                    add(name, catalogStore.smartRow(source, VodKind.SERIES, id, false, SMART_ROW_SIZE), VodKind.SERIES, id, VodSort.POPULAR)
                }
                val movieCounts = catalogStore.smartCounts(source, VodKind.MOVIE, Tmdb.movieGenres.map { it.first })
                val seriesCounts = catalogStore.smartCounts(source, VodKind.SERIES, Tmdb.seriesGenres.map { it.first })
                val hero = ArrayList<Vod>()
                for (i in 0 until 4) {
                    trendingSeries.filter { it.backdrop.isNotBlank() }.getOrNull(i)?.let(hero::add)
                    trendingMovies.filter { it.backdrop.isNotBlank() }.getOrNull(i)?.let(hero::add)
                }
                SmartBuild(rows, hero.take(5), counts, movieCounts, seriesCounts)
            }
            if (active?.id == source.id && tmdbToken.isNotBlank()) {
                smartMovieGenres = genreList(Tmdb.movieGenres, built.movies)
                smartSeriesGenres = genreList(Tmdb.seriesGenreNames, built.series)
                smartMovieTotal = built.movies.total + built.movies.unmatched
                smartSeriesTotal = built.series.total + built.series.unmatched
                smartRows = built.first
                heroPicks = built.second
                tmdbMatched = built.third.first
                tmdbChecked = built.third.second
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The provider's own rows stay in use.
        }
    }

    /** Trailer of a matched title on YouTube, or null (no key, not matched, none listed, no network). */
    suspend fun trailerUrl(vod: Vod): String? {
        val source = active ?: return null
        val catalogStore = store ?: return null
        val token = tmdbToken
        if (token.isBlank() || source.type != "xtream") return null
        return withContext(Dispatchers.IO) {
            try {
                val id = catalogStore.tmdbId(source, vod.kind, vod.id)
                if (id > 0) Tmdb.trailer(token, vod.kind == VodKind.MOVIE, id) else null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    /** The versions of this title the source offers, when it has been matched; empty otherwise. */
    suspend fun variants(vod: Vod): List<Vod> {
        val source = active ?: return emptyList()
        val catalogStore = store ?: return emptyList()
        if (source.type != "xtream") return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                catalogStore.variants(source, vod.kind, vod.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private class SmartBuild(
        val first: List<SmartRow>,
        val second: List<Vod>,
        val third: Pair<Int, Int>,
        val movies: SmartCounts,
        val series: SmartCounts,
    )

    /** TMDB sometimes answers "too many requests": wait a little and ask again, a few times. */
    private suspend fun searchPatiently(token: String, movie: Boolean, title: String, year: String): Tmdb.Match? {
        var attempt = 0
        while (true) {
            try {
                return Tmdb.search(token, movie, title, year)
            } catch (e: Http.StatusException) {
                attempt++
                if (e.code != 429 || attempt > 4) throw e
                delay(1200L * attempt)
            }
        }
    }

    private val TMDB_NO_MATCH = Any()
    private const val TMDB_BATCH = 120
    private const val TMDB_PARALLEL = 8
    private const val TMDB_MAX_HICCUPS = 12
    private const val TMDB_HICCUP_PAUSE_MS = 10_000L
    private const val TMDB_FIRST_REFRESH = 240
    private const val TMDB_REFRESH_EVERY = 2400
    private const val SMART_ROW_SIZE = 24
    private const val SMART_ROW_MIN = 6

    /** Categories hidden from child profiles, as "live:<id>", "movie:<id>" or "series:<id>". */
    var lockedCategories by mutableStateOf(setOf<String>())

    /** The catalogue summary as stored, before the parental filter of the active profile. */
    private var rawSummary: CatalogStore.Summary? = null

    private fun hiddenNow(): Set<String> = if (activeProfile.kids) lockedCategories else emptySet()

    private fun updateLock() {
        store?.locked = hiddenNow()
            .groupBy({ it.substringBefore(':') }, { it.substringAfter(':') })
            .mapValues { it.value.toSet() }
    }

    fun setCategoryLocked(kind: String, id: String, locked: Boolean) {
        val source = active ?: return
        val key = "$kind:$id"
        lockedCategories = if (locked) lockedCategories + key else lockedCategories - key
        Store.saveLockedCategories(source.id, lockedCategories)
        updateLock()
        if (activeProfile.kids) {
            apply(rawSummary)
            catalogVersion++
        }
    }

    fun setProfileKids(profileId: String, kids: Boolean) {
        profiles = profiles.map { if (it.id == profileId && !it.isMain) it.copy(kids = kids) else it }
        Store.saveProfiles(profiles)
        if (activeProfile.id == profileId) {
            activeProfile = profiles.first { it.id == profileId }
            updateLock()
            apply(rawSummary)
            catalogVersion++
        }
    }

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
        tmdbToken = Store.tmdbToken
        hideIncompatible = Store.hideIncompatible
        device = tv.arka.app.player.DeviceVideo.profile
        smartHome = Store.smartHome
        if (initialised) return
        initialised = true
        store = CatalogStore(ArkaDatabase.create(context.applicationContext))
        store?.avoidUhd = !tv.arka.app.player.DeviceVideo.capabilities.uhd
        updateVisibility()
        sources = Store.loadSources()
        profiles = Store.loadProfiles()
        activeProfile = profiles.firstOrNull { it.id == Store.activeProfileId } ?: profiles.first()
        if (activeProfile.id != Store.activeProfileId) Store.activeProfileId = activeProfile.id
        val wanted = Store.activeSourceId
        val first = sources.firstOrNull { it.id == wanted } ?: sources.firstOrNull()
        if (first != null) select(first)
    }

    fun select(source: Source) {
        active = source
        Store.activeSourceId = source.id
        loadUserData(source)
        account = Store.loadAccount(source.id)
        open(source, force = false)
    }

    /** Everything that belongs to the active profile for this source. The catalogue is shared. */
    private fun loadUserData(source: Source) {
        favoriteOrder = Store.loadFavoriteOrder(source.id)
        favorites = favoriteOrder.toSet()
        hiddenChannels = Store.loadHiddenChannels(source.id)
        // Lists saved by earlier versions still carry the provider's decorated names.
        myList = Store.loadMyList(source.id).map { it.copy(name = TitleCleaner.title(it.name)) }
        progress = Store.loadProgress(source.id).map { entry ->
            entry.copy(
                item = entry.item.copy(
                    title = TitleCleaner.title(entry.item.title),
                    subtitle = TitleCleaner.subtitle(entry.item.subtitle),
                )
            )
        }
        recentChannels = Store.loadRecentChannels(source.id)
        hiddenCategories = Store.loadHiddenCategories(source.id)
        channelGroups = Store.loadChannelGroups(source.id)
        watched = Store.loadWatched(source.id)
        followed = Store.loadFollowed(source.id).map { it.copy(name = TitleCleaner.title(it.name)) }
        lockedCategories = Store.loadLockedCategories(source.id)
        unplayable = Store.loadUnplayable(source.id)
        updateLock()
        updateVisibility()
    }

    fun switchProfile(profile: Profile) {
        activeProfile = profile
        Store.activeProfileId = profile.id
        profileChosen = true
        active?.let { loadUserData(it) }
        // A child profile sees the catalogue without the locked categories, and the others see all of it.
        apply(rawSummary)
        catalogVersion++
    }

    /** Returns the new profile, or null when the limit is reached or the name is empty. */
    fun addProfile(name: String, kids: Boolean = false): Profile? {
        val clean = name.trim()
        if (clean.isEmpty() || profiles.size >= Profile.MAX) return null
        val used = profiles.map { it.color }.toSet()
        val color = (0 until Profile.MAX).firstOrNull { it !in used } ?: 0
        val profile = Profile(java.util.UUID.randomUUID().toString().replace("-", "").take(8), clean, color, kids)
        profiles = profiles + profile
        Store.saveProfiles(profiles)
        return profile
    }

    fun renameProfile(profile: Profile, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val renamed = profile.copy(name = clean)
        profiles = profiles.map { if (it.id == profile.id) renamed else it }
        Store.saveProfiles(profiles)
        if (activeProfile.id == profile.id) activeProfile = renamed
    }

    /** The main profile cannot be deleted: it owns the data of earlier versions. */
    fun deleteProfile(profile: Profile) {
        if (profile.isMain) return
        profiles = profiles.filter { it.id != profile.id }
        Store.saveProfiles(profiles)
        if (activeProfile.id == profile.id) switchProfile(profiles.first())
        Store.deleteProfileData(profile.id)
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
        rawSummary = summary
        val hidden = hiddenNow()
        fun gone(kind: String, id: String) = hidden.isNotEmpty() && "$kind:$id" in hidden
        channels = summary?.channels.orEmpty().filter { !gone(CatalogStore.LIVE, it.categoryId) && !hiddenChannel(it) }
        // A live category left without any channel this device can show is not listed.
        val usedLive = if (hideIncompatible) channels.mapTo(HashSet()) { it.categoryId } else null
        liveCategories = summary?.liveCategories.orEmpty()
            .filter { !gone(CatalogStore.LIVE, it.id) && (usedLive == null || it.id in usedLive) }
        vodCategories = summary?.vodCategories.orEmpty().filter { !gone(CatalogStore.MOVIE, it.id) }
        seriesCategories = summary?.seriesCategories.orEmpty().filter { !gone(CatalogStore.SERIES, it.id) }
        val movieCounts = summary?.movieCategoryCounts.orEmpty().filterKeys { !gone(CatalogStore.MOVIE, it) }
        val seriesCounts = summary?.seriesCategoryCounts.orEmpty().filterKeys { !gone(CatalogStore.SERIES, it) }
        movieCount = if (hidden.isEmpty()) summary?.movieCount ?: 0 else movieCounts.values.sum()
        seriesCount = if (hidden.isEmpty()) summary?.seriesCount ?: 0 else seriesCounts.values.sum()
        latestMovies = summary?.latestMovies.orEmpty().filter { !gone(CatalogStore.MOVIE, it.categoryId) }
        latestSeries = summary?.latestSeries.orEmpty().filter { !gone(CatalogStore.SERIES, it.categoryId) }
        movieCategoryCounts = movieCounts
        seriesCategoryCounts = seriesCounts
        rowCache.clear()
        detailsCache.clear()
        if (summary != null) {
            startEnrichment()
        } else {
            tmdbJob?.cancel()
            smartRows = emptyList()
            heroPicks = emptyList()
            smartMovieGenres = emptyList()
            smartSeriesGenres = emptyList()
        }
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
            val refreshAfter = Store.catalogRefreshHours * 3600 * 1000L
            if (force || !hasCache || (refreshAfter > 0 && now - lastSync > refreshAfter)) {
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
        rawSummary = rawSummary?.let { raw ->
            CatalogStore.Summary(
                liveCategories = raw.liveCategories,
                channels = raw.channels.map { it.copy(url = api.liveUrl(it.id, format)) },
                vodCategories = raw.vodCategories,
                seriesCategories = raw.seriesCategories,
                movieCount = raw.movieCount,
                seriesCount = raw.seriesCount,
                movieCategoryCounts = raw.movieCategoryCounts,
                seriesCategoryCounts = raw.seriesCategoryCounts,
                latestMovies = raw.latestMovies,
                latestSeries = raw.latestSeries,
            )
        }
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
    suspend fun vodPage(
        kind: VodKind,
        categoryId: String?,
        sort: VodSort,
        descending: Boolean?,
        offset: Int,
        filter: VodFilter = VodFilter.NONE,
    ): List<Vod> = vodRange(kind, categoryId, sort, descending, GRID_PAGE, offset, filter)

    /** Ids of the films marked as watched, most recent last, for the "not watched" filter. */
    fun watchedMovieIds(): List<String> =
        watched.filter { it.startsWith("m:") }.map { it.removePrefix("m:") }.takeLast(VodFilter.MAX_EXCLUDED)

    private suspend fun vodRange(
        kind: VodKind,
        categoryId: String?,
        sort: VodSort,
        descending: Boolean?,
        limit: Int,
        offset: Int,
        filter: VodFilter = VodFilter.NONE,
    ): List<Vod> {
        val source = active ?: return emptyList()
        val catalogStore = store ?: return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                catalogStore.vodPage(source, kind, categoryId, sort, descending, limit, offset, filter)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /** Discovery: number of titles matching the chosen kind and categories. */
    suspend fun discoverCount(kind: VodKind, categories: Collection<String>): Int {
        val source = active ?: return 0
        val catalogStore = store ?: return 0
        return withContext(Dispatchers.IO) {
            try {
                catalogStore.countIn(source, kind, categories)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                0
            }
        }
    }

    /** Discovery: the title at [position] among the matches. */
    suspend fun discoverPick(kind: VodKind, categories: Collection<String>, position: Int): Vod? {
        val source = active ?: return null
        val catalogStore = store ?: return null
        return withContext(Dispatchers.IO) {
            try {
                catalogStore.pickIn(source, kind, categories, position)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    /** First items of a category for a home row; null until [categoryRow] has loaded it. */
    fun cachedRow(kind: VodKind, categoryId: String): List<Vod>? = rowCache["$kind:$categoryId"]

    suspend fun categoryRow(kind: VodKind, categoryId: String): List<Vod> {
        val version = catalogVersion
        val row = vodRange(kind, categoryId, VodSort.PROVIDER, null, ROW_SIZE, 0)
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
        favoriteOrder = if (channelId in favorites) favoriteOrder - channelId else favoriteOrder + channelId
        favorites = favoriteOrder.toSet()
        Store.saveFavorites(source.id, favoriteOrder)
    }

    /** Moves a favourite up (negative [delta]) or down in the favourites list. */
    fun moveFavorite(channelId: String, delta: Int) {
        val source = active ?: return
        val from = favoriteOrder.indexOf(channelId)
        val to = from + delta
        if (from < 0 || to < 0 || to > favoriteOrder.lastIndex) return
        val list = favoriteOrder.toMutableList()
        list.add(to, list.removeAt(from))
        favoriteOrder = list
        Store.saveFavorites(source.id, favoriteOrder)
    }

    fun setChannelHidden(channelId: String, hidden: Boolean) {
        val source = active ?: return
        hiddenChannels = if (hidden) hiddenChannels + channelId else hiddenChannels - channelId
        Store.saveHiddenChannels(source.id, hiddenChannels)
    }

    fun showAllChannels() {
        val source = active ?: return
        hiddenChannels = emptySet()
        Store.saveHiddenChannels(source.id, hiddenChannels)
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

    fun setWatched(key: String, value: Boolean) {
        val source = active ?: return
        watched = if (value) watched + key else watched - key
        Store.saveWatched(source.id, watched)
        // A title marked as watched leaves "Continuer à regarder".
        if (value && progress.any { it.item.key == key }) {
            progress = progress.filterNot { it.item.key == key }
            Store.saveProgress(source.id, progress)
        }
    }

    /** Empties "Continuer à regarder" for the active profile. Watched marks are kept. */
    fun clearProgress() {
        val source = active ?: return
        progress = emptyList()
        Store.saveProgress(source.id, progress)
    }

    fun isFollowed(vod: Vod): Boolean = followed.any { it.id == vod.id }

    fun toggleFollow(vod: Vod) {
        val source = active ?: return
        followed = if (isFollowed(vod)) followed.filterNot { it.id == vod.id } else listOf(vod) + followed
        Store.saveFollowed(source.id, followed)
    }

    /** Other titles of the same category, most recent first. */
    suspend fun similar(vod: Vod): List<Vod> =
        vodRange(vod.kind, vod.categoryId, VodSort.RECENT, null, ROW_SIZE + 1, 0).filterNot { it.id == vod.id }.take(ROW_SIZE)

    fun progressFor(key: String): Progress? = progress.firstOrNull { it.item.key == key }

    fun saveProgress(item: PlayItem, position: Long, duration: Long) {
        val source = active ?: return
        if (item.live || duration <= 0) return
        progress = ProgressRules.update(progress, item, position, duration, System.currentTimeMillis())
        Store.saveProgress(source.id, progress)
        // Reaching the end marks the title as watched.
        if (ProgressRules.isFinished(position, duration) && item.key !in watched) {
            watched = watched + item.key
            Store.saveWatched(source.id, watched)
        }
    }

    /**
     * The season an episode belongs to, as a play queue, with the place of that episode in it.
     * Used when an episode is resumed on its own, so that the player still offers the other episodes.
     * Entries saved before the series was recorded are matched by the exact name of the series.
     */
    suspend fun episodeQueue(item: PlayItem): Pair<List<PlayItem>, Int>? {
        if (!item.key.startsWith("e:")) return null
        val episodeId = item.key.removePrefix("e:")
        return try {
            // Old entries do not name their series: the best name matches are tried, and only a
            // series that really contains this episode is accepted.
            val candidates = if (item.seriesId.isNotBlank()) {
                listOf(Vod(item.seriesId, VodKind.SERIES, item.title, item.poster, "", ""))
            } else {
                val wanted = TextNormalizer.normalize(TitleCleaner.title(item.title))
                searchVods(VodKind.SERIES, wanted, 0)
                    .sortedBy { if (TextNormalizer.normalize(it.name) == wanted) 0 else 1 }
                    .take(3)
            }
            var found: Pair<List<PlayItem>, Int>? = null
            for (series in candidates) {
                for (episodes in details(series).seasons.values) {
                    val place = episodes.indexOfFirst { it.id == episodeId }
                    if (place >= 0) {
                        found = episodes.map { it.toPlayItem(series) } to place
                        break
                    }
                }
                if (found != null) break
            }
            found
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
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

    fun isFinished(position: Long, duration: Long): Boolean = duration > 0 && position > duration * FINISHED_RATIO

    fun update(current: List<Progress>, item: PlayItem, position: Long, duration: Long, now: Long): List<Progress> {
        val others = current.filterNot { it.item.key == item.key }
        val finished = isFinished(position, duration)
        return if (finished || position < MIN_POSITION_MS) {
            others
        } else {
            (listOf(Progress(item, position, duration, now)) + others).take(MAX_ENTRIES)
        }
    }
}
