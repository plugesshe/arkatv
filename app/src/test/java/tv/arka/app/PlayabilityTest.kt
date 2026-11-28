package tv.arka.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.arka.app.data.DeviceProfile
import tv.arka.app.data.Playability
import tv.arka.app.data.Requirement

class PlayabilityTest {
    /** A tablet whose hardware decoders stop at 1920×1088, without Dolby Vision, with an SDR screen. */
    private val fullHdOnly = DeviceProfile(1920, 1088, dolbyVision = false, hdrScreen = false)

    /** A device that decodes 4K and Dolby Vision. */
    private val everything = DeviceProfile(4096, 2304, dolbyVision = true, hdrScreen = true)

    @Test
    fun readsRequirementsFromProviderTitles() {
        val heavy = Playability.fromName("4K-D+ - Percy Jackson (2023) DV")
        assertEquals(3840, heavy.width)
        assertEquals(2160, heavy.height)
        assertTrue(heavy.dolbyVision)
        assertTrue(heavy.hdr)
        assertEquals(Requirement(), Playability.fromName("FR - Teen Wolf (2011) FHD"))
        assertTrue(Playability.fromName("Dune 2160p HDR10+").hdr)
        assertEquals(3840, Playability.fromName("Dune UHD").width)
        // Letters inside a word are not a tag.
        assertEquals(Requirement(), Playability.fromName("Advance"))
        assertFalse(Playability.fromName("Hdrama").hdr)
    }

    @Test
    fun aDeviceLimitedToFullHdCannotPlay4k() {
        assertFalse(Playability.canPlay(Requirement(3840, 2160), fullHdOnly))
        assertTrue(Playability.canPlay(Requirement(1920, 1080), fullHdOnly))
        // Decoders declare sizes rounded to blocks.
        assertTrue(Playability.canPlay(Requirement(1920, 1088), fullHdOnly))
        assertTrue(Playability.canPlay(Requirement(3840, 2160), everything))
        // Unknown size: benefit of the doubt.
        assertTrue(Playability.canPlay(Requirement(), fullHdOnly))
        assertTrue(Playability.canPlay(Requirement(3840, 2160), DeviceProfile.UNKNOWN))
    }

    @Test
    fun dolbyVisionOnlyBlocksSingleLayerWithoutADecoder() {
        val profile5 = Requirement(1920, 1080, dolbyVision = true, hdr = true, visionProfile = 5)
        val profile8 = Requirement(1920, 1080, dolbyVision = true, hdr = true, visionProfile = 8)
        assertFalse(Playability.canPlay(profile5, fullHdOnly))
        assertTrue(Playability.canPlay(profile8, fullHdOnly))
        assertTrue(Playability.canPlay(profile5, everything))
        // Profile not known: left visible, the player finds out.
        assertTrue(Playability.canPlay(Requirement(1920, 1080, dolbyVision = true), fullHdOnly))
    }

    @Test
    fun nothingIsHiddenOnACapableDevice() {
        assertTrue(Playability.hiddenNamePatterns(everything).isEmpty())
        assertTrue(Playability.hiddenNamePatterns(DeviceProfile.UNKNOWN).isEmpty())
        assertFalse(Playability.isHiddenName("CANAL+ 4K", everything))
        assertEquals(listOf("%4K%", "%UHD%", "%2160%"), Playability.hiddenNamePatterns(fullHdOnly))
        assertTrue(Playability.isHiddenName("CANAL+ 4K", fullHdOnly))
        assertTrue(Playability.isHiddenName("Sport uhd", fullHdOnly))
        assertFalse(Playability.isHiddenName("CANAL+ FHD", fullHdOnly))
    }
}
