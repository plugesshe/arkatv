package tv.arka.app.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.PushbackInputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.zip.GZIPInputStream

/** Streaming XMLTV reader. Keeps only the wanted channels inside a time window. */
object Epg {

    fun load(url: String, wanted: Set<String>): Map<String, List<Program>> {
        val now = System.currentTimeMillis() / 1000
        val from = now - 2 * 3600
        val to = now + 24 * 3600
        val result = HashMap<String, MutableList<Program>>()

        Http.open(url).use { response ->
            val body = response.body ?: return result
            val pushback = PushbackInputStream(BufferedInputStream(body.byteStream()), 2)
            val signature = ByteArray(2)
            val read = pushback.read(signature)
            if (read > 0) pushback.unread(signature, 0, read)
            val gzip = read == 2 && signature[0] == 0x1f.toByte() && signature[1] == 0x8b.toByte()
            val input: InputStream = if (gzip) GZIPInputStream(pushback) else pushback

            try {
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(input, null)

                var channel: String? = null
                var start = 0L
                var end = 0L
                var title = ""
                var desc = ""
                var tag: String? = null

                var event = parser.next()
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            if (parser.name == "programme") {
                                val id = parser.getAttributeValue(null, "channel")?.lowercase()
                                start = parseTime(parser.getAttributeValue(null, "start"))
                                end = parseTime(parser.getAttributeValue(null, "stop"))
                                channel = if (id != null && id in wanted && end > from && start < to) id else null
                                title = ""
                                desc = ""
                            } else if (channel != null) {
                                tag = parser.name
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (channel != null) {
                                when (tag) {
                                    "title" -> title += parser.text
                                    "desc" -> desc += parser.text
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == "programme") {
                                val id = channel
                                if (id != null) {
                                    result.getOrPut(id) { ArrayList() }
                                        .add(Program(start, end, title.trim(), desc.trim().take(500)))
                                }
                                channel = null
                            }
                            tag = null
                        }
                    }
                    event = parser.next()
                }
            } catch (e: Exception) {
                // A truncated or malformed guide still gives us everything read so far.
            }
        }
        result.values.forEach { list -> list.sortBy { it.start } }
        return result
    }

    /** XMLTV time: "20261127203000 +0100". */
    fun parseTime(value: String?): Long {
        if (value == null || value.length < 14) return 0
        return try {
            val year = value.substring(0, 4).toInt()
            val month = value.substring(4, 6).toInt()
            val day = value.substring(6, 8).toInt()
            val hour = value.substring(8, 10).toInt()
            val minute = value.substring(10, 12).toInt()
            val second = value.substring(12, 14).toInt()
            var offset = 0
            val rest = value.substring(14).trim()
            if (rest.length >= 5 && (rest[0] == '+' || rest[0] == '-')) {
                val h = rest.substring(1, 3).toInt()
                val m = rest.substring(3, 5).toInt()
                offset = (h * 3600 + m * 60) * (if (rest[0] == '-') -1 else 1)
            }
            LocalDateTime.of(year, month, day, hour, minute, second)
                .toEpochSecond(ZoneOffset.ofTotalSeconds(offset))
        } catch (e: Exception) {
            0
        }
    }
}
