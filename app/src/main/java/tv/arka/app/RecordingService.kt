package tv.arka.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import tv.arka.app.data.Http
import tv.arka.app.data.RecordingEntry
import tv.arka.app.data.Recordings
import tv.arka.app.data.Store
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Records one live MPEG-TS channel to a file, as a foreground service so that it goes on when the
 * app is left. It opens its own connection to the provider: an account limited to one connection
 * cannot record and watch at the same time.
 */
class RecordingService : Service() {
    @Volatile
    private var stopRequested = false
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRequested = true
            if (worker == null) stopSelf()
            return START_NOT_STICKY
        }
        val url = intent?.getStringExtra(EXTRA_URL)
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        val minutes = intent?.getIntExtra(EXTRA_MINUTES, 0) ?: 0
        // A service started for the foreground must show its notification even if it stops at once.
        showNotification(title)
        if (url.isNullOrBlank() || worker != null) {
            if (worker == null) stopSelf()
            return START_NOT_STICKY
        }
        record(url, title, minutes)
        return START_NOT_STICKY
    }

    private fun showNotification(title: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Enregistrements", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_arka_enregistrer)
            .setContentTitle("Enregistrement en cours")
            .setContentText(title)
            .setOngoing(true)
            .addAction(0, "Arrêter", stop)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun record(url: String, title: String, minutes: Int) {
        Store.init(applicationContext)
        val folder = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        val now = System.currentTimeMillis()
        val id = now.toString()
        val file = File(folder, "rec_$id.ts")
        val entry = RecordingEntry(id, title, file.absolutePath, now, if (minutes > 0) now + minutes * 60_000L else 0)
        Recordings.add(entry)
        Recordings.activeId = id
        Recordings.activeBytes = 0
        Recordings.lastError = null
        val main = Handler(Looper.getMainLooper())
        worker = thread(name = "arkatv-recording") {
            var error: String? = null
            try {
                Http.open(url).use { response ->
                    val input = response.body?.byteStream() ?: throw IOException("réponse vide")
                    FileOutputStream(file).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        var rounds = 0
                        while (!stopRequested) {
                            if (entry.plannedEnd > 0 && System.currentTimeMillis() >= entry.plannedEnd) break
                            val count = input.read(buffer)
                            if (count < 0) break
                            out.write(buffer, 0, count)
                            total += count
                            Recordings.activeBytes = total
                            rounds++
                            if (rounds % 200 == 0 && folder.usableSpace < MIN_FREE_BYTES) {
                                error = "Enregistrement arrêté : plus assez d'espace."
                                break
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                error = "Enregistrement interrompu : le flux s'est arrêté."
            }
            val problem = error
            main.post {
                Recordings.activeId = null
                Recordings.lastError = problem
                // An empty file is not a recording.
                if (file.length() == 0L) Recordings.remove(entry)
                worker = null
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        stopRequested = true
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "recordings"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "tv.arka.app.STOP_RECORDING"
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MINUTES = "minutes"
        private const val MIN_FREE_BYTES = 300L * 1024 * 1024

        /** [minutes] 0 records until [stop] is called. */
        fun start(context: Context, url: String, title: String, minutes: Int) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RecordingService::class.java)
                    .putExtra(EXTRA_URL, url).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_MINUTES, minutes),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_STOP))
        }
    }
}
