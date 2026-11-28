package tv.arka.app.data

/**
 * What a device can decode, reduced to what decides whether a title is shown. It always comes from
 * what the device itself reports, never from a list of models.
 */
data class DeviceProfile(
    /** Largest picture a hardware decoder accepts. 0 means unknown: nothing is then ruled out. */
    val maxWidth: Int,
    val maxHeight: Int,
    /** A Dolby Vision decoder exists. */
    val dolbyVision: Boolean,
    /** The screen can show HDR. */
    val hdrScreen: Boolean,
) {
    val uhd: Boolean get() = maxWidth == 0 || maxWidth.toLong() * maxHeight >= 3840L * 2160

    companion object {
        /** Used until the device has been asked: everything is assumed playable. */
        val UNKNOWN = DeviceProfile(0, 0, dolbyVision = true, hdrScreen = true)
    }
}

/** What playing a title requires. A zero size means "not known". [visionProfile] 0 is unknown too. */
data class Requirement(
    val width: Int = 0,
    val height: Int = 0,
    val dolbyVision: Boolean = false,
    val hdr: Boolean = false,
    val visionProfile: Int = 0,
)

object Playability {
    private const val EDGE = "(^|[^A-Za-z0-9])"
    private const val END = "([^A-Za-z0-9]|$)"
    private val uhdTag = Regex("$EDGE(4K|UHD|2160P?)$END", RegexOption.IGNORE_CASE)
    private val visionTag = Regex("$EDGE(DV|DOVI|DOLBY[ .]?VISION)$END", RegexOption.IGNORE_CASE)
    private val hdrTag = Regex("$EDGE(HDR|HDR10|HDR10\\+|HLG)$END", RegexOption.IGNORE_CASE)

    /** The least reliable source: what the provider wrote in the title. */
    fun fromName(name: String): Requirement {
        val uhd = uhdTag.containsMatchIn(name)
        val vision = visionTag.containsMatchIn(name)
        return Requirement(
            width = if (uhd) 3840 else 0,
            height = if (uhd) 2160 else 0,
            dolbyVision = vision,
            hdr = vision || hdrTag.containsMatchIn(name),
        )
    }

    /**
     * True when the device can decode this. An unknown size is given the benefit of the doubt.
     * Dolby Vision without a dedicated decoder still plays through HEVC, except single-layer
     * profile 5, which has no ordinary picture underneath.
     */
    fun canPlay(requirement: Requirement, device: DeviceProfile): Boolean {
        if (requirement.dolbyVision && requirement.visionProfile == 5 && !device.dolbyVision) return false
        if (requirement.width <= 0 || requirement.height <= 0 || device.maxWidth <= 0) return true
        // Decoders report sizes rounded to blocks (1920×1088): allow a few percent.
        return requirement.width.toLong() * requirement.height <= device.maxWidth.toLong() * device.maxHeight * 105 / 100
    }

    /** Title patterns (SQL LIKE) this device cannot play; empty for a device that decodes 4K. */
    fun hiddenNamePatterns(device: DeviceProfile): List<String> =
        if (canPlay(Requirement(3840, 2160), device)) emptyList() else listOf("%4K%", "%UHD%", "%2160%")

    /** The same rule as [hiddenNamePatterns], for lists held in memory. */
    fun isHiddenName(name: String, device: DeviceProfile): Boolean {
        if (canPlay(Requirement(3840, 2160), device)) return false
        val upper = name.uppercase()
        return "4K" in upper || "UHD" in upper || "2160" in upper
    }
}
