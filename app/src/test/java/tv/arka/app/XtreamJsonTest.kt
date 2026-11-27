package tv.arka.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tv.arka.app.data.Source
import tv.arka.app.data.XtreamApi
import tv.arka.app.data.XtreamJson
import java.io.StringReader

@RunWith(RobolectricTestRunner::class)
class XtreamJsonTest {

    @Test
    fun listsOnlyTheProgrammesKeptForReplay() {
        fun b64(text: String) = android.util.Base64.encodeToString(text.toByteArray(), android.util.Base64.NO_WRAP)
        val root = JSONObject(
            """{"epg_listings":[
              {"title":"${b64("Journal de 20 h")}","description":"${b64("L'actualité")}","start":"2026-11-26 20:00:00",
               "start_timestamp":"1000","stop_timestamp":"2800","has_archive":1},
              {"title":"${b64("Film du soir")}","description":"","start":"2026-11-26 20:30:00",
               "start_timestamp":2800,"stop_timestamp":8200,"has_archive":1},
              {"title":"${b64("Sans replay")}","description":"","start":"2026-11-26 19:00:00",
               "start_timestamp":"500","stop_timestamp":"1000","has_archive":0},
              {"title":"${b64("Pas encore diffusé")}","description":"","start":"2026-11-27 23:00:00",
               "start_timestamp":"99000","stop_timestamp":"99900","has_archive":1}
            ]}"""
        )
        val programs = XtreamJson.replayPrograms(root, now = 9000)
        assertEquals(listOf("Film du soir", "Journal de 20 h"), programs.map { it.title })
        assertEquals("2026-11-26:20-30", programs[0].startText)
        assertEquals("L'actualité", programs[1].description)
        val api = XtreamApi(Source("x", "xtream", "X", "http://host.test:8080", "alice", "s3cret"))
        assertEquals(
            "http://host.test:8080/timeshift/alice/s3cret/90/2026-11-26:20-30/42.ts",
            api.replayUrl("42", programs[0].startText, 90),
        )
        assertEquals(0, XtreamJson.replayPrograms(JSONObject("{}"), 9000).size)
    }

    @Test
    fun readsFlatObjectsWhateverTheValueTypes() {
        val json = """
            [
              {"stream_id": 12, "name": "One", "num": "3", "category_id": "7", "extra": null, "list": [1, 2], "nested": {"a": 1}},
              "garbage",
              {"stream_id": "13", "name": "Two", "is_adult": false}
            ]
        """.trimIndent()
        val rows = XtreamJson.readArray(StringReader(json)) { m -> HashMap(m) }
        assertEquals(2, rows.size)
        assertEquals("12", rows[0]["stream_id"])
        assertEquals("3", rows[0]["num"])
        assertEquals(null, rows[0]["extra"])
        assertEquals(null, rows[0]["list"])
        assertEquals("13", rows[1]["stream_id"])
    }

    @Test
    fun returnsNothingWhenTheServerDoesNotSendAnArray() {
        val rows = XtreamJson.readArray(StringReader("""{"user_info": {"auth": 0}}""")) { m -> HashMap(m) }
        assertEquals(0, rows.size)
    }

    @Test
    fun readsSeasonsSentAsAnObject() {
        val root = JSONObject(
            """
            {"info": {"plot": "Résumé", "releaseDate": "2021-05-01", "backdrop_path": ["http://img.test/b.jpg"]},
             "episodes": {
               "2": [{"id": "21", "episode_num": 1, "title": "S2E1", "container_extension": "mkv", "info": []}],
               "1": [{"id": "12", "episode_num": "2", "title": "S1E2", "info": {"plot": "p", "movie_image": "http://img.test/e.jpg"}},
                     {"id": "11", "episode_num": 1, "title": "S1E1", "container_extension": "mp4"}]
             }}
            """.trimIndent()
        )
        val details = XtreamJson.seriesDetails(root) { id, ext -> "url/$id.$ext" }
        assertEquals("Résumé", details.plot)
        assertEquals("2021", details.year)
        assertEquals("http://img.test/b.jpg", details.backdrop)
        assertEquals(listOf(1, 2), details.seasons.keys.toList())
        assertEquals(listOf("S1E1", "S1E2"), details.seasons.getValue(1).map { it.title })
        assertEquals("url/21.mkv", details.seasons.getValue(2)[0].url)
        assertEquals("url/12.mp4", details.seasons.getValue(1)[1].url)
        assertEquals("http://img.test/e.jpg", details.seasons.getValue(1)[1].image)
    }

    @Test
    fun readsSeasonsSentAsNestedArrays() {
        val root = JSONObject(
            """{"episodes": [[{"id": "1", "episode_num": 1, "title": "A"}], [{"id": "2", "episode_num": 1, "title": "B", "season": 5}]]}"""
        )
        val details = XtreamJson.seriesDetails(root) { id, ext -> "$id.$ext" }
        assertEquals(listOf(1, 5), details.seasons.keys.toList())
    }

    @Test
    fun readsAccountDetailsWhateverTheirTypes() {
        val full = JSONObject("""{"auth": 1, "status": "Active", "exp_date": "1798761600", "max_connections": "2", "active_cons": 1}""")
        val account = XtreamJson.accountInfo(full, 42)
        assertEquals("Active", account.status)
        assertEquals(1798761600L, account.expiresAt)
        assertEquals(2, account.maxConnections)
        assertEquals(1, account.activeConnections)
        assertEquals(42L, account.checkedAt)

        val unlimited = XtreamJson.accountInfo(JSONObject("""{"auth": 1, "status": "Active", "exp_date": null}"""), 0)
        assertEquals(0L, unlimited.expiresAt)
        assertEquals(0, unlimited.maxConnections)
    }

    @Test
    fun readsEpisodeDurationsInEitherForm() {
        val root = JSONObject(
            """{"episodes": {"1": [
                {"id": "1", "episode_num": 1, "info": {"duration_secs": 2890}},
                {"id": "2", "episode_num": 2, "info": {"duration": "00:51:20"}},
                {"id": "3", "episode_num": 3, "info": {}}
            ]}}"""
        )
        val episodes = XtreamJson.seriesDetails(root) { id, ext -> "$id.$ext" }.seasons.getValue(1)
        assertEquals(listOf(48, 51, 0), episodes.map { it.durationMin })
    }

    @Test
    fun buildsStreamUrlsFromTheSource() {
        val api = XtreamApi(Source("s", "xtream", "Test", "host.test:8080/", "alice", "pw"))
        assertEquals("http://host.test:8080", api.base)
        assertEquals("http://host.test:8080/live/alice/pw/5.ts", api.liveUrl("5", "ts"))
        assertEquals("http://host.test:8080/movie/alice/pw/9.mkv", api.movieUrl("9", "mkv"))
        assertEquals("http://host.test:8080/series/alice/pw/4.mp4", api.episodeUrl("4", "mp4"))
    }
}
