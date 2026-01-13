package com.phoneunison.mobile.protocol

import java.util.UUID

/**
 * Represents a protocol message between Android and Windows PC.
 */
data class Message(
    val type: String,
    val data: Map<String, Any?>? = null,
    val id: String = UUID.randomUUID().toString(),
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        // Message types
        const val HEARTBEAT = "HEARTBEAT"
        const val PAIRING_REQUEST = "PAIRING_REQUEST"
        const val PAIRING_RESPONSE = "PAIRING_RESPONSE"
        const val NOTIFICATION = "NOTIFICATION"
        const val NOTIFICATION_ACTION = "NOTIFICATION_ACTION"
        const val SMS_LIST = "SMS_LIST"
        const val SMS_MESSAGES = "SMS_MESSAGES"
        const val SMS_SEND = "SMS_SEND"
        const val SMS_RECEIVED = "SMS_RECEIVED"
        const val CALL_STATE = "CALL_STATE"
        const val CALL_ACTION = "CALL_ACTION"
        const val CLIPBOARD = "CLIPBOARD"
        const val FILE_OFFER = "FILE_OFFER"
        const val FILE_ACCEPT = "FILE_ACCEPT"
        const val FILE_CHUNK = "FILE_CHUNK"
        const val FILE_COMPLETE = "FILE_COMPLETE"
        const val ERROR = "ERROR"
    }
}
