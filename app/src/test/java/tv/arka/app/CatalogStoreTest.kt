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
import tv.arka.app.data.Vod
import tv.arka.app.data.VodKind
import tv.arka.app.data.db.ArkaDatabase
import tv.arka.app.data.db.CatalogStore
import tv.arka.app.data.security.SecretBox

@RunWith(RobolectricTestRunner::class)
class CatalogStoreTest {
    private lateinit var database: ArkaDatabase
    private lateinit var store: CatalogStore

    private val xtream = Source("x", "xtream", "Xtream", "http://host.test:8080", "alice", "s3cret")
    private val m3u = Source("m", "m3u", "Liste", "http://host.test/list.m3u")

    private fun movie(id: String, name: String) =
        Vod(id, VodKind.MOVIE, name, "", "c1", "http://host.test:8080/movie/alice/s3cret/$id.mkv", "7.5", 10, "", "2020")

    private val catalog = Catalog(
        liveCategories = listOf(Category("l1", "Généralistes")),
        channels = listOf(
            Channel("1", "One HD", "", "l1", "one.fr", "http://host.test:8080/live/alice/s3cret/1.ts", 1),
            Channel("2", "Héritage TV", "", "l1", "", "http://host.test:8080/live/alice/s3cret/2.ts", 2),
        ),
        vodCategories = listOf(Category("c1", "Drame")),
        movies = listOf(
            movie("10", "Les Héritiers du Vent"),
            movie("11", "Héritage"),
            movie("12", "Le Dernier Convoi"),
            movie("13", "L'Héritier"),
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

    private suspend fun search(text: String): List<String> =
        store.searchVods(xtream, VodKind.MOVIE, TextNormalizer.normalize(text), 20, 0).map { it.name }
}
