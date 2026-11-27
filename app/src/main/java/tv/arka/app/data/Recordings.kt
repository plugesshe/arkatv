package tv.arka.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** A live channel recorded to a file. [plannedEnd] is 0 when the recording runs until it is stopped. */
class RecordingEntry(val id: String, val title: String, val path: String, val startedAt: Long, val plannedEnd: Long)

/** Recordings kept on the device, and what the recording service is doing right now. */
object Recordings {
    var entries by mutableStateOf(listOf<RecordingEntry>())
        private set

    /** Id of the recording in progress, if any. One at a time. */
    var activeId by mutableStateOf<String?>(null)
    var activeBytes by mutableLongStateOf(0L)
    var lastError by mutableStateOf<String?>(null)
    private var loaded = false

    fun init() {
        if (loaded) return
        loaded = true
        entries = Store.loadRecordings().filter { File(it.path).exists() }
    }

    fun add(entry: RecordingEntry) {
        init()
        entries = listOf(entry) + entries
        Store.saveRecordings(entries)
    }

    fun remove(entry: RecordingEntry) {
        File(entry.path).delete()
        entries = entries.filter { it.id != entry.id }
        Store.saveRecordings(entries)
    }
}

/** Channels chosen for the multi-view screen (two to four). */
object MultiviewPicks {
    const val MAX = 4
    var channels by mutableStateOf(listOf<PlayItem>())

    fun toggle(item: PlayItem) {
        channels = when {
            channels.any { it.key == item.key } -> channels.filter { it.key != item.key }
            channels.size < MAX -> channels + item
            else -> channels
        }
    }
}
