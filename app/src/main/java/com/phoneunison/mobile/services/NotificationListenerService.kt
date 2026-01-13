package com.phoneunison.mobile.services

import android.content.ComponentName
import android.content.Intent
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.gson.Gson
import com.phoneunison.mobile.protocol.Message
import android.service.notification.NotificationListenerService as AndroidNotificationListenerService

/**
 * Listens for notifications and forwards them to the connected PC.
 */
class NotificationListenerService : AndroidNotificationListenerService() {

    companion object {
        private const val TAG = "NotificationListener"
        private val gson = Gson()
        
        // Apps to ignore (system apps, etc.)
        private val ignoredPackages = setOf(
            "com.phoneunison.mobile", // Don't forward our own notifications
            "android",
            "com.android.systemui"
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "Notification listener disconnected")
        
        // Try to rebind
        requestRebind(ComponentName(this, NotificationListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!ConnectionService.isConnected) return
        if (sbn.packageName in ignoredPackages) return
        
        try {
            val notification = sbn.notification
            val extras = notification.extras
            
            val title = extras.getCharSequence("android.title")?.toString() ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""
            val appName = getAppName(sbn.packageName)
            
            // Get app icon as base64 (simplified - would need actual implementation)
            val iconBase64 = "" // TODO: Extract and encode icon
            
            val data = mapOf(
                "id" to sbn.key,
                "packageName" to sbn.packageName,
                "appName" to appName,
                "title" to title,
                "text" to text,
                "icon" to iconBase64,
                "timestamp" to sbn.postTime,
                "actions" to getNotificationActions(notification)
            )
            
            val message = Message(Message.NOTIFICATION, data)
            
            // Get connection service and send
            sendNotificationToPC(message)
            
            Log.d(TAG, "Forwarded notification from $appName: $title")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Optionally notify PC that notification was dismissed
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun getNotificationActions(notification: android.app.Notification): List<Map<String, String>> {
        val actions = notification.actions ?: return emptyList()
        
        return actions.mapIndexed { index, action ->
            mapOf(
                "id" to index.toString(),
                "title" to (action.title?.toString() ?: "")
            )
        }
    }

    private fun sendNotificationToPC(message: Message) {
        // Send via broadcast to ConnectionService
        val intent = Intent("com.phoneunison.SEND_MESSAGE").apply {
            putExtra("message", gson.toJson(message))
        }
        sendBroadcast(intent)
    }
}
