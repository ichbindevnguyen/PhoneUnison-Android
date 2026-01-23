package com.phoneunison.mobile.services

import android.content.Context
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log

class SmsHandler(private val context: Context) {

    companion object {
        private const val TAG = "SmsHandler"
    }

    fun getConversations(): List<Map<String, Any>> {
        val conversations = mutableListOf<Map<String, Any>>()
        val uri = Uri.parse("content://sms/conversations")
        val projection = arrayOf("thread_id", "msg_count", "snippet")

        try {
            val cursor = context.contentResolver.query(uri, projection, null, null, "date DESC")

            cursor?.use {
                val threadIdIdx = it.getColumnIndex("thread_id")
                val msgCountIdx = it.getColumnIndex("msg_count")
                val snippetIdx = it.getColumnIndex("snippet")

                while (it.moveToNext()) {
                    val threadId = it.getString(threadIdIdx)
                    val msgCount = it.getInt(msgCountIdx)
                    val snippet = it.getString(snippetIdx)

                    val details = getThreadDetailsWithTimestamp(threadId)

                    if (details != null) {
                        conversations.add(
                                mapOf(
                                        "threadId" to threadId,
                                        "msgCount" to msgCount,
                                        "snippet" to (snippet ?: ""),
                                        "address" to details.first,
                                        "contactName" to details.second,
                                        "timestamp" to details.third
                                )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading conversations", e)
        }

        return conversations
    }

    private fun getThreadDetailsWithTimestamp(threadId: String): Triple<String, String, Long>? {
        val uri = Uri.parse("content://sms")
        val projection = arrayOf("address", "date")
        val selection = "thread_id = ?"
        val selectionArgs = arrayOf(threadId)

        try {
            val cursor =
                    context.contentResolver.query(
                            uri,
                            projection,
                            selection,
                            selectionArgs,
                            "date DESC LIMIT 1"
                    )

            cursor?.use {
                if (it.moveToFirst()) {
                    val address = it.getString(it.getColumnIndexOrThrow("address"))
                    val timestamp = it.getLong(it.getColumnIndexOrThrow("date"))
                    val contactName = getContactName(address)
                    return Triple(address, contactName, timestamp)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting thread details for $threadId", e)
        }
        return null
    }

    private fun getContactName(phoneNumber: String?): String {
        if (phoneNumber.isNullOrEmpty()) return "Unknown"

        val uri =
                Uri.withAppendedPath(
                        android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                        Uri.encode(phoneNumber)
                )

        try {
            val cursor =
                    context.contentResolver.query(
                            uri,
                            arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                            null,
                            null,
                            null
                    )

            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getString(
                            it.getColumnIndexOrThrow(
                                    android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME
                            )
                    )
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return phoneNumber
    }

    fun getMessages(threadId: String): List<Map<String, Any>> {
        val messages = mutableListOf<Map<String, Any>>()
        val uri = Uri.parse("content://sms")
        val selection = "thread_id = ?"
        val selectionArgs = arrayOf(threadId)

        try {
            val cursor =
                    context.contentResolver.query(
                            uri,
                            null,
                            selection,
                            selectionArgs,
                            "date ASC" // Oldest first
                    )

            cursor?.use {
                val _id = it.getColumnIndex("_id")
                val address = it.getColumnIndex("address")
                val body = it.getColumnIndex("body")
                val date = it.getColumnIndex("date")
                val type = it.getColumnIndex("type")

                while (it.moveToNext()) {
                    messages.add(
                            mapOf(
                                    "id" to it.getString(_id),
                                    "address" to (it.getString(address) ?: ""),
                                    "body" to (it.getString(body) ?: ""),
                                    "timestamp" to it.getLong(date),
                                    "type" to it.getInt(type) // 1 = Inbox, 2 = Sent
                            )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading messages for thread $threadId", e)
        }

        return messages
    }

    fun sendSms(address: String, body: String) {
        try {
            val smsManager = context.getSystemService(SmsManager::class.java)

            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(address, null, parts, null, null)
                Log.d(TAG, "Sent multipart SMS (${parts.size} parts) to $address")
            } else {
                smsManager.sendTextMessage(address, null, body, null, null)
                Log.d(TAG, "Sent single SMS to $address")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS", e)
            throw e
        }
    }
}
