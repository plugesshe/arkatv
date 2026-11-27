package tv.arka.app.data

import java.io.BufferedInputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Collections
import kotlin.concurrent.thread

/**
 * A tiny page served on the local network so that a long key can be pasted from a phone or a
 * computer instead of being typed with a remote. It only runs while its settings block is on screen,
 * asks for the four-digit [code] shown on the TV, and stops after a few wrong codes.
 * The page is plain HTTP: it must only be used on a home network.
 */
class PairingServer(private val fields: List<Field>, private val onValues: (Map<String, String>) -> Unit) {
    /** One input of the page. [long] shows a multi-line box; [minLength] 0 makes the field optional. */
    class Field(val name: String, val label: String, val long: Boolean = false, val minLength: Int = 1, val secret: Boolean = false)

    /** A single long value, such as an access key. */
    constructor(label: String, onValue: (String) -> Unit) : this(
        listOf(Field("value", label, long = true, minLength = MIN_VALUE)),
        { values -> onValue(values["value"].orEmpty()) },
    )

    val code: String = (1000..9999).random().toString()

    @Volatile
    private var server: ServerSocket? = null
    var port: Int = 0
        private set

    fun start(): Boolean = try {
        val socket = try {
            ServerSocket(PREFERRED_PORT)
        } catch (e: IOException) {
            ServerSocket(0)
        }
        server = socket
        port = socket.localPort
        thread(isDaemon = true, name = "arkatv-pairing") { serve(socket) }
        true
    } catch (e: Exception) {
        false
    }

    fun stop() {
        try {
            server?.close()
        } catch (e: IOException) {
            // Already closed.
        }
        server = null
    }

    private fun serve(socket: ServerSocket) {
        var failures = 0
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (e: IOException) {
                break
            }
            try {
                client.soTimeout = 8000
                when (handle(client)) {
                    Outcome.ACCEPTED -> stop()
                    Outcome.REFUSED -> {
                        failures++
                        if (failures >= MAX_FAILURES) stop()
                    }
                    Outcome.NONE -> {}
                }
            } catch (e: Exception) {
                // A broken request must not stop the page for the next one.
            } finally {
                try {
                    client.close()
                } catch (e: IOException) {
                    // Nothing to do.
                }
            }
        }
    }

    private enum class Outcome { NONE, ACCEPTED, REFUSED }

    private fun handle(client: Socket): Outcome {
        val input = BufferedInputStream(client.getInputStream())
        val head = StringBuilder()
        while (head.length < MAX_HEAD && !head.endsWith("\r\n\r\n")) {
            val byte = input.read()
            if (byte < 0) break
            head.append(byte.toChar())
        }
        val lines = head.split("\r\n")
        val method = lines.firstOrNull().orEmpty().substringBefore(' ')
        if (method != "POST") {
            respond(client, page(""))
            return Outcome.NONE
        }
        val length = lines.firstOrNull { it.startsWith("content-length:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toIntOrNull()?.coerceIn(0, MAX_BODY) ?: 0
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(body, read, length - read)
            if (count < 0) break
            read += count
        }
        val form = parseForm(String(body, 0, read, Charsets.UTF_8))
        val values = fields.associate { it.name to form[it.name].orEmpty().trim() }
        val complete = fields.all { values[it.name].orEmpty().length >= it.minLength }
        return if (form["code"]?.trim() == code && complete) {
            onValues(values)
            respond(client, page("C'est envoyé. Vous pouvez fermer cette page."))
            Outcome.ACCEPTED
        } else {
            respond(client, page("Code incorrect ou champ incomplet. Vérifiez le code affiché sur la TV."))
            Outcome.REFUSED
        }
    }

    private fun respond(client: Socket, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val out = client.getOutputStream()
        out.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
        )
        out.write(bytes)
        out.flush()
    }

    private fun page(message: String): String =
        "<!doctype html><html lang=\"fr\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>ArkaTV</title>" +
            "<style>body{font-family:sans-serif;background:#0E0E12;color:#F5F5F7;max-width:520px;margin:40px auto;padding:0 20px}" +
            "input,textarea,button{width:100%;box-sizing:border-box;font-size:16px;padding:12px;margin:6px 0 16px;border-radius:10px;border:0}" +
            "textarea{height:140px}button{background:#E50E3C;color:#fff;font-weight:700}p{color:#B4B4BE}</style></head><body>" +
            "<h2>ArkaTV</h2>" + (if (message.isNotEmpty()) "<p><b>" + escape(message) + "</b></p>" else "") +
            "<form method=\"post\" action=\"/\"><label>Code affiché sur la TV</label>" +
            "<input name=\"code\" inputmode=\"numeric\" maxlength=\"4\" autocomplete=\"off\">" +
            fields.joinToString("") { field ->
                "<label>" + escape(field.label) + "</label>" + if (field.long) {
                    "<textarea name=\"" + field.name + "\" autocomplete=\"off\"></textarea>"
                } else {
                    "<input name=\"" + field.name + "\" autocomplete=\"off\" autocapitalize=\"off\" type=\"" +
                        (if (field.secret) "password" else "text") + "\">"
                }
            } +
            "<button type=\"submit\">Envoyer à la TV</button></form>" +
            "<p>Ces informations sont envoyées à votre TV sur votre réseau local, puis conservées chiffrées sur l'appareil.</p></body></html>"

    private fun escape(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    companion object {
        private const val PREFERRED_PORT = 8765
        private const val MAX_HEAD = 16 * 1024
        private const val MAX_BODY = 16 * 1024
        private const val MAX_FAILURES = 5
        private const val MIN_VALUE = 20

        fun parseForm(body: String): Map<String, String> = body.split('&').mapNotNull { pair ->
            val key = pair.substringBefore('=', "")
            if (key.isEmpty()) {
                null
            } else {
                try {
                    URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        }.toMap()

        /** The address of this device on the home network, or null when it has none. */
        fun localAddress(): String? = try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { it.isUp && !it.isLoopback }
                .flatMap { Collections.list(it.inetAddresses) }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }
}
