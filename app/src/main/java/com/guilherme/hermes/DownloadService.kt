package com.guilherme.hermes

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps downloads going with the screen off or in another app, showing the progress
 * in a notification. Stops by itself when there is nothing left to download.
 */
@android.annotation.SuppressLint("MissingPermission")
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        channels(this)
        ServiceCompat.startForeground(this, ONGOING, progress(null, 0), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        wake = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hermes:download").apply { acquire(3 * 3600_000L) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (job == null) job = scope.launch {
            Engine.items.collectLatest { list ->
                val active = list.filter { it.active }
                if (active.isEmpty()) {
                    delay(800) // a moment, in case the next one is about to start
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val now = active.lastOrNull { it.state == State.Running } ?: active.last()
                    if (canNotify(this@DownloadService)) {
                        try { NotificationManagerCompat.from(this@DownloadService).notify(ONGOING, progress(now, active.size)) } catch (_: SecurityException) {}
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        try { wake?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun progress(item: Item?, count: Int): Notification {
        val b = NotificationCompat.Builder(this, CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_notif)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp(this))
            .setColor(0xFF8FD9AE.toInt())
        if (item == null) return b.setContentTitle("Preparando o download…").setProgress(0, 0, true).build()
        val more = if (count > 1) " · mais ${count - 1} na fila" else ""
        return b.setContentTitle(item.shownTitle.take(80))
            .setContentText(item.message + more)
            .setProgress(100, item.pct.toInt(), item.state != State.Running || item.pct <= 0f)
            .build()
    }

    companion object {
        private const val CH_PROGRESS = "progress"
        private const val CH_DONE = "done"
        private const val ONGOING = 1

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
            } catch (_: Exception) {
                // Android may refuse when the app is in the background; the download still runs while the app is open
            }
        }

        fun channels(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_PROGRESS, "Baixando", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CH_DONE, "Downloads prontos", NotificationManager.IMPORTANCE_DEFAULT))
        }

        private fun canNotify(ctx: Context) =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
                android.os.Build.VERSION.SDK_INT < 33

        private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        @android.annotation.SuppressLint("MissingPermission")
        /** "Pronto: <title>" — tapping it opens the file. */
        fun notifyDone(ctx: Context, item: Item) {
            if (!canNotify(ctx)) return
            channels(ctx)
            val file = item.files.firstOrNull()
            val tap = if (file != null && item.files.size == 1) {
                val view = Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse(file.uri), file.mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                PendingIntent.getActivity(ctx, item.id.toInt(), view, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            } else openApp(ctx)
            val n = NotificationCompat.Builder(ctx, CH_DONE)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Pronto: " + item.shownTitle.take(80))
                .setContentText(if (file?.mime?.startsWith("video") == true) "Salvo em Filmes › Swallow" else "Salvo em Música › Swallow")
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setColor(0xFF8FD9AE.toInt())
                .build()
            try { NotificationManagerCompat.from(ctx).notify(item.id.toInt(), n) } catch (_: SecurityException) {}
        }
    }
}
