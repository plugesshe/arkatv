package tv.arka.app.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * In-app updates for builds installed outside a store.
 *
 * The app reads a small description of the newest published build, and only when the user accepts
 * does it download the APK, check its SHA-256 and hand it to the system installer. Nothing is sent:
 * both requests are plain downloads from the project's public GitHub releases.
 */
object Updater {
    private const val BASE = "https://github.com/plugesshe/arkatv/releases/download/dev/"
    private const val INFO_URL = BASE + "version.json"
    private const val APK_URL = BASE + "ArkaTV-dev.apk"
    private const val MAX_APK_BYTES = 300L * 1024 * 1024

    class Info(val versionCode: Long, val versionName: String, val sha256: String, val size: Long, val notes: String)

    sealed class State {
        data object Idle : State()
        data object Checking : State()
        data object UpToDate : State()
        class Available(val info: Info) : State()
        class Downloading(val info: Info, val percent: Int) : State()
        class Ready(val info: Info) : State()
        class Failed(val message: String) : State()
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    fun parse(json: String): Info {
        val root = JSONObject(json)
        val sha = root.optString("sha256").trim().lowercase()
        val code = root.optLong("versionCode", 0)
        if (code <= 0 || !Regex("[0-9a-f]{64}").matches(sha)) throw IOException("Description de version invalide")
        return Info(code, root.optString("versionName"), sha, root.optLong("size", 0), root.optString("notes").take(500))
    }

    fun isNewer(info: Info, installed: Long): Boolean = info.versionCode > installed

    fun installedVersionCode(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    fun installedVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""

    /** [silent] is the check made when the app opens: it never reports a failure. */
    suspend fun check(context: Context, silent: Boolean) {
        val current = state
        if (current is State.Checking || current is State.Downloading) return
        state = State.Checking
        state = try {
            val info = withContext(Dispatchers.IO) { parse(Http.getString(INFO_URL)) }
            if (isNewer(info, installedVersionCode(context))) State.Available(info) else State.UpToDate
        } catch (e: CancellationException) {
            state = State.Idle
            throw e
        } catch (e: Exception) {
            when {
                silent -> State.Idle
                e.message?.contains("404") == true -> State.Failed("Aucune version n'est publiée pour l'instant.")
                else -> State.Failed("Vérification impossible : " + (e.message ?: "pas de réponse") + ".")
            }
        }
    }

    suspend fun download(context: Context, info: Info) {
        if (state is State.Downloading) return
        state = State.Downloading(info, 0)
        val target = apkFile(context)
        state = try {
            withContext(Dispatchers.IO) { fetch(info, target) }
            State.Ready(info)
        } catch (e: CancellationException) {
            target.delete()
            state = State.Available(info)
            throw e
        } catch (e: Exception) {
            target.delete()
            State.Failed("Téléchargement impossible : " + (e.message ?: "erreur inconnue") + ".")
        }
        if (state is State.Ready) install(context)
    }

    private fun fetch(info: Info, target: File) {
        target.parentFile?.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        Http.open(APK_URL).use { response ->
            val body = response.body ?: throw IOException("réponse vide")
            val total = if (info.size > 0) info.size else body.contentLength()
            var done = 0L
            var shown = 0
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        done += read
                        if (done > MAX_APK_BYTES) throw IOException("fichier trop volumineux")
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        val percent = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0
                        if (percent != shown) {
                            shown = percent
                            state = State.Downloading(info, percent)
                        }
                    }
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        // A build replaced on the server between the two requests also ends here: check again.
        if (actual != info.sha256) throw IOException("le fichier reçu ne correspond pas à la version annoncée, réessayez")
    }

    /** Opens the system installer, which asks for confirmation (and for the permission the first time). */
    fun install(context: Context) {
        val file = apkFile(context)
        if (!file.exists()) {
            state = State.Failed("Le fichier de mise à jour n'est plus disponible.")
            return
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            state = State.Failed("Cet appareil ne propose pas d'installeur de paquets.")
        }
    }

    private fun apkFile(context: Context) = File(File(context.cacheDir, "updates"), "ArkaTV-update.apk")
}
