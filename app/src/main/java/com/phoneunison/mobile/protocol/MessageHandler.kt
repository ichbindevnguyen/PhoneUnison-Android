package com.phoneunison.mobile.protocol

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.phoneunison.mobile.services.ConnectionService

class MessageHandler(private val connectionService: ConnectionService) {

    companion object {
        private const val TAG = "MessageHandler"
    }

    fun handleMessage(message: Message) {
        Log.d(TAG, "Handling message: ${message.type}")

        when (message.type) {
            Message.SMS_LIST -> handleSmsList(message)
            Message.SMS_MESSAGES -> handleSmsMessages(message)
            Message.SMS_SEND -> handleSmsSend(message)
            Message.CALL_ACTION -> handleCallAction(message)
            Message.CLIPBOARD -> handleClipboard(message)
            Message.FILE_OFFER -> handleFileOffer(message)
            Message.FILE_ACCEPT -> handleFileAccept(message)
            Message.FILE_CHUNK -> handleFileChunk(message)
            Message.NOTIFICATION_ACTION -> handleNotificationAction(message)
            else -> Log.w(TAG, "Unhandled message type: ${message.type}")
        }
    }

    private fun handleSmsList(message: Message) {
        try {
            val conversations = connectionService.smsHandler.getConversations()
            val response = Message(Message.SMS_LIST, mapOf("conversations" to conversations))
            connectionService.sendMessage(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling SMS_LIST", e)
        }
    }

    private fun handleSmsMessages(message: Message) {
        try {
            val threadId = message.data?.get("threadId") as? String ?: return
            val messages = connectionService.smsHandler.getMessages(threadId)
            val response =
                    Message(
                            Message.SMS_MESSAGES,
                            mapOf("threadId" to threadId, "messages" to messages)
                    )
            connectionService.sendMessage(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling SMS_MESSAGES", e)
        }
    }

    private fun handleSmsSend(message: Message) {
        try {
            val address = message.data?.get("address") as? String ?: return
            val body = message.data?.get("body") as? String ?: return

            Log.d(TAG, "Sending SMS to $address")

            val smsManager =
                    connectionService.getSystemService(Context.TELEPHONY_SERVICE) as? SmsManager
                            ?: SmsManager.getDefault()

            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(address, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(address, null, body, null, null)
            }

            Log.i(TAG, "SMS sent successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS", e)
            sendError("SMS_SEND_FAILED", e.message ?: "Unknown error")
        }
    }

    private fun handleCallAction(message: Message) {
        val action = message.data?.get("action") as? String ?: return

        Log.d(TAG, "Call action: $action")

        when (action) {
            "reject" -> {
                try {
                    val telecomManager =
                            connectionService.getSystemService(Context.TELECOM_SERVICE) as
                                    android.telecom.TelecomManager

                    @Suppress("DEPRECATION") telecomManager.endCall()

                    Log.i(TAG, "Call rejected")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reject call", e)
                }
            }
            "answer" -> {
                try {
                    val telecomManager =
                            connectionService.getSystemService(Context.TELECOM_SERVICE) as
                                    android.telecom.TelecomManager

                    telecomManager.acceptRingingCall()

                    Log.i(TAG, "Call answered")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to answer call", e)
                }
            }
        }
    }

    private fun handleClipboard(message: Message) {
        try {
            val content = message.data?.get("content") as? String ?: return
            val contentType = message.data?.get("contentType") as? String ?: "text/plain"

            if (contentType == "text/plain") {
                val clipboardManager =
                        connectionService.getSystemService(Context.CLIPBOARD_SERVICE) as
                                android.content.ClipboardManager

                val clip = android.content.ClipData.newPlainText("PhoneUnison", content)
                clipboardManager.setPrimaryClip(clip)

                Log.d(TAG, "Clipboard set: ${content.take(50)}...")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set clipboard", e)
        }
    }

    private fun handleFileOffer(message: Message) {
        val fileName = message.data?.get("fileName") as? String
        val fileSize = message.data?.get("fileSize") as? Long

        Log.d(TAG, "File offer: $fileName ($fileSize bytes)")

        val response =
                Message(
                        Message.FILE_ACCEPT,
                        mapOf("transferId" to message.data?.get("transferId"), "accepted" to true)
                )
        connectionService.sendMessage(response)
    }

    private fun handleFileAccept(message: Message) {
        val uriString = message.data?.get("uri") as? String ?: return
        val fileName = message.data?.get("fileName") as? String ?: "file"

        Log.i(TAG, "File accepted, starting upload")
        connectionService.fileHandler.uploadFile(uriString, fileName)
    }

    private fun handleFileChunk(message: Message) {}

    private fun handleNotificationAction(message: Message) {
        val notificationId = message.data?.get("notificationId") as? String
        val actionId = message.data?.get("actionId") as? String

        Log.d(TAG, "Notification action: $actionId on $notificationId")
    }

    private fun sendError(code: String, errorMessage: String) {
        val message = Message(Message.ERROR, mapOf("code" to code, "message" to errorMessage))
        connectionService.sendMessage(message)
    }
}
