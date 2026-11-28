package tv.arka.app

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import tv.arka.app.data.Catalog
import tv.arka.app.data.Category
import tv.arka.app.data.Channel
import tv.arka.app.data.Program
import tv.arka.app.data.Source
import tv.arka.app.data.TextNormalizer
import tv.arka.app.data.Tmdb
import tv.arka.app.data.Vod
import tv.arka.app.data.VodFilter
import tv.arka.app.data.VodKind
import tv.arka.app.data.VodSort
import tv.arka.app.data.db.ArkaDatabase
import tv.arka.app.data.db.CatalogStore
import tv.arka.app.data.security.SecretBox
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class CatalogStoreTest {
    private lateinit var database: ArkaDatabase
    private lateinit var store: CatalogStore

    private val xtream = Source("x", "xtream", "Xtream", "http://host.test:8080", "alice", "s3cret")
    private val m3u = Source("m", "m3u", "Liste", "http://host.test/list.m3u")

    private fun movie(
        id: String,
        name: String,
        category: String = "c1",
        added: Long = 10,
        year: String = "2020",
        rating: String = "7.5",
    ) = Vod(id, VodKind.MOVIE, name, "", category, "http://host.test:8080/movie/alice/s3cret/$id.mkv", rating, added, "", year)

    private val catalog = Catalog(
        liveCategories = listOf(Category("l1", "Généralistes")),
        channels = listOf(
            Channel("1", "One HD", "", "l1", "one.fr", "http://host.test:8080/live/alice/s3cret/1.ts", 1),
            Channel("2", "Héritage TV", "", "l1", "", "http://host.test:8080/live/alice/s3cret/2.ts", 2),
        ),
        vodCategories = listOf(Category("c1", "Drame"), Category("c2", "Action"), Category("c3", "Vide")),
        movies = listOf(
            movie("10", "Les Héritiers du Vent", added = 40, year = "2021", rating = "8.1"),
            movie("11", "Héritage", added = 10, year = "2019", rating = "6.0"),
            movie("12", "Le Dernier Convoi", added = 30, year = "2023", rating = "9.2"),
            movie("13", "L'Héritier", category = "c2", added = 20, year = "", rating = ""),
        ),
        seriesCategories = listOf(Category("s1", "Séries")),
        series = listOf(Vod("20", VodKind.SERIES, "Ombres de Verre", "", "s1", "", "", 5, "Résumé", "2022")),
    )

    @Before
    fun setUp() {
        SecretBox.useKeyForTests(ByteArray(32) { it.toByte() })
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, ArkaDatabase::class.java).allowMainThreadQueries().build()
        store = CatalogStore(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun returnsNothingBeforeAnySync() = runBlocking {
        assertNull(store.load(xtream, "ts"))
    }

    @Test
    fun restoresAnXtreamCatalogueAndRebuildsItsUrls() = runBlocking {
        store.save(xtream, catalog)
        val loaded = store.load(xtream, "ts")!!
        assertEquals(catalog, loaded)
        // The live format is applied when reading, without any network access.
        assertEquals("http://host.test:8080/live/alice/s3cret/1.m3u8", store.load(xtream, "m3u8")!!.channels[0].url)
    }

    @Test
    fun neverStoresXtreamCredentials() = runBlocking {
        store.save(xtream, catalog)
        val dao = database.catalog()
        val stored = (dao.channels("x") + dao.vods("x", "movie") + dao.vods("x", "series")).joinToString("|")
        assertFalse(stored.contains("s3cret"))
        assertFalse(stored.contains("alice"))
    }

    @Test
    fun storesM3uUrlsEncryptedAndRestoresThem() = runBlocking {
        val playlist = Catalog(
            liveCategories = listOf(Category("Infos", "Infos")),
            channels = listOf(
                Channel("http://host.test/live/bob/hunter2/1.ts", "News", "", "Infos", "", "http://host.test/live/bob/hunter2/1.ts", 1)
            ),
            vodCategories = listOf(Category("Films", "Films")),
            movies = listOf(
                Vod("http://host.test/movie/bob/hunter2/3.mp4", VodKind.MOVIE, "Film", "", "Films", "http://host.test/movie/bob/hunter2/3.mp4", "", 1)
            ),
        )
        store.save(m3u, playlist)
        val dao = database.catalog()
        assertFalse((dao.channels("m") + dao.vods("m", "movie")).joinToString("|").contains("hunter2"))
        assertEquals(playlist, store.load(m3u, "ts"))
    }

    @Test
    fun aNewSyncReplacesThePreviousCatalogueOfThatSourceOnly() = runBlocking {
        store.save(xtream, catalog)
        store.save(m3u, Catalog(channels = listOf(Channel("u", "Autre", "", "", "", "u", 1))))
        store.save(xtream, catalog.copy(movies = listOf(movie("99", "Nouveau"))))
        assertEquals(listOf("Nouveau"), store.load(xtream, "ts")!!.movies.map { it.name })
        assertEquals(1, store.load(m3u, "ts")!!.channels.size)
    }

    @Test
    fun searchIgnoresAccentsAndCase() = runBlocking {
        store.save(xtream, catalog)
        val found = search("HERITIERS")
        assertEquals(listOf("Les Héritiers du Vent"), found)
    }

    @Test
    fun searchRanksExactThenPrefixThenWordStart() = runBlocking {
        store.save(xtream, catalog)
        assertEquals(listOf("Héritage", "L'Héritier", "Les Héritiers du Vent"), search("herit"))
        assertEquals("Héritage", search("héritage").first())
    }

    @Test
    fun searchRequiresEveryTerm() = runBlocking {
        store.save(xtream, catalog)
        assertEquals(listOf("Les Héritiers du Vent"), search("vent heritiers"))
        assertTrue(search("vent convoi").isEmpty())
    }

    @Test
    fun searchIsPaginatedAndScopedByKind() = runBlocking {
        store.save(xtream, catalog)
        val query = TextNormalizer.normalize("herit")
        val first = store.searchVods(xtream, VodKind.MOVIE, query, 2, 0)
        val second = store.searchVods(xtream, VodKind.MOVIE, query, 2, 2)
        assertEquals(2, first.size)
        assertEquals(listOf("Les Héritiers du Vent"), second.map { it.name })
        assertTrue(store.searchVods(xtream, VodKind.SERIES, query, 10, 0).isEmpty())
        assertEquals(listOf("Héritage TV"), store.searchChannels(xtream, "ts", query, 10, 0).map { it.name })
        assertEquals("http://host.test:8080/movie/alice/s3cret/11.mkv", first[0].url)
    }

    @Test
    fun keepsTheGuideAndDropsWhatIsOver() = runBlocking {
        val guide = mapOf(
            "one.fr" to listOf(Program(100, 200, "Passé", ""), Program(200, 300, "En cours", "Description")),
        )
        store.saveEpg("x", guide)
        assertEquals(guide, store.loadEpg("x", 0))
        assertEquals(listOf("En cours"), store.loadEpg("x", 250).getValue("one.fr").map { it.title })
        store.delete("x")
        assertTrue(store.loadEpg("x", 0).isEmpty())
    }

    @Test
    fun summaryKeepsOnlyWhatTheInterfaceHoldsInMemory() = runBlocking {
        assertNull(store.loadSummary(xtream, "ts", 20))
        store.save(xtream, catalog)
        val summary = store.loadSummary(xtream, "ts", 2)!!
        assertEquals(4, summary.movieCount)
        assertEquals(1, summary.seriesCount)
        assertEquals(2, summary.channels.size)
        // The empty category is not offered.
        assertEquals(listOf("Drame", "Action"), summary.vodCategories.map { it.name })
        assertEquals(listOf("Séries"), summary.seriesCategories.map { it.name })
        assertEquals(listOf("Les Héritiers du Vent", "Le Dernier Convoi"), summary.latestMovies.map { it.name })
        assertEquals(listOf("Ombres de Verre"), summary.latestSeries.map { it.name })
    }

    @Test
    fun versionsCanBeFilteredAndTheBestOneRepresentsAWork() = runBlocking {
        val versions = Catalog(
            vodCategories = listOf(Category("c1", "Tout")),
            movies = listOf(
                movie("1", "VOSTFR - Dune FHD"),
                movie("2", "FR - Dune (2021)"),
                movie("3", "4K-D+ - Dune (2021)"),
                movie("4", "Arrival"),
            ),
        )
        store.save(xtream, versions)
        suspend fun ids(filter: VodFilter) =
            store.vodPage(xtream, VodKind.MOVIE, null, VodSort.PROVIDER, null, 10, 0, filter).map { it.id }
        assertEquals(listOf("2"), ids(VodFilter(language = "FR")))
        assertEquals(listOf("1"), ids(VodFilter(language = "VOSTFR")))
        assertEquals(listOf("3"), ids(VodFilter(quality = "4K")))
        assertEquals(listOf("1"), ids(VodFilter(quality = "FHD")))
        assertEquals(4, ids(VodFilter.NONE).size)

        val dune = Tmdb.Match(9, "Dune", "2021", listOf(878), 80.0, 7.8, 9000, "", "", "")
        for (id in listOf("1", "2", "3")) store.saveTmdb(xtream, VodKind.MOVIE, id, dune, 1)
        // French first, then the best picture.
        assertEquals(listOf("2"), store.smartPage(xtream, VodKind.MOVIE, null, VodSort.POPULAR, null, 10, 0).map { it.id })
        assertEquals(listOf("2"), store.smartRow(xtream, VodKind.MOVIE, null, false, 10).map { it.id })
        assertEquals(
            listOf("3"),
            store.smartPage(xtream, VodKind.MOVIE, null, VodSort.POPULAR, null, 10, 0, VodFilter(quality = "4K")).map { it.id },
        )
    }

    @Test
    fun aLimitedDeviceOnlySeesWhatItCanPlay() = runBlocking {
        val mixed = Catalog(
            vodCategories = listOf(Category("uhd", "Films 4K"), Category("all", "Films")),
            movies = listOf(
                movie("1", "4K-D+ - Dune (2021)", "uhd"),
                movie("2", "FR - Dune (2021) FHD", "all"),
                movie("3", "4K - Oppenheimer (2023) DV", "uhd"),
                movie("4", "Arrival", "all"),
                movie("5", "FR - Tenet (2020)", "all"),
            ),
        )
        store.save(xtream, mixed)
        val dune = Tmdb.Match(9, "Dune", "2021", listOf(878), 80.0, 7.8, 9000, "", "", "")
        val oppenheimer = Tmdb.Match(10, "Oppenheimer", "2023", listOf(18), 95.0, 8.1, 9000, "", "", "")
        store.saveTmdb(xtream, VodKind.MOVIE, "1", dune, 1)
        store.saveTmdb(xtream, VodKind.MOVIE, "2", dune, 1)
        store.saveTmdb(xtream, VodKind.MOVIE, "3", oppenheimer, 1)

        suspend fun provider() = store.vodPage(xtream, VodKind.MOVIE, null, VodSort.PROVIDER, null, 10, 0).map { it.id }
        suspend fun merged() = store.smartPage(xtream, VodKind.MOVIE, null, VodSort.POPULAR, null, 10, 0).map { it.id }

        // A device that decodes everything keeps the whole catalogue, 4K first for a merged work.
        assertEquals(listOf("1", "2", "3", "4", "5"), provider())
        assertEquals(listOf("3", "2"), merged())

        // A device limited to Full HD: 4K entries go, the Full HD version stands for the work, and a
        // work that only exists in 4K disappears, as does the category left empty.
        store.hiddenPatterns = listOf("%4K%", "%UHD%", "%2160%")
        assertEquals(listOf("2", "4", "5"), provider())
        assertEquals(listOf("2"), merged())
        assertEquals(listOf("2"), store.variants(xtream, VodKind.MOVIE, "2").map { it.id })
        val summary = store.loadSummary(xtream, "ts", 10)!!
        assertEquals(3, summary.movieCount)
        assertEquals(listOf("all"), summary.vodCategories.map { it.id })
        assertEquals(1, store.smartCounts(xtream, VodKind.MOVIE, listOf(878, 18)).total)

        // A title that failed to decode on this device leaves its lists too.
        store.hiddenIds = mapOf("movie" to setOf("5"))
        assertEquals(listOf("2", "4"), provider())
        assertEquals(2, store.loadSummary(xtream, "ts", 10)!!.movieCount)

        store.hiddenPatterns = emptyList()
        store.hiddenIds = emptyMap()
        assertEquals(5, provider().size)
    }

    @Test
    fun arkaOrganisationMergesVersionsAndKeepsUnmatchedTitles() = runBlocking {
        store.save(xtream, catalog)
        fun match(id: Int, title: String, popularity: Double, genres: List<Int>) =
            Tmdb.Match(id, title, "2021", genres, popularity, 8.0, 500, "", "", "")
        // Two versions of the same work, another work, and a title TMDB does not know.
        store.saveTmdb(xtream, VodKind.MOVIE, "10", match(1, "Vent", 50.0, listOf(18)), 1)
        store.saveTmdb(xtream, VodKind.MOVIE, "12", match(1, "Vent", 50.0, listOf(18)), 1)
        store.saveTmdb(xtream, VodKind.MOVIE, "11", match(2, "Héritage", 90.0, listOf(28)), 1)
        store.saveTmdb(xtream, VodKind.MOVIE, "13", null, 1)

        suspend fun names(genre: Int?, sort: VodSort) =
            store.smartPage(xtream, VodKind.MOVIE, genre, sort, null, 10, 0).map { it.name }
        assertEquals(listOf("Héritage", "Vent"), names(null, VodSort.POPULAR))
        assertEquals(listOf("Héritage", "Vent"), names(null, VodSort.NAME))
        assertEquals(listOf("Vent", "Héritage"), names(null, VodSort.RECENT))
        assertEquals(listOf("Vent"), names(18, VodSort.POPULAR))
        assertEquals(listOf("Héritage", "Vent"), store.smartRow(xtream, VodKind.MOVIE, null, false, 10).map { it.name })
        assertEquals(listOf("L'Héritier"), store.unmatchedPage(xtream, VodKind.MOVIE, 10, 0).map { it.name })

        val counts = store.smartCounts(xtream, VodKind.MOVIE, listOf(18, 28, 35))
        assertEquals(2, counts.total)
        assertEquals(1, counts.unmatched)
        assertEquals(mapOf(18 to 1, 28 to 1, 35 to 0), counts.byGenre)
        assertEquals(2, store.variants(xtream, VodKind.MOVIE, "10").size)
        assertTrue(store.variants(xtream, VodKind.MOVIE, "13").isEmpty())
        assertEquals(3 to 4, store.tmdbCounts(xtream))

        assertTrue(store.tmdbPending(xtream, VodKind.MOVIE, 10).isEmpty())
        assertEquals(listOf("20"), store.tmdbPending(xtream, VodKind.SERIES, 10).map { it.streamId })

        // A child profile never sees a work whose only versions are in a locked category.
        store.locked = mapOf("movie" to setOf("c1"))
        assertTrue(names(null, VodSort.POPULAR).isEmpty())
        assertEquals(listOf("L'Héritier"), store.unmatchedPage(xtream, VodKind.MOVIE, 10, 0).map { it.name })
        store.locked = emptyMap()
    }

    @Test
    fun pagesCanBeNarrowedByRatingYearsAndWatchedTitles() = runBlocking {
        store.save(xtream, catalog)
        suspend fun names(filter: VodFilter) =
            store.vodPage(xtream, VodKind.MOVIE, null, VodSort.PROVIDER, null, 10, 0, filter).map { it.name }
        assertEquals(listOf("Les Héritiers du Vent", "Le Dernier Convoi"), names(VodFilter(minRating = 7.0)))
        assertEquals(listOf("Les Héritiers du Vent", "Le Dernier Convoi"), names(VodFilter(yearFrom = 2020)))
        assertEquals(listOf("Héritage"), names(VodFilter(yearFrom = 2010, yearTo = 2019)))
        // A title without a year never matches a range, even an open one.
        assertEquals(listOf("Héritage"), names(VodFilter(yearFrom = 1, yearTo = 2019)))
        assertEquals(
            listOf("Héritage", "Le Dernier Convoi", "L'Héritier"),
            names(VodFilter(excludeIds = listOf("10"))),
        )
        assertEquals(listOf("Le Dernier Convoi"), names(VodFilter(minRating = 7.0, excludeIds = listOf("10"))))
        assertEquals(4, names(VodFilter.NONE).size)
    }

    @Test
    fun pagesCanBeSortedInEveryOfferedOrder() = runBlocking {
        store.save(xtream, catalog)
        suspend fun names(sort: VodSort) = store.vodPage(xtream, VodKind.MOVIE, null, sort, null, 10, 0).map { it.name }
        assertEquals(listOf("Les Héritiers du Vent", "Héritage", "Le Dernier Convoi", "L'Héritier"), names(VodSort.PROVIDER))
        assertEquals(listOf("Héritage", "L'Héritier", "Le Dernier Convoi", "Les Héritiers du Vent"), names(VodSort.NAME))
        assertEquals(listOf("Les Héritiers du Vent", "Le Dernier Convoi", "L'Héritier", "Héritage"), names(VodSort.RECENT))
        assertEquals(listOf("Le Dernier Convoi", "Les Héritiers du Vent", "Héritage", "L'Héritier"), names(VodSort.YEAR))
        assertEquals(listOf("Le Dernier Convoi", "Les Héritiers du Vent", "Héritage", "L'Héritier"), names(VodSort.RATING))
    }

    @Test
    fun everyOrderCanBeReversed() = runBlocking {
        store.save(xtream, catalog)
        suspend fun names(sort: VodSort, descending: Boolean) =
            store.vodPage(xtream, VodKind.MOVIE, null, sort, descending, 10, 0).map { it.name }
        assertEquals(listOf("Les Héritiers du Vent", "Le Dernier Convoi", "L'Héritier", "Héritage"), names(VodSort.NAME, true))
        assertEquals(listOf("Héritage", "L'Héritier", "Le Dernier Convoi", "Les Héritiers du Vent"), names(VodSort.RECENT, false))
        assertEquals("Le Dernier Convoi", names(VodSort.RATING, true).first())
        assertEquals("Le Dernier Convoi", names(VodSort.RATING, false).last())
    }

    @Test
    fun countsTitlesPerCategory() = runBlocking {
        store.save(xtream, catalog)
        val summary = store.loadSummary(xtream, "ts", 20)!!
        assertEquals(mapOf("c1" to 3, "c2" to 1), summary.movieCategoryCounts)
        assertEquals(mapOf("s1" to 1), summary.seriesCategoryCounts)
    }

    @Test
    fun discoveryCountsAndPicksAmongTheChosenCategories() = runBlocking {
        store.save(xtream, catalog)
        assertEquals(4, store.countIn(xtream, VodKind.MOVIE, emptyList()))
        assertEquals(3, store.countIn(xtream, VodKind.MOVIE, listOf("c1")))
        assertEquals(4, store.countIn(xtream, VodKind.MOVIE, listOf("c1", "c2")))
        assertEquals(0, store.countIn(xtream, VodKind.MOVIE, listOf("c3")))
        assertEquals("L'Héritier", store.pickIn(xtream, VodKind.MOVIE, listOf("c2"), 0)!!.name)
        assertEquals("Le Dernier Convoi", store.pickIn(xtream, VodKind.MOVIE, listOf("c1"), 2)!!.name)
        assertNull(store.pickIn(xtream, VodKind.MOVIE, listOf("c1"), 3))
        assertEquals("Ombres de Verre", store.pickIn(xtream, VodKind.SERIES, emptyList(), 0)!!.name)
    }

    @Test
    fun pagesAreLimitedToACategoryAndPaginated() = runBlocking {
        store.save(xtream, catalog)
        assertEquals(listOf("L'Héritier"), store.vodPage(xtream, VodKind.MOVIE, "c2", VodSort.PROVIDER, null, 10, 0).map { it.name })
        assertTrue(store.vodPage(xtream, VodKind.MOVIE, "c3", VodSort.PROVIDER, null, 10, 0).isEmpty())
        val first = store.vodPage(xtream, VodKind.MOVIE, null, VodSort.PROVIDER, null, 3, 0)
        val second = store.vodPage(xtream, VodKind.MOVIE, null, VodSort.PROVIDER, null, 3, 3)
        assertEquals(3, first.size)
        assertEquals(listOf("L'Héritier"), second.map { it.name })
        assertEquals("http://host.test:8080/movie/alice/s3cret/13.mkv", second[0].url)
    }

    @Test
    fun aFailedRefreshLeavesThePreviousCatalogueUntouched() = runBlocking {
        store.save(xtream, catalog)
        val failed = try {
            store.replace(xtream) { writer ->
                writer.channel(Channel("9", "Partiel", "", "l1", "", "u", 1))
                throw IOException("coupure réseau")
            }
            false
        } catch (e: IOException) {
            true
        }
        assertTrue(failed)
        assertEquals(catalog, store.load(xtream, "ts"))
    }

    @Test
    fun writesLargeCataloguesInBatchesKeepingTheirOrder() = runBlocking {
        store.replace(xtream) { writer ->
            repeat(1234) { i -> writer.vod(movie("m$i", "Film $i")) }
            repeat(3) { i -> writer.channel(Channel("c$i", "Chaîne $i", "", "l1", "", "u", i + 1)) }
        }
        assertEquals(1234, store.count(xtream, VodKind.MOVIE))
        val page = store.vodPage(xtream, VodKind.MOVIE, null, VodSort.PROVIDER, null, 3, 1230)
        assertEquals(listOf("Film 1230", "Film 1231", "Film 1232"), page.map { it.name })
        assertEquals(3, store.load(xtream, "ts")!!.channels.size)
    }

    private suspend fun search(text: String): List<String> =
        store.searchVods(xtream, VodKind.MOVIE, TextNormalizer.normalize(text), 20, 0).map { it.name }
}
