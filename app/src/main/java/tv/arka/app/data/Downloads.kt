package tv.arka.app.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** A film or an episode kept on the device. [path] is the file; the stream address is never stored here. */
class DownloadEntry(val id: Long, val key: String, val title: String, val subtitle: String, val poster: String, val path: String)

class DownloadState(val entry: DownloadEntry, val status: Int, val done: Long, val total: Long)

/**
 * Offline copies of films and episodes, fetched by the system download manager so that they
 * continue in the background and survive restarts. Files live in the app's own folder: they need
 * no permission and are removed with the app.
 */
object Downloads {
    const val PENDING = 0
    const val RUNNING = 1
    const val DONE = 2
    const val FAILED = 3

    var entries by mutableStateOf(listOf<DownloadEntry>())
        private set
    private var loaded = false

    fun init() {
        if (loaded) return
        loaded = true
        entries = Store.loadDownloads()
    }

    /** Only plain files can be kept: live channels and adaptive (HLS) streams cannot. */
    fun canDownload(item: PlayItem): Boolean {
        val path = item.url.substringBefore('?')
        return !item.live && (item.url.startsWith("http://") || item.url.startsWith("https://")) &&
            !path.endsWith(".m3u8") && "/timeshift/" !in path
    }

    fun has(key: String): Boolean = entries.any { it.key == key }

    /** Returns null when the download was queued, otherwise the reason it was not. */
    fun start(context: Context, item: PlayItem): String? {
        init()
        if (!canDownload(item)) return "Cette vidéo ne peut pas être téléchargée."
        if (has(item.key)) return "Déjà dans vos téléchargements."
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return "Cet appareil n'a pas de gestionnaire de téléchargements."
        val folder = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: return "Stockage indisponible."
        val extension = item.url.substringBefore('?').substringAfterLast('.', "mp4").takeIf { it.length in 2..4 } ?: "mp4"
        val name = item.key.replace(Regex("[^A-Za-z0-9_-]"), "_") + "." + extension
        val file = File(folder, name)
        if (file.exists()) file.delete()
        val id = try {
            manager.enqueue(
                DownloadManager.Request(Uri.parse(item.url))
                    .setTitle(item.title)
                    .setDescription(item.subtitle.ifBlank { "ArkaTV" })
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MOVIES, name)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .addRequestHeader("User-Agent", Http.USER_AGENT)
            )
        } catch (e: Exception) {
            return "Le système a refusé ce téléchargement."
        }
        save(entries + DownloadEntry(id, item.key, item.title, item.subtitle, item.poster, file.absolutePath))
        return null
    }

    fun remove(context: Context, entry: DownloadEntry) {
        try {
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager)?.remove(entry.id)
        } catch (e: Exception) {
            // The file is deleted below whatever the system answers.
        }
        File(entry.path).delete()
        save(entries.filter { it.id != entry.id })
    }

    private fun save(list: List<DownloadEntry>) {
        entries = list
        Store.saveDownloads(list)
    }

    /** Current progress of every download, as the system reports it. */
    fun states(context: Context): List<DownloadState> {
        init()
        val list = entries
        if (list.isEmpty()) return emptyList()
        val known = HashMap<Long, Triple<Int, Long, Long>>()
        try {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            manager?.query(DownloadManager.Query().setFilterById(*list.map { it.id }.toLongArray()))?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val statusColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val doneColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                while (cursor.moveToNext()) {
                    val status = when (cursor.getInt(statusColumn)) {
                        DownloadManager.STATUS_SUCCESSFUL -> DONE
                        DownloadManager.STATUS_FAILED -> FAILED
                        DownloadManager.STATUS_RUNNING -> RUNNING
                        else -> PENDING
                    }
                    known[cursor.getLong(idColumn)] = Triple(status, cursor.getLong(doneColumn), cursor.getLong(totalColumn))
                }
            }
        } catch (e: Exception) {
            // Reported as failed below: nothing is known about them.
        }
        return list.map { entry ->
            val state = known[entry.id]
            val size = File(entry.path).length()
            when {
                state != null -> DownloadState(entry, state.first, state.second, state.third)
                // The system forgot it (cleared by the user): a complete file is still usable.
                size > 0 -> DownloadState(entry, DONE, size, size)
                else -> DownloadState(entry, FAILED, 0, 0)
            }
        }
    }

    /** Items that have a finished copy on the device are played from it. */
    fun resolve(context: Context, queue: List<PlayItem>): List<PlayItem> {
        init()
        if (entries.isEmpty() || queue.none { has(it.key) }) return queue
        val finished = states(context).filter { it.status == DONE && File(it.entry.path).exists() }.associateBy { it.entry.key }
        return queue.map { item ->
            finished[item.key]?.let { item.copy(url = Uri.fromFile(File(it.entry.path)).toString()) } ?: item
        }
    }

    fun freeSpace(context: Context): Long = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.usableSpace ?: 0
}
