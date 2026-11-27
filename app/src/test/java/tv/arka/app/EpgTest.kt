package tv.arka.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tv.arka.app.data.Epg
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
class EpgTest {
    // 2026-11-27 20:00:00 UTC
    private val base = 1795809600L

    private val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv>
          <channel id="One.fr"><display-name>One</display-name></channel>
          <programme start="20261127210000 +0100" stop="20261127220000 +0100" channel="One.fr">
            <title lang="fr">Le journal</title>
            <desc lang="fr">Toute l'actualité.</desc>
          </programme>
          <programme start="20261127190000 +0000" stop="20261127200000 +0000" channel="One.fr">
            <title>Avant</title>
          </programme>
          <programme start="20261127210000 +0000" stop="20261127220000 +0000" channel="other.fr">
            <title>Ignoré</title>
          </programme>
          <programme start="20261201210000 +0000" stop="20261201220000 +0000" channel="One.fr">
            <title>Trop tard</title>
          </programme>
        </tv>
    """.trimIndent()

    @Test
    fun parsesXmltvTimesWithTheirOffset() {
        assertEquals(base, Epg.parseTime("20261127200000 +0000"))
        assertEquals(base, Epg.parseTime("20261127210000 +0100"))
        assertEquals(base, Epg.parseTime("20261127150000 -0500"))
        assertEquals(base, Epg.parseTime("20261127200000"))
        assertEquals(0L, Epg.parseTime("bad"))
        assertEquals(0L, Epg.parseTime(null))
    }

    @Test
    fun keepsWantedChannelsInsideTheWindowSortedByStart() {
        val guide = Epg.parse(xml.byteInputStream(), setOf("one.fr"), base - 3600, base + 24 * 3600)
        assertEquals(setOf("one.fr"), guide.keys)
        val programs = guide.getValue("one.fr")
        assertEquals(listOf("Avant", "Le journal"), programs.map { it.title })
        assertEquals(base - 3600, programs[0].start)
        assertEquals(base, programs[0].end)
        assertEquals(base, programs[1].start)
        assertEquals("Toute l'actualité.", programs[1].desc)
        assertNull(guide["other.fr"])
    }

    @Test
    fun readsGzippedGuides() {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(xml.toByteArray()) }
        val guide = Epg.parse(bytes.toByteArray().inputStream(), setOf("one.fr"), base - 3600, base + 24 * 3600)
        assertEquals(2, guide.getValue("one.fr").size)
    }

    @Test
    fun keepsWhatWasReadFromATruncatedGuide() {
        val cut = xml.substring(0, xml.indexOf("<programme start=\"20261127190000"))
        val guide = Epg.parse(cut.byteInputStream(), setOf("one.fr"), base - 3600, base + 24 * 3600)
        assertEquals(listOf("Le journal"), guide.getValue("one.fr").map { it.title })
    }
}
