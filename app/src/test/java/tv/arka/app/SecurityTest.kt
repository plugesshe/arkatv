package tv.arka.app

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import tv.arka.app.data.Source
import tv.arka.app.data.Store
import tv.arka.app.data.security.SecretBox

@RunWith(RobolectricTestRunner::class)
class SecurityTest {

    @Before
    fun setUp() {
        SecretBox.useKeyForTests(ByteArray(32) { it.toByte() })
        Store.resetForTests()
    }

    @Test
    fun encryptsAndDecrypts() {
        val stored = SecretBox.encrypt("http://host.test/live/alice/s3cret/1.ts")
        assertFalse(stored.contains("s3cret"))
        assertEquals("http://host.test/live/alice/s3cret/1.ts", SecretBox.decrypt(stored))
    }

    @Test
    fun neverProducesTheSameCiphertextTwice() {
        assertNotEquals(SecretBox.encrypt("same"), SecretBox.encrypt("same"))
    }

    @Test
    fun rejectsTamperedOrForeignValues() {
        val stored = SecretBox.encrypt("value")
        val flipped = stored.dropLast(2) + (if (stored.endsWith("AA")) "BB" else "AA")
        assertNull(SecretBox.decrypt(flipped))
        assertNull(SecretBox.decrypt("plain text"))
        SecretBox.useKeyForTests(ByteArray(32) { 1 })
        assertNull(SecretBox.decrypt(stored))
    }

    @Test
    fun migratesClearTextDataFromVersion010WithoutLosingIt() {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("arkatv", Context.MODE_PRIVATE)
        prefs.edit()
            .putString(
                "sources",
                """[{"id":"s1","type":"xtream","name":"Maison","url":"http://host.test:8080","user":"alice","pass":"s3cret","epg":""}]""",
            )
            .putString(
                "progress_s1",
                """[{"key":"m:1","title":"Film","subtitle":"","url":"http://host.test:8080/movie/alice/s3cret/1.mp4","poster":"","pos":60000,"dur":600000,"updated":1}]""",
            )
            .putString("list_s1", """[{"id":"9","kind":"SERIES","name":"Série","poster":"","cat":"1","url":"","rating":"","year":"","plot":""}]""")
            .putStringSet("fav_s1", setOf("12", "34"))
            .putString("active", "s1")
            .commit()

        Store.init(context)

        val sources = Store.loadSources()
        assertEquals(1, sources.size)
        assertEquals("alice", sources[0].user)
        assertEquals("s3cret", sources[0].pass)
        assertEquals(setOf("12", "34"), Store.loadFavorites("s1"))
        assertEquals(60000L, Store.loadProgress("s1").single().position)
        assertEquals("Série", Store.loadMyList("s1").single().name)
        assertEquals("s1", Store.activeSourceId)

        assertFalse(prefs.contains("sources"))
        assertFalse(prefs.contains("progress_s1"))
        assertFalse(prefs.contains("fav_s1"))
        val everything = prefs.all.values.joinToString("|") { it.toString() }
        assertFalse(everything.contains("s3cret"))
        assertFalse(everything.contains("alice"))
    }

    @Test
    fun storesNewSourcesEncrypted() {
        val context: Context = RuntimeEnvironment.getApplication()
        Store.init(context)
        Store.saveSources(listOf(Source("s2", "m3u", "Liste", "http://host.test/get.php?username=bob&password=hunter2")))
        Store.saveFavorites("s2", setOf("http://host.test/live/bob/hunter2/1.ts"))
        val prefs = context.getSharedPreferences("arkatv", Context.MODE_PRIVATE)
        val everything = prefs.all.values.joinToString("|") { it.toString() }
        assertFalse(everything.contains("hunter2"))
        assertTrue(Store.loadSources().single().url.contains("hunter2"))
        assertEquals(1, Store.loadFavorites("s2").size)
    }
}
