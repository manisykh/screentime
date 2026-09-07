package com.manisykh.screenrest.notification

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
import com.manisykh.screenrest.MainActivity
import com.manisykh.screenrest.R

enum class NotificationDeliveryResult(
    val delivered: Boolean,
    val diagnostic: String,
) {
    Delivered(true, ""),
    PermissionDenied(false, "notification permission denied"),
    NotificationsDisabled(false, "app notifications disabled"),
    ChannelDisabled(false, "parent request notification channel disabled"),
    Failed(false, "notification manager rejected the request"),
}

class UsageNotificationHelper(
    private val context: Context,
) {
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            "ScreenRest Alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun ensureParentRequestChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            PARENT_REQUEST_CHANNEL_ID,
            "부모 승인 요청",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "자녀 기기의 사용 시간 승인 요청과 처리 결과"
            enableVibration(true)
            setShowBadge(true)
        }
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
            .setSmallIcon(R.mipmap.notification_icon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(context)
                .notify(message.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the readiness check and notify().
        }
    }

    fun showParentRequestAlert(
        requestId: String,
        title: String,
        message: String,
    ): NotificationDeliveryResult {
        ensureParentRequestChannel()
        val readiness = parentRequestNotificationReadiness()
        if (readiness != NotificationDeliveryResult.Delivered) {
            return readiness
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            requestId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_PARENT_REQUESTS, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, PARENT_REQUEST_CHANNEL_ID)
            .setSmallIcon(R.mipmap.notification_icon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context)
                .notify(parentRequestNotificationId(requestId), notification)
            NotificationDeliveryResult.Delivered
        } catch (_: SecurityException) {
            NotificationDeliveryResult.PermissionDenied
        } catch (_: RuntimeException) {
            NotificationDeliveryResult.Failed
        }
    }

    fun cancelParentRequestAlert(requestId: String) {
        if (requestId.isBlank()) {
            return
        }
        NotificationManagerCompat.from(context)
            .cancel(parentRequestNotificationId(requestId))
    }

    fun showRemoteDecisionAlert(
        requestId: String,
        title: String,
        message: String,
    ): NotificationDeliveryResult {
        ensureParentRequestChannel()
        val readiness = parentRequestNotificationReadiness()
        if (readiness != NotificationDeliveryResult.Delivered) {
            return readiness
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            requestId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, PARENT_REQUEST_CHANNEL_ID)
            .setSmallIcon(R.mipmap.notification_icon)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context)
                .notify(parentRequestNotificationId(requestId), notification)
            NotificationDeliveryResult.Delivered
        } catch (_: SecurityException) {
            NotificationDeliveryResult.PermissionDenied
        } catch (_: RuntimeException) {
            NotificationDeliveryResult.Failed
        }
    }

    fun parentRequestNotificationReadiness(): NotificationDeliveryResult {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return NotificationDeliveryResult.PermissionDenied
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return NotificationDeliveryResult.NotificationsDisabled
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ensureParentRequestChannel()
            val channel = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(PARENT_REQUEST_CHANNEL_ID)
            if (channel == null || channel.importance == NotificationManager.IMPORTANCE_NONE) {
                return NotificationDeliveryResult.ChannelDisabled
            }
        }
        return NotificationDeliveryResult.Delivered
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
        private const val PARENT_REQUEST_CHANNEL_ID = "parent_requests_v1"

        private fun parentRequestNotificationId(requestId: String): Int {
            return 0x53000000 xor requestId.hashCode()
        }
    }
}
