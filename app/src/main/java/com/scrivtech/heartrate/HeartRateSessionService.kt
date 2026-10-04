package com.scrivtech.heartrate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.scrivtech.heartrate.data.maxHrForAge
import com.scrivtech.heartrate.data.zoneFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Keeps the app in the foreground for the duration of a heart rate session, and mirrors the
 * live reading into its notification so it can be read with the app in the background.
 *
 * The BLE connection lives in [BleHeartRateManager], owned by [HeartRateApp] — this service
 * holds no Bluetooth state. It stops the process being frozen or killed by Doze and
 * background execution limits, and stops itself once the session is over.
 *
 * On Android 16 QPR1+ the notification asks to be promoted to a Live Update, which puts the BPM
 * in a chip in the status bar. The user can turn that off per app in system settings, and
 * the notification is unchanged apart from the chip.
 */
class HeartRateSessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null
    private var lastStartId = 0

    private val bleManager get() = (application as HeartRateApp).bleManager
    private val storage get() = (application as HeartRateApp).storage

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            // Same path as the Disconnect button: notifications are switched off at the
            // watch before the link drops, so it stops streaming.
            bleManager.disconnect()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        lastStartId = startId
        createChannel()
        // Every startForegroundService must be answered with startForeground, even when the
        // session has already ended by the time this runs; the observer then stops us.
        startForeground(NOTIFICATION_ID, buildNotification(currentContent()))
        if (observer == null) observer = scope.launch { observeSession() }
        // The session cannot outlive the process that holds the BLE connection, so there is
        // nothing worth having the system recreate the service for.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun observeSession() {
        val notifications = getSystemService(NotificationManager::class.java)
        combine(
            bleManager.state,
            bleManager.heartRate,
            bleManager.connectedDeviceName,
            bleManager.reconnectAttemptState
        ) { _, _, _, _ -> currentContent() }
            // The watch sends about one reading a second; only post when the text changes.
            .distinctUntilChanged()
            .collect { content ->
                if (content == null) {
                    // stopSelf(id) is ignored if a newer session has started us again since.
                    stopSelf(lastStartId)
                } else {
                    notifications.notify(NOTIFICATION_ID, buildNotification(content))
                }
            }
    }

    /** What the notification should say right now, or null once the session is over. */
    private fun currentContent(): Content? {
        val state = bleManager.state.value
        if (!state.isInSession) return null
        val device = bleManager.connectedDeviceName.value ?: "heart rate monitor"
        val bpm = bleManager.heartRate.value

        return when {
            state == ConnectionState.CONNECTING -> Content("Connecting to $device…", null, null)
            state == ConnectionState.RECONNECTING -> Content(
                "Reconnecting to $device…",
                "Attempt ${bleManager.reconnectAttemptState.value} of " +
                    "${BleHeartRateManager.MAX_RECONNECT_ATTEMPTS}",
                null
            )
            bpm == null -> Content("Waiting for heart rate…", device, null)
            else -> {
                val zone = storage.getAge()?.let { zoneFor(bpm, maxHrForAge(it)).displayName }
                Content("$bpm bpm", listOfNotNull(zone, device).joinToString(" · "), "$bpm bpm")
            }
        }
    }

    private data class Content(val title: String, val text: String?, val chip: String?)

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active session",
            // Low: the reading updates every second, which must never make a sound or
            // vibrate. Live Updates only need the channel to be above MIN.
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Live heart rate while a session is running"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(content: Content?): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        val disconnect = PendingIntent.getService(
            this,
            1,
            Intent(this, HeartRateSessionService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(content?.title ?: "Heart rate session")
            .setContentText(content?.text)
            .setSmallIcon(R.drawable.ic_notification_heart)
            .setContentIntent(openApp)
            .addAction(
                Notification.Action.Builder(null, "Disconnect", disconnect).build()
            )
            .setCategory(Notification.CATEGORY_WORKOUT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // Show it at once rather than after the up-to-10s foreground service delay.
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)

        // Live Updates arrived in Android 16 QPR1 (36.1); SDK_INT_FULL only exists from 36.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
            Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1
        ) {
            builder.setRequestPromotedOngoing(true)
            content?.chip?.let { builder.setShortCriticalText(it) }
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "heart_rate_session"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_DISCONNECT = "com.scrivtech.heartrate.action.DISCONNECT"

        /** Returns false if the system refused to start it. */
        fun start(context: Context): Boolean = runCatching {
            context.startForegroundService(Intent(context, HeartRateSessionService::class.java))
        }.onFailure {
            // ForegroundServiceStartNotAllowedException if the app is already in the
            // background. The session still runs; it is just not protected from Doze.
            android.util.Log.w("HeartRateMirror", "Could not start session service", it)
        }.isSuccess
    }
}
