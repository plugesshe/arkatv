package tv.arka.app

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.arka.app.data.TitleCleaner

class TitleCleanerTest {
    private fun title(raw: String) = TitleCleaner.clean(raw).title

    @Test
    fun removesProviderTagsAndKeepsTheYear() {
        val teen = TitleCleaner.clean("FR - Teen Wolf (2011) (US)")
        assertEquals("Teen Wolf", teen.title)
        assertEquals("2011", teen.year)
        val percy = TitleCleaner.clean("4K-D+ - Percy Jackson and the Olympians (2023) (US)")
        assertEquals("Percy Jackson and the Olympians", percy.title)
        assertEquals("2023", percy.year)
        assertEquals("MELANIA the Series", title("4M-AMZ - MELANIA the Series (2025)"))
        assertEquals("La colline a des yeux 2", title("FR - La colline a des yeux 2 (2007)"))
        assertEquals("Dune", title("Dune [MULTI] 4K"))
    }

    @Test
    fun leavesOrdinaryTitlesAlone() {
        assertEquals("Les Héritiers du Vent", title("Les Héritiers du Vent"))
        assertEquals("L'Héritier", title("L'Héritier"))
        assertEquals("Spider-Man: No Way Home", title("Spider-Man: No Way Home"))
        assertEquals("1917", title("1917"))
        assertEquals("Mission - Impossible", title("Mission - Impossible"))
        assertEquals("", TitleCleaner.clean("Héritage").year)
    }

    @Test
    fun neverReturnsAnEmptyTitle() {
        assertEquals("FR - X", title("FR - X"))
        assertEquals("(2020)", title("(2020)"))
    }

    @Test
    fun cleansEpisodeTitles() {
        assertEquals("Meute contre meute", TitleCleaner.episode("FR - Teen Wolf (2011) (US) - S02E05 - Meute contre meute"))
        assertEquals("Pilote", TitleCleaner.episode("Pilote"))
        assertEquals("", TitleCleaner.episode("Serie - S01E01"))
        assertEquals(
            "S2 · É5 · Meute contre meute",
            TitleCleaner.subtitle("S2 · É5 · FR - Teen Wolf (2011) (US) - S02E05 - Meute contre meute"),
        )
        assertEquals("S1 · É1", TitleCleaner.subtitle("S1 · É1 · MELANIA the Series - S01E01"))
        assertEquals("Reste 88 min", TitleCleaner.subtitle("Reste 88 min"))
    }
}
