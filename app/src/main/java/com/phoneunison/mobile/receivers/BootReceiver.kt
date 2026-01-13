package com.phoneunison.mobile.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.phoneunison.mobile.services.ConnectionService

/**
 * Starts the connection service when device boots.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Log.i(TAG, "Boot completed, checking auto-start setting")
            
            // Check if auto-start is enabled in preferences
            val prefs = context.getSharedPreferences("phoneunison_prefs", Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean("auto_start", false)
            
            if (autoStart) {
                Log.i(TAG, "Starting connection service")
                val serviceIntent = Intent(context, ConnectionService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
