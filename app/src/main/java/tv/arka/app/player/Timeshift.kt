package tv.arka.app.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import tv.arka.app.data.Http
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import kotlin.concurrent.thread

/**
 * Keeps a live MPEG-TS stream on disk while the picture is paused, so that playback resumes where
 * it stopped instead of jumping to the present.
 *
 * One thread reads the network without ever waiting for the player and appends to small files;
 * the player reads those files in order and each file is deleted once it has been read. Disk use is
 * therefore the delay between the two, never the whole viewing session. When the delay would
 * exceed [maxBytes] the recording stops; the player then reaches the end and goes back to live.
 */
class TimeshiftBuffer(private val folder: File, private val url: String, private val maxBytes: Long) {
    private val lock = Object()
    private var written = 0L
    private var consumed = 0L
    private var oldestChunk = 0
    private var finished = false
    private var failure: IOException? = null
    private var started = false

    @Volatile
    private var stopped = false
    private var readerFile: RandomAccessFile? = null
    private var readerChunk = -1

    private fun chunkFile(index: Int) = File(folder, "chunk_$index.ts")

    fun ensureStarted() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        folder.mkdirs()
        folder.listFiles()?.forEach { it.delete() }
        thread(name = "arkatv-timeshift", isDaemon = true) { record() }
    }

    private fun record() {
        var out: FileOutputStream? = null
        try {
            Http.open(url).use { response ->
                val input = response.body?.byteStream() ?: throw IOException("réponse vide")
                val buffer = ByteArray(64 * 1024)
                var chunk = -1
                var total = 0L
                while (!stopped) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    var offset = 0
                    while (offset < count) {
                        val index = (total / CHUNK_BYTES).toInt()
                        if (index != chunk) {
                            out?.close()
                            out = FileOutputStream(chunkFile(index), true)
                            chunk = index
                        }
                        val room = (CHUNK_BYTES - total % CHUNK_BYTES).toInt()
                        val length = minOf(room, count - offset)
                        out?.write(buffer, offset, length)
                        offset += length
                        total += length
                        synchronized(lock) {
                            written = total
                            lock.notifyAll()
                        }
                    }
                    val behind = synchronized(lock) { written - consumed }
                    if (behind > maxBytes) break
                }
            }
        } catch (e: IOException) {
            synchronized(lock) { failure = e }
        } finally {
            try {
                out?.close()
            } catch (e: IOException) {
                // Nothing more to write anyway.
            }
            synchronized(lock) {
                finished = true
                lock.notifyAll()
            }
        }
    }

    /** First byte still on disk: an earlier position can no longer be read. */
    fun oldestPosition(): Long = synchronized(lock) { oldestChunk.toLong() * CHUNK_BYTES }

    /** Blocks until bytes exist at [position]. Returns -1 once the recording is over and fully read. */
    fun read(position: Long, target: ByteArray, offset: Int, length: Int): Int {
        val available = synchronized(lock) {
            while (position >= written) {
                if (finished) {
                    val error = failure
                    if (error != null && written == 0L) throw error
                    return -1
                }
                if (stopped) return -1
                try {
                    lock.wait(500)
                } catch (e: InterruptedException) {
                    throw InterruptedIOException()
                }
            }
            written - position
        }
        val index = (position / CHUNK_BYTES).toInt()
        val within = position % CHUNK_BYTES
        val wanted = minOf(available, CHUNK_BYTES - within, length.toLong()).toInt()
        if (index != readerChunk) {
            readerFile?.close()
            readerFile = RandomAccessFile(chunkFile(index), "r")
            readerChunk = index
        }
        val file = readerFile ?: throw IOException("tampon indisponible")
        file.seek(within)
        val count = file.read(target, offset, wanted)
        if (count <= 0) throw IOException("tampon illisible")
        synchronized(lock) {
            consumed = position + count
            // Files entirely behind the reader are no longer needed.
            while (oldestChunk < index) {
                chunkFile(oldestChunk).delete()
                oldestChunk++
            }
        }
        return count
    }

    fun stop() {
        stopped = true
        synchronized(lock) { lock.notifyAll() }
        try {
            readerFile?.close()
        } catch (e: IOException) {
            // Being discarded.
        }
        readerFile = null
        thread(name = "arkatv-timeshift-clean", isDaemon = true) {
            // Leave the recorder a moment to see the stop before its files disappear.
            try {
                Thread.sleep(1500)
            } catch (e: InterruptedException) {
                // Clean anyway.
            }
            if (folder.exists()) folder.listFiles()?.forEach { it.delete() }
        }
    }

    companion object {
        const val CHUNK_BYTES = 8L * 1024 * 1024
    }
}

/** Lets the player read a [TimeshiftBuffer] as if it were the network stream itself. */
@UnstableApi
class TimeshiftDataSource(private val buffer: TimeshiftBuffer) : BaseDataSource(true) {
    private var uri: Uri? = null
    private var position = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        buffer.ensureStarted()
        position = dataSpec.position
        if (position < buffer.oldestPosition()) throw IOException("cette partie du direct n'est plus conservée")
        opened = true
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val count = buffer.read(position, target, offset, length)
        if (count < 0) return C.RESULT_END_OF_INPUT
        position += count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}
