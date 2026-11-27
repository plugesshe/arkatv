package tv.arka.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.arka.app.data.M3u
import java.io.IOException
import java.io.StringReader

class M3uTest {
    private val playlist = """
        #EXTM3U url-tvg="http://guide.test/epg.xml.gz"
        #EXTINF:-1 tvg-id="one.fr" tvg-name="One" tvg-logo="http://img.test/one.png" group-title="Généralistes",One HD
        http://host.test/live/u/p/1.ts
        #EXTINF:-1 tvg-id="" group-title="Infos",News, the 24/7 channel
        http://host.test/live/u/p/2.m3u8

        #EXTVLCOPT:http-user-agent=Test
        #EXTINF:-1 tvg-logo="http://img.test/film.jpg" group-title="Films",Un film (2020)
        http://host.test/movie/u/p/30.mkv
        #EXTINF:-1,Sans groupe
        http://host.test/stream/40
    """.trimIndent()

    @Test
    fun readsChannelsWithTheirAttributes() {
        val result = M3u.parse(StringReader(playlist))
        assertEquals(3, result.channels.size)
        val one = result.channels[0]
        assertEquals("One HD", one.name)
        assertEquals("one.fr", one.epgId)
        assertEquals("http://img.test/one.png", one.logo)
        assertEquals("Généralistes", one.categoryId)
        assertEquals("http://host.test/live/u/p/1.ts", one.url)
        assertEquals(1, one.number)
    }

    @Test
    fun keepsCommasInsideChannelNames() {
        val result = M3u.parse(StringReader(playlist))
        assertEquals("News, the 24/7 channel", result.channels[1].name)
    }

    @Test
    fun separatesMoviesFromLiveChannels() {
        val result = M3u.parse(StringReader(playlist))
        assertEquals(1, result.movies.size)
        assertEquals("Un film (2020)", result.movies[0].name)
        assertEquals(listOf("Films"), result.vodCategories.map { it.name })
        assertEquals(listOf("Généralistes", "Infos", "Autres"), result.liveCategories.map { it.name })
    }

    @Test
    fun findsTheGuideUrlInTheHeader() {
        assertEquals("http://guide.test/epg.xml.gz", M3u.parse(StringReader(playlist)).epgUrl)
    }

    @Test
    fun rejectsAPlaylistWithoutAnyEntry() {
        val failed = try {
            M3u.parse(StringReader("<html>not a playlist</html>"))
            false
        } catch (e: IOException) {
            true
        }
        assertTrue(failed)
    }
}
