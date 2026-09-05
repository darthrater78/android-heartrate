package com.scrivtech.heartrate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Keeps the app in the foreground for the duration of a heart rate session.
 *
 * The BLE connection itself lives in [BleHeartRateManager], owned by the ViewModel — this
 * service holds no Bluetooth state. Its only job is to stop the process being frozen or
 * killed by Doze and background execution limits while a session is running, which is what
 * used to end long sessions once the screen went off.
 *
 * Because the manager is Activity-scoped, this does not survive the user dismissing the app
 * from Recents; it covers screen-off and backgrounding, not full task removal.
 */
class HeartRateSessionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME)
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(deviceName))
        // The Activity starts and stops this explicitly; there is no state worth having the
        // system recreate the service for.
        return START_NOT_STICKY
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active session",
            // Low: the notification is a required foreground-service affordance, not an alert.
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while a heart rate session is running"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(deviceName: String?): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Heart rate session running")
            .setContentText(deviceName?.let { "Connected to $it" } ?: "Monitoring heart rate")
            .setSmallIcon(R.drawable.ic_notification_heart)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "heart_rate_session"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_DEVICE_NAME = "device_name"

        fun start(context: Context, deviceName: String?) {
            val intent = Intent(context, HeartRateSessionService::class.java)
                .putExtra(EXTRA_DEVICE_NAME, deviceName)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HeartRateSessionService::class.java))
        }
    }
}
