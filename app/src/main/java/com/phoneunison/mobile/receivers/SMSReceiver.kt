package com.phoneunison.mobile.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.phoneunison.mobile.protocol.Message
import com.phoneunison.mobile.services.ConnectionService

/**
 * Receives incoming SMS messages and forwards them to the PC.
 */
class SMSReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SMSReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!ConnectionService.isConnected) return
        
        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            
            for (sms in messages) {
                val sender = sms.displayOriginatingAddress ?: "Unknown"
                val body = sms.displayMessageBody ?: ""
                val timestamp = sms.timestampMillis
                
                Log.d(TAG, "SMS received from $sender")
                
                val data = mapOf(
                    "address" to sender,
                    "body" to body,
                    "timestamp" to timestamp,
                    "type" to "incoming"
                )
                
                val message = Message(Message.SMS_RECEIVED, data)
                
                // Send to PC via broadcast
                val sendIntent = Intent("com.phoneunison.SEND_MESSAGE").apply {
                    putExtra("message", com.google.gson.Gson().toJson(message))
                }
                context.sendBroadcast(sendIntent)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS", e)
        }
    }
}
