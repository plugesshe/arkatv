package tv.arka.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tv.arka.app.data.Updater
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class UpdaterTest {
    private val sha = "A".repeat(64)

    @Test
    fun readsThePublishedDescription() {
        val info = Updater.parse("""{"versionCode":1042,"versionName":"0.2.0-dev","sha256":"$sha","size":123,"notes":"Fix"}""")
        assertEquals(1042L, info.versionCode)
        assertEquals("0.2.0-dev", info.versionName)
        assertEquals("a".repeat(64), info.sha256)
        assertEquals(123L, info.size)
        assertEquals("Fix", info.notes)
    }

    @Test(expected = IOException::class)
    fun rejectsADescriptionWithoutAValidChecksum() {
        Updater.parse("""{"versionCode":1042,"sha256":"abc"}""")
    }

    @Test(expected = IOException::class)
    fun rejectsADescriptionWithoutAVersion() {
        Updater.parse("""{"sha256":"$sha"}""")
    }

    @Test
    fun onlyAHigherBuildIsAnUpdate() {
        val info = Updater.parse("""{"versionCode":1042,"sha256":"$sha"}""")
        assertTrue(Updater.isNewer(info, 1041))
        assertFalse(Updater.isNewer(info, 1042))
        assertFalse(Updater.isNewer(info, 1043))
    }
}
