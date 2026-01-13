package com.phoneunison.mobile

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * PhoneUnison Application class.
 * Handles application-wide initialization.
 */
class PhoneUnisonApp : Application() {

    companion object {
        private const val TAG = "PhoneUnisonApp"
        
        const val CHANNEL_ID_SERVICE = "phoneunison_service"
        const val CHANNEL_ID_NOTIFICATIONS = "phoneunison_notifications"
        const val CHANNEL_ID_CALLS = "phoneunison_calls"
        
        lateinit var instance: PhoneUnisonApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        Log.i(TAG, "PhoneUnison starting...")
        
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Service channel (for foreground service)
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "Connection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when PhoneUnison is connected to your PC"
                setShowBadge(false)
            }

            // Notifications channel (for mirrored notifications)
            val notificationsChannel = NotificationChannel(
                CHANNEL_ID_NOTIFICATIONS,
                "PC Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications from your connected PC"
            }

            // Calls channel (high priority for incoming calls)
            val callsChannel = NotificationChannel(
                CHANNEL_ID_CALLS,
                "Call Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming call notifications"
                enableVibration(true)
            }

            notificationManager.createNotificationChannels(
                listOf(serviceChannel, notificationsChannel, callsChannel)
            )
            
            Log.d(TAG, "Notification channels created")
        }
    }
}
