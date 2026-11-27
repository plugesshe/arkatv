package tv.arka.app.data

import android.app.Instrumentation
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.BufferedInputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.UUID
import kotlin.concurrent.thread

/**
 * ArkaTV Connect: a remote control served as a web page on the local network, for a phone or a
 * computer. Off by default. The page asks for the four-digit [code] shown in the settings, then
 * sends the arrows, OK, Back, play / pause and typed text to ArkaTV — and only to ArkaTV: the
 * system refuses the keys when another application is in front.
 * The page is plain HTTP and must only be used on a home network.
 */
object ArkaConnect {
    private const val PREFERRED_PORT = 8766
    private const val MAX_HEAD = 16 * 1024
    private const val MAX_BODY = 4 * 1024
    private const val MAX_FAILURES = 5

    var running by mutableStateOf(false)
        private set
    var port by mutableIntStateOf(0)
        private set
    var code by mutableStateOf("")
        private set

    @Volatile
    private var server: ServerSocket? = null
    private val tokens = HashSet<String>()
    private var failures = 0
    private val instrumentation = Instrumentation()

    private val keys = mapOf(
        "up" to KeyEvent.KEYCODE_DPAD_UP,
        "down" to KeyEvent.KEYCODE_DPAD_DOWN,
        "left" to KeyEvent.KEYCODE_DPAD_LEFT,
        "right" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "ok" to KeyEvent.KEYCODE_DPAD_CENTER,
        "back" to KeyEvent.KEYCODE_BACK,
        "play" to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        "rewind" to KeyEvent.KEYCODE_MEDIA_REWIND,
        "forward" to KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        "chup" to KeyEvent.KEYCODE_CHANNEL_UP,
        "chdown" to KeyEvent.KEYCODE_CHANNEL_DOWN,
        "delete" to KeyEvent.KEYCODE_DEL,
    )

    fun start(): Boolean {
        if (running) return true
        return try {
            val socket = try {
                ServerSocket(PREFERRED_PORT)
            } catch (e: IOException) {
                ServerSocket(0)
            }
            server = socket
            port = socket.localPort
            code = (1000..9999).random().toString()
            failures = 0
            synchronized(tokens) { tokens.clear() }
            running = true
            thread(isDaemon = true, name = "arkatv-connect") { serve(socket) }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun stop() {
        running = false
        try {
            server?.close()
        } catch (e: IOException) {
            // Already closed.
        }
        server = null
    }

    private fun serve(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (e: IOException) {
                break
            }
            // One short thread per request: a slow phone must not hold the remote for another one.
            thread(isDaemon = true, name = "arkatv-connect-request") {
                try {
                    client.soTimeout = 8000
                    handle(client)
                } catch (e: Exception) {
                    // A broken request is simply dropped.
                } finally {
                    try {
                        client.close()
                    } catch (e: IOException) {
                        // Nothing to do.
                    }
                }
            }
        }
    }

    private fun handle(client: Socket) {
        val input = BufferedInputStream(client.getInputStream())
        val head = StringBuilder()
        while (head.length < MAX_HEAD && !head.endsWith("\r\n\r\n")) {
            val byte = input.read()
            if (byte < 0) break
            head.append(byte.toChar())
        }
        val lines = head.split("\r\n")
        val request = lines.firstOrNull().orEmpty().split(' ')
        val method = request.getOrNull(0).orEmpty()
        val target = request.getOrNull(1).orEmpty()
        val path = target.substringBefore('?')
        val query = PairingServer.parseForm(target.substringAfter('?', ""))
        val length = lines.firstOrNull { it.startsWith("content-length:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toIntOrNull()?.coerceIn(0, MAX_BODY) ?: 0
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(body, read, length - read)
            if (count < 0) break
            read += count
        }
        val text = String(body, 0, read, Charsets.UTF_8)
        val authorised = synchronized(tokens) { query["t"] in tokens }

        when {
            method == "POST" && path == "/login" -> {
                if (PairingServer.parseForm(text)["code"]?.trim() == code) {
                    val token = UUID.randomUUID().toString()
                    synchronized(tokens) { tokens.add(token) }
                    respond(client, 200, "text/html; charset=utf-8", remotePage(token))
                } else {
                    failures++
                    respond(client, 200, "text/html; charset=utf-8", loginPage("Code incorrect."))
                    if (failures >= MAX_FAILURES) stop()
                }
            }
            method == "POST" && path == "/key" && authorised -> {
                val key = keys[query["k"]]
                if (key == null) respond(client, 400, "text/plain; charset=utf-8", "Touche inconnue") else inject(client) {
                    instrumentation.sendKeyDownUpSync(key)
                }
            }
            method == "POST" && path == "/text" && authorised -> {
                val typed = try {
                    URLDecoder.decode(text, "UTF-8")
                } catch (e: IllegalArgumentException) {
                    text
                }
                inject(client) { instrumentation.sendStringSync(typed.take(500)) }
            }
            method == "POST" -> respond(client, 403, "text/plain; charset=utf-8", "Session expirée : rechargez la page.")
            else -> respond(client, 200, "text/html; charset=utf-8", loginPage(""))
        }
    }

    /** Keys only reach ArkaTV's own windows; anything else is refused by the system. */
    private fun inject(client: Socket, action: () -> Unit) {
        try {
            action()
            respond(client, 200, "text/plain; charset=utf-8", "ok")
        } catch (e: SecurityException) {
            respond(client, 409, "text/plain; charset=utf-8", "ArkaTV n'est pas à l'écran.")
        } catch (e: Exception) {
            respond(client, 500, "text/plain; charset=utf-8", "Action impossible.")
        }
    }

    private fun respond(client: Socket, status: Int, type: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val out = client.getOutputStream()
        out.write(
            ("HTTP/1.1 $status OK\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
        )
        out.write(bytes)
        out.flush()
    }

    private const val STYLE = "<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1,user-scalable=no\">" +
        "<title>ArkaTV Connect</title><style>body{font-family:sans-serif;background:#0E0E12;color:#F5F5F7;max-width:420px;" +
        "margin:24px auto;padding:0 16px;text-align:center}button,input{font-size:18px;border:0;border-radius:14px;padding:16px;" +
        "margin:4px;background:#22222B;color:#F5F5F7}button:active{background:#E50E3C}.pad{display:grid;grid-template-columns:" +
        "repeat(3,1fr);gap:6px;margin:16px 0}.row{display:grid;grid-template-columns:repeat(3,1fr);gap:6px}input{width:100%;" +
        "box-sizing:border-box;background:#18181F}.red{background:#E50E3C}p{color:#B4B4BE;min-height:1.2em}</style>"

    private fun loginPage(message: String): String =
        "<!doctype html><html lang=\"fr\"><head>$STYLE</head><body><h2>ArkaTV Connect</h2><p>$message</p>" +
            "<form method=\"post\" action=\"/login\"><input name=\"code\" inputmode=\"numeric\" maxlength=\"4\" " +
            "placeholder=\"Code affiché sur la TV\" autocomplete=\"off\"><button class=\"red\" type=\"submit\">Connecter</button></form></body></html>"

    private fun remotePage(token: String): String =
        "<!doctype html><html lang=\"fr\"><head>$STYLE</head><body><h2>ArkaTV Connect</h2><p id=\"s\"></p>" +
            "<div class=\"pad\"><span></span><button onclick=\"k('up')\">▲</button><span></span>" +
            "<button onclick=\"k('left')\">◀</button><button class=\"red\" onclick=\"k('ok')\">OK</button><button onclick=\"k('right')\">▶</button>" +
            "<span></span><button onclick=\"k('down')\">▼</button><span></span></div>" +
            "<div class=\"row\"><button onclick=\"k('back')\">Retour</button><button onclick=\"k('play')\">⏯</button><button onclick=\"k('delete')\">⌫</button>" +
            "<button onclick=\"k('rewind')\">⏪</button><button onclick=\"k('chup')\">Chaîne +</button><button onclick=\"k('forward')\">⏩</button>" +
            "<span></span><button onclick=\"k('chdown')\">Chaîne −</button><span></span></div>" +
            "<p>Texte à saisir dans le champ sélectionné sur la TV :</p><input id=\"t\" autocomplete=\"off\" autocapitalize=\"off\">" +
            "<button class=\"red\" onclick=\"send()\">Envoyer le texte</button>" +
            "<script>var T='$token',s=document.getElementById('s');function done(r){r.text().then(function(x){s.textContent=r.ok?'':x})}" +
            "function fail(){s.textContent='TV injoignable'}" +
            "function k(c){fetch('/key?t='+T+'&k='+c,{method:'POST'}).then(done,fail)}" +
            "function send(){var i=document.getElementById('t');fetch('/text?t='+T,{method:'POST',body:encodeURIComponent(i.value)})" +
            ".then(function(r){if(r.ok)i.value='';done(r)},fail)}</script></body></html>"
}
