package com.phoneunison.mobile.services

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.phoneunison.mobile.protocol.Message

class CallHandler(private val context: Context, private val connectionService: ConnectionService) {

    companion object {
        private const val TAG = "CallHandler"
    }

    fun getSimCards(): List<Map<String, Any>> {
        val simList = mutableListOf<Map<String, Any>>()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
                        PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "READ_PHONE_STATE permission not granted")
            return simList
        }

        try {
            val subscriptionManager =
                    context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as
                            SubscriptionManager

            val activeSubscriptions: List<SubscriptionInfo>? =
                    subscriptionManager.activeSubscriptionInfoList

            activeSubscriptions?.forEachIndexed { index, info ->
                @Suppress("DEPRECATION") val phoneNumber = info.number ?: ""
                simList.add(
                        mapOf(
                                "slotIndex" to info.simSlotIndex,
                                "subscriptionId" to info.subscriptionId,
                                "carrierName" to
                                        (info.carrierName?.toString() ?: "SIM ${index + 1}"),
                                "displayName" to
                                        (info.displayName?.toString() ?: "SIM ${index + 1}"),
                                "number" to phoneNumber
                        )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting SIM cards", e)
        }

        if (simList.isEmpty()) {
            simList.add(
                    mapOf(
                            "slotIndex" to 0,
                            "subscriptionId" to -1,
                            "carrierName" to "Default SIM",
                            "displayName" to "Default SIM",
                            "number" to ""
                    )
            )
        }

        return simList
    }

    fun sendSimList() {
        val simCards = getSimCards()
        val message = Message(Message.SIM_LIST, mapOf("sims" to simCards))
        connectionService.sendMessage(message)
        Log.i(TAG, "Sent SIM list: ${simCards.size} SIMs")
    }

    fun dialNumber(phoneNumber: String, subscriptionId: Int = -1) {
        Log.i(TAG, "dialNumber called: number=$phoneNumber, subscriptionId=$subscriptionId")

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) !=
                        PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "CALL_PHONE permission not granted")
            sendError("CALL_PERMISSION_DENIED", "Permission to make calls not granted")
            return
        }

        Log.d(TAG, "CALL_PHONE permission granted, proceeding...")

        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val uri = Uri.fromParts("tel", phoneNumber, null)
            val extras = android.os.Bundle()

            if (subscriptionId > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val phoneAccountHandles = telecomManager.callCapablePhoneAccounts
                    Log.d(TAG, "Available phone accounts: ${phoneAccountHandles.size}")

                    val subscriptionManager =
                            context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as
                                    SubscriptionManager
                    val subscriptionInfo =
                            subscriptionManager.getActiveSubscriptionInfo(subscriptionId)

                    if (subscriptionInfo != null) {
                        val simSlot = subscriptionInfo.simSlotIndex
                        Log.d(TAG, "SIM slot: $simSlot")
                        if (simSlot >= 0 && simSlot < phoneAccountHandles.size) {
                            extras.putParcelable(
                                    TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                                    phoneAccountHandles[simSlot]
                            )
                            Log.d(TAG, "Set phone account for slot $simSlot")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set specific SIM for call", e)
                }
            }

            Log.i(TAG, "Placing call via TelecomManager: $phoneNumber")
            telecomManager.placeCall(uri, extras)
            Log.i(TAG, "Call placed successfully via TelecomManager")
        } catch (e: Exception) {
            Log.w(TAG, "TelecomManager.placeCall failed, falling back to Intent", e)
            try {
                val callIntent =
                        Intent(Intent.ACTION_CALL).apply {
                            data = Uri.parse("tel:$phoneNumber")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                context.startActivity(callIntent)
                Log.i(TAG, "Call started via Intent fallback")
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to dial number: ${e2.message}", e2)
                sendError("CALL_FAILED", e2.message ?: "Unknown error")
            }
        }
    }

    fun endCall() {
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION") telecomManager.endCall()
            }

            Log.i(TAG, "Call ended")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to end call", e)
        }
    }

    fun answerCall() {
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            @Suppress("DEPRECATION") telecomManager.acceptRingingCall()
            Log.i(TAG, "Call answered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to answer call", e)
        }
    }

    private fun sendError(code: String, errorMessage: String) {
        val message = Message(Message.ERROR, mapOf("code" to code, "message" to errorMessage))
        connectionService.sendMessage(message)
    }
}
