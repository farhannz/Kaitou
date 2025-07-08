package com.farhannz.kaitou.helpers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationHelper {

    const val PACKAGE_NAME = "com.farhannz.kaitou"
    const val OVERLAY_CHANNEL_ID = "overlay_service_channel"
    const val CAPTURE_CHANNEL_ID = "capture_service_channel"

    fun createNotificationChannels(context: Context) {
        val overlayChannel = NotificationChannel(
            OVERLAY_CHANNEL_ID,
            "Overlay Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows floating button"
        }

        val captureChannel = NotificationChannel(
            CAPTURE_CHANNEL_ID,
            "Screen Capture",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Handles screen captures"
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(overlayChannel)
        manager.createNotificationChannel(captureChannel)
    }
}