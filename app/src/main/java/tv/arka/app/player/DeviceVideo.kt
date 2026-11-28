package tv.arka.app.player

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

/**
 * What this device can really decode, asked once to the system. Used to pick, among the versions of
 * a film, the best one the device can play, instead of starting one it will fail on.
 */
object DeviceVideo {
    class Capabilities(
        /** A hardware decoder accepts HEVC at 3840×2160. */
        val uhd: Boolean,
        /** A hardware decoder accepts HEVC 10-bit (HDR). */
        val tenBit: Boolean,
        /** A Dolby Vision decoder exists, and whether it accepts 3840×2160. */
        val dolbyVision: Boolean,
        val dolbyVisionUhd: Boolean,
        /** Largest HEVC picture a hardware decoder declares, as "1920×1080". */
        val largest: String,
    )

    val capabilities: Capabilities by lazy { probe() }

    private fun probe(): Capabilities {
        var uhd = false
        var tenBit = false
        var vision = false
        var visionUhd = false
        var largestWidth = 0
        var largestHeight = 0
        try {
            for (info in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
                if (info.isEncoder) continue
                val hardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    info.isHardwareAccelerated
                } else {
                    !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")
                }
                if (!hardware) continue
                for (type in info.supportedTypes) {
                    val mime = type.lowercase()
                    if (mime != "video/hevc" && mime != "video/dolby-vision") continue
                    val capabilities = info.getCapabilitiesForType(type)
                    val video = capabilities.videoCapabilities ?: continue
                    val acceptsUhd = try {
                        video.isSizeSupported(3840, 2160)
                    } catch (e: IllegalArgumentException) {
                        false
                    }
                    if (mime == "video/dolby-vision") {
                        vision = true
                        if (acceptsUhd) visionUhd = true
                    } else {
                        if (acceptsUhd) uhd = true
                        if (capabilities.profileLevels.any {
                                it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
                                    it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
                            }
                        ) {
                            tenBit = true
                        }
                        val width = video.supportedWidths.upper
                        val height = video.supportedHeights.upper
                        if (width * height > largestWidth * largestHeight) {
                            largestWidth = width
                            largestHeight = height
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Unknown: nothing is ruled out, the player finds out by trying.
            return Capabilities(uhd = true, tenBit = true, dolbyVision = false, dolbyVisionUhd = false, largest = "")
        }
        return Capabilities(uhd, tenBit, vision, visionUhd, if (largestWidth > 0) "$largestWidth×$largestHeight" else "")
    }

    /** One line for messages and diagnostics. */
    val summary: String by lazy {
        val c = capabilities
        listOf(
            "HEVC jusqu'à " + c.largest.ifEmpty { "?" },
            if (c.uhd) "4K : oui" else "4K : non",
            if (c.tenBit) "10 bits : oui" else "10 bits : non",
            when {
                c.dolbyVisionUhd -> "Dolby Vision : oui, 4K comprise"
                c.dolbyVision -> "Dolby Vision : oui, pas en 4K"
                else -> "Dolby Vision : non"
            },
        ).joinToString(" · ")
    }
}
