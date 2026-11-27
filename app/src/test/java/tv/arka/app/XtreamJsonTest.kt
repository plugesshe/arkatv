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
    fun buildsStreamUrlsFromTheSource() {
        val api = XtreamApi(Source("s", "xtream", "Test", "host.test:8080/", "alice", "pw"))
        assertEquals("http://host.test:8080", api.base)
        assertEquals("http://host.test:8080/live/alice/pw/5.ts", api.liveUrl("5", "ts"))
        assertEquals("http://host.test:8080/movie/alice/pw/9.mkv", api.movieUrl("9", "mkv"))
        assertEquals("http://host.test:8080/series/alice/pw/4.mp4", api.episodeUrl("4", "mp4"))
    }
}
