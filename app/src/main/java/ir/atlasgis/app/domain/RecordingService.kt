package ir.atlasgis.app.domain

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ir.atlasgis.app.MainActivity
import ir.atlasgis.app.R
import ir.atlasgis.app.core.formatDistance
import ir.atlasgis.app.core.formatDuration
import ir.atlasgis.app.data.RecordingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the process (and the GPS listener owned by [RecordingEngine]) alive while a
 * track is being recorded. Android suspends background location updates for an app
 * without a foreground service, so without this a screen-off hike would be lost
 * after the first few hundred metres.
 *
 * The service owns no recording state itself: it mirrors [RecordingEngine.recording]
 * into an ongoing notification and forwards the notification buttons back to the
 * engine, so the UI and the notification can never disagree.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var engine: RecordingEngine

    override fun onCreate() {
        super.onCreate()
        engine = RecordingEngine.get(this)
        createChannel()
        startForegroundCompat(buildNotification(engine.recording.value))
        scope.launch {
            engine.recording.collectLatest { state ->
                if (state.active) {
                    notificationManager()?.notify(NOTIFICATION_ID, buildNotification(state))
                } else {
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> engine.pause()
            ACTION_RESUME -> engine.resume()
            ACTION_STOP -> engine.stop()
            ACTION_START -> Unit
        }
        // A START_STICKY restart brings no intent: if the engine has no session left
        // there is nothing to keep alive, otherwise the notification is refreshed by
        // the collector above.
        if (!engine.isActive) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        engine.stopLocationIfIdle()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(state: RecordingState): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (state.active) {
            buildString {
                append(formatDistance(state.distanceMeters))
                append(" · ")
                append(formatDuration(state.movingSeconds))
                if (state.paused) append(" · متوقف")
            }
        } else {
            "ثبت مسیر پایان یافت"
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_route)
            .setContentTitle(state.name.ifBlank { "ثبت مسیر" })
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(state.active)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (state.active) {
            if (state.paused) {
                builder.addAction(action(ACTION_RESUME, "ادامه", 1))
            } else {
                builder.addAction(action(ACTION_PAUSE, "توقف موقت", 2))
            }
            builder.addAction(action(ACTION_STOP, "پایان و ذخیره", 3))
        }
        return builder.build()
    }

    private fun action(action: String, label: String, requestCode: Int): NotificationCompat.Action {
        val intent = Intent(this, RecordingService::class.java).setAction(action)
        val pending = PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(0, label, pending).build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = notificationManager() ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ثبت مسیر",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "وضعیت ثبت مسیر در پس‌زمینه"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager? =
        getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    companion object {
        private const val CHANNEL_ID = "atlasgis_recording"
        private const val NOTIFICATION_ID = 4711

        const val ACTION_START = "ir.atlasgis.app.action.START_RECORDING"
        const val ACTION_PAUSE = "ir.atlasgis.app.action.PAUSE_RECORDING"
        const val ACTION_RESUME = "ir.atlasgis.app.action.RESUME_RECORDING"
        const val ACTION_STOP = "ir.atlasgis.app.action.STOP_RECORDING"

        /** Starts (or keeps) the foreground service; safe to call repeatedly. */
        fun start(context: Context) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_START)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }
    }
}
