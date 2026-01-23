package com.phoneunison.mobile.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.phoneunison.mobile.protocol.Message
import com.phoneunison.mobile.services.ConnectionService

/** Monitors phone call state and forwards to PC. */
class CallStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallStateReceiver"

        @Volatile private var lastState = TelephonyManager.CALL_STATE_IDLE
        @Volatile private var lastNumber: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        if (!ConnectionService.isConnected) return

        try {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            val state =
                    when (stateStr) {
                        TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
                        TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
                        TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
                        else -> return
                    }

            // Avoid duplicate events
            if (state == lastState && number == lastNumber) return

            lastState = state
            if (number != null) lastNumber = number

            val stateString =
                    when (state) {
                        TelephonyManager.CALL_STATE_IDLE -> "idle"
                        TelephonyManager.CALL_STATE_RINGING -> "ringing"
                        TelephonyManager.CALL_STATE_OFFHOOK -> "offhook"
                        else -> "unknown"
                    }

            Log.d(TAG, "Call state: $stateString, number: $lastNumber")

            // Get contact name if available
            val contactName = lastNumber?.let { getContactName(context, it) }

            val data =
                    mapOf(
                            "state" to stateString,
                            "number" to (lastNumber ?: ""),
                            "contactName" to (contactName ?: "Unknown"),
                            "contactPhoto" to "" // TODO: Get contact photo
                    )

            val message = Message(Message.CALL_STATE, data)

            // Send to PC via broadcast
            val sendIntent =
                    Intent("com.phoneunison.SEND_MESSAGE").apply {
                        putExtra("message", com.google.gson.Gson().toJson(message))
                    }
            context.sendBroadcast(sendIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing call state", e)
        }
    }

    private fun getContactName(context: Context, phoneNumber: String): String? {
        return try {
            val uri =
                    android.net.Uri.withAppendedPath(
                            android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                            android.net.Uri.encode(phoneNumber)
                    )

            context.contentResolver.query(
                            uri,
                            arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                            null,
                            null,
                            null
                    )
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getString(0)
                        } else null
                    }
        } catch (e: Exception) {
            null
        }
    }
}
