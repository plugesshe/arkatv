package tv.arka.app.player

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import tv.arka.app.data.DeviceProfile

/**
 * What this device can really decode and show, asked once to the system: hardware video decoders
 * and their limits, HDR of the screen, audio decoders. Software video decoders are left out: they
 * cannot play 4K smoothly on the kind of chip that lacks a hardware one.
 */
object DeviceVideo {
    class Capabilities(
        val uhd: Boolean,
        val tenBit: Boolean,
        val dolbyVision: Boolean,
        val dolbyVisionUhd: Boolean,
        val maxWidth: Int,
        val maxHeight: Int,
        /** One line per video format: "HEVC : 1920×1088 · 10 bits". */
        val video: List<String>,
        /** HDR formats the screen shows; empty for an SDR screen. */
        val hdr: List<String>,
        /** Audio formats decoded by the device itself. */
        val audio: List<String>,
        val known: Boolean,
    ) {
        val largest: String get() = if (maxWidth > 0) "$maxWidth×$maxHeight" else ""
    }

    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    val capabilities: Capabilities by lazy { probe() }

    val profile: DeviceProfile by lazy {
        val c = capabilities
        if (!c.known) DeviceProfile.UNKNOWN else DeviceProfile(c.maxWidth, c.maxHeight, c.dolbyVision, c.hdr.isNotEmpty())
    }

    private val videoTypes = listOf(
        "video/avc" to "H.264",
        "video/hevc" to "HEVC",
        "video/x-vnd.on2.vp9" to "VP9",
        "video/av01" to "AV1",
        "video/dolby-vision" to "Dolby Vision",
    )
    private val audioTypes = listOf(
        "audio/eac3-joc" to "Dolby Atmos (E-AC-3 JOC)",
        "audio/ac4" to "Dolby AC-4",
        "audio/eac3" to "Dolby Digital+",
        "audio/ac3" to "Dolby Digital",
        "audio/true-hd" to "Dolby TrueHD",
        "audio/vnd.dts" to "DTS",
        "audio/vnd.dts.hd" to "DTS-HD",
    )

    private fun isHardware(info: MediaCodecInfo): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        info.isHardwareAccelerated
    } else {
        !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")
    }

    private fun probe(): Capabilities {
        val widths = HashMap<String, Int>()
        val heights = HashMap<String, Int>()
        val tenBitTypes = HashSet<String>()
        val uhdTypes = HashSet<String>()
        val audioFound = LinkedHashSet<String>()
        try {
            for (info in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
                if (info.isEncoder) continue
                for (type in info.supportedTypes) {
                    val mime = type.lowercase()
                    audioTypes.firstOrNull { it.first == mime }?.let { audioFound.add(it.second) }
                    if (videoTypes.none { it.first == mime } || !isHardware(info)) continue
                    val capabilities = info.getCapabilitiesForType(type)
                    val video = capabilities.videoCapabilities ?: continue
                    val width = video.supportedWidths.upper
                    val height = video.supportedHeights.upper
                    if (width.toLong() * height > (widths[mime] ?: 0).toLong() * (heights[mime] ?: 0)) {
                        widths[mime] = width
                        heights[mime] = height
                    }
                    val acceptsUhd = try {
                        video.areSizeAndRateSupported(3840, 2160, 24.0)
                    } catch (e: IllegalArgumentException) {
                        false
                    }
                    if (acceptsUhd) uhdTypes.add(mime)
                    val tenBit = capabilities.profileLevels.any {
                        when (mime) {
                            "video/hevc" -> it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
                                it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
                            "video/x-vnd.on2.vp9" -> it.profile == MediaCodecInfo.CodecProfileLevel.VP9Profile2 ||
                                it.profile == MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR
                            else -> false
                        }
                    }
                    if (tenBit) tenBitTypes.add(mime)
                }
            }
        } catch (e: Exception) {
            // Unknown: nothing is ruled out, the player finds out by trying.
            return Capabilities(true, true, false, false, 0, 0, emptyList(), emptyList(), emptyList(), known = false)
        }
        val lines = videoTypes.mapNotNull { (mime, label) ->
            val width = widths[mime] ?: return@mapNotNull null
            label + " : " + width + "×" + (heights[mime] ?: 0) +
                (if (mime in uhdTypes) " · 4K" else "") + (if (mime in tenBitTypes) " · 10 bits" else "")
        }
        // The size that decides what is shown: HEVC when the device has it (4K films are HEVC), else H.264.
        val reference = if (widths.containsKey("video/hevc")) "video/hevc" else "video/avc"
        return Capabilities(
            uhd = "video/hevc" in uhdTypes || ("video/hevc" !in widths && "video/avc" in uhdTypes),
            tenBit = "video/hevc" in tenBitTypes,
            dolbyVision = widths.containsKey("video/dolby-vision"),
            dolbyVisionUhd = "video/dolby-vision" in uhdTypes,
            maxWidth = widths[reference] ?: 0,
            maxHeight = heights[reference] ?: 0,
            video = lines,
            hdr = screenHdr(),
            audio = audioFound.toList(),
            known = widths.isNotEmpty(),
        )
    }

    @Suppress("DEPRECATION")
    private fun screenHdr(): List<String> = try {
        val manager = appContext?.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val types = manager?.getDisplay(Display.DEFAULT_DISPLAY)?.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)
        types.map {
            when (it) {
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
                else -> "HDR"
            }
        }.distinct()
    } catch (e: Exception) {
        emptyList()
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
            if (c.hdr.isEmpty()) "écran SDR" else "écran " + c.hdr.joinToString(", "),
        ).joinToString(" · ")
    }
}
