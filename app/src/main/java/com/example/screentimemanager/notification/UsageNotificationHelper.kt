package com.example.screentimemanager.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.screentimemanager.MainActivity
import com.example.screentimemanager.R

class UsageNotificationHelper(
    private val context: Context,
) {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Time Alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun showPolicyAlert(title: String, message: String) {
        if (!canPostNotifications()) {
            return
        }

        ensureChannel()
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(message.hashCode(), notification)
    }

    fun canPostNotifications(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return isChannelEnabled()
        }
        val hasRuntimePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        return hasRuntimePermission && isChannelEnabled()
    }

    private fun isChannelEnabled(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return true
        }
        ensureChannel()
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_ID)
        return channel?.importance != NotificationManager.IMPORTANCE_NONE
    }

    companion object {
        private const val CHANNEL_ID = "usage_alerts"
    }
}
