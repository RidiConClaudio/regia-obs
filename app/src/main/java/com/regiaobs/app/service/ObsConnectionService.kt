package com.regiaobs.app.service

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
import com.regiaobs.app.MainActivity
import com.regiaobs.app.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ObsServiceBridge {
    private val _disconnectEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val disconnectEvents = _disconnectEvents.asSharedFlow()

    fun requestDisconnect() {
        _disconnectEvents.tryEmit(Unit)
    }
}

class ObsConnectionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_DISCONNECT -> {
                ObsServiceBridge.requestDisconnect()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        ensureChannel()
        val serverName = intent?.getStringExtra(EXTRA_SERVER_NAME).orEmpty().ifBlank { "OBS Studio" }
        val statusText = intent?.getStringExtra(EXTRA_STATUS_TEXT).orEmpty().ifBlank { "Connected and ready" }
        val notification = buildNotification(serverName, statusText)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(serverName: String, statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, ObsConnectionService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPending = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("RegiaOBS • $serverName")
            .setContentText(statusText)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openPending)
            .addAction(0, "Disconnect", disconnectPending)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "regia_obs_connection"
        private const val NOTIFICATION_ID = 4455
        private const val ACTION_START = "com.regiaobs.app.action.START"
        private const val ACTION_STOP = "com.regiaobs.app.action.STOP"
        private const val ACTION_DISCONNECT = "com.regiaobs.app.action.DISCONNECT"
        private const val EXTRA_SERVER_NAME = "server_name"
        private const val EXTRA_STATUS_TEXT = "status_text"

        fun updateOrStart(context: Context, serverName: String, statusText: String) {
            val intent = Intent(context, ObsConnectionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SERVER_NAME, serverName)
                putExtra(EXTRA_STATUS_TEXT, statusText)
            }
            runCatching {
                context.startForegroundService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ObsConnectionService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching {
                context.stopService(intent)
            }
        }
    }
}
