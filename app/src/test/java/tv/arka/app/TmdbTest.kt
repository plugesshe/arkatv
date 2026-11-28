package tv.arka.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tv.arka.app.data.PairingServer
import tv.arka.app.data.TitleCleaner
import tv.arka.app.data.Tmdb

@RunWith(RobolectricTestRunner::class)
class TmdbTest {
    private val series = """
        {"page":1,"results":[
          {"id":900,"name":"Teen Wolf : The Movie","original_name":"Teen Wolf: The Movie","first_air_date":"2023-01-26",
           "genre_ids":[18],"popularity":10.0,"vote_average":7.0,"vote_count":50,"poster_path":"/x.jpg","backdrop_path":null,"overview":""},
          {"id":34524,"name":"Teen Wolf","original_name":"Teen Wolf","first_air_date":"2011-06-05",
           "genre_ids":[10759,18,10765],"popularity":120.5,"vote_average":8.6,"vote_count":4000,
           "poster_path":"/p.jpg","backdrop_path":"/b.jpg","overview":"Un lycéen est mordu."}
        ]}
    """.trimIndent()

    @Test
    fun picksTheResultWithTheSameNameAndYear() {
        val match = Tmdb.parseSearch(series, movie = false, wantedTitle = "Teen Wolf", wantedYear = "2011")!!
        assertEquals(34524, match.id)
        assertEquals("Teen Wolf", match.title)
        assertEquals("2011", match.year)
        assertEquals(listOf(10759, 18, 10765), match.genres)
        assertEquals("https://image.tmdb.org/t/p/w342/p.jpg", match.poster)
        assertEquals("https://image.tmdb.org/t/p/w1280/b.jpg", match.backdrop)
        assertEquals(8.6, match.rating, 0.001)
    }

    @Test
    fun refusesResultsThatAreAnotherTitle() {
        assertNull(Tmdb.parseSearch(series, movie = false, wantedTitle = "Les Experts", wantedYear = "2011"))
        assertNull(Tmdb.parseSearch("""{"results":[]}""", movie = true, wantedTitle = "Dune", wantedYear = ""))
        assertNull(Tmdb.parseSearch("""{}""", movie = true, wantedTitle = "Dune", wantedYear = ""))
    }

    @Test
    fun acceptsTheOriginalTitleAndANeighbouringYear() {
        val movies = """{"results":[{"id":7,"title":"La Colline a des yeux 2","original_title":"The Hills Have Eyes 2",
            "release_date":"2007-03-22","genre_ids":[27],"popularity":30,"vote_average":5.5,"vote_count":900,
            "poster_path":"/h.jpg","backdrop_path":"/hb.jpg","overview":"…"}]}"""
        assertEquals(7, Tmdb.parseSearch(movies, true, "The Hills Have Eyes 2", "2006")!!.id)
        assertEquals(7, Tmdb.parseSearch(movies, true, "La colline a des yeux 2", "")!!.id)
    }

    @Test
    fun readsLanguageAndQualityFromProviderTags() {
        assertEquals("FR", TitleCleaner.clean("FR - Teen Wolf (2011) (US)").tags)
        assertEquals("4K", TitleCleaner.clean("4K-D+ - Percy Jackson and the Olympians (2023) (US)").tags)
        assertEquals("MULTI 4K", TitleCleaner.clean("Dune [MULTI] 4K").tags)
        assertEquals("VOSTFR FHD", TitleCleaner.clean("VOSTFR - Shogun FHD").tags)
        assertEquals("", TitleCleaner.clean("Les Héritiers du Vent").tags)
        assertEquals("", TitleCleaner.clean("4M-AMZ - MELANIA the Series (2025)").tags)
        val dune = TitleCleaner.clean("FR - Dune (2021) 4K HDR ATMOS")
        assertEquals("Dune", dune.title)
        assertEquals("2021", dune.year)
        assertEquals("FR 4K HDR ATMOS", dune.tags)
        assertEquals("4K HDR", TitleCleaner.clean("Oppenheimer [4K DV]").tags)
        // Words that only look like tags stay in the title.
        assertEquals("Double Vision", TitleCleaner.clean("Double Vision").title)
        assertEquals("", TitleCleaner.clean("Double Vision").tags)
    }

    @Test
    fun picksATrailerOnYouTubeAndIgnoresOtherVideos() {
        val videos = """{"results":[
          {"site":"Vimeo","type":"Trailer","key":"123456789"},
          {"site":"YouTube","type":"Featurette","key":"aaaaaaaaaaa"},
          {"site":"YouTube","type":"Teaser","key":"tttttttttt1"},
          {"site":"YouTube","type":"Trailer","key":"dQw4w9WgXcQ"}
        ]}"""
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", Tmdb.parseTrailer(videos))
        assertEquals(
            "https://www.youtube.com/watch?v=tttttttttt1",
            Tmdb.parseTrailer("""{"results":[{"site":"YouTube","type":"Teaser","key":"tttttttttt1"}]}"""),
        )
        assertNull(Tmdb.parseTrailer("""{"results":[{"site":"YouTube","type":"Trailer","key":"bad key!"}]}"""))
        assertNull(Tmdb.parseTrailer("{}"))
    }

    @Test
    fun readsThePairingForm() {
        val form = PairingServer.parseForm("code=1234&value=eyJhbGci.abc%2Bdef+ghi&empty=")
        assertEquals("1234", form["code"])
        assertEquals("eyJhbGci.abc+def ghi", form["value"])
        assertEquals("", form["empty"])
    }
}
