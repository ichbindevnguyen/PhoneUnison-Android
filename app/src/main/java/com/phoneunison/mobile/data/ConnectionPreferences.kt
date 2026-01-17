package com.phoneunison.mobile.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class ConnectionPreferences(context: Context) {

    companion object {
        private const val TAG = "ConnectionPreferences"
        private const val PREF_NAME = "phoneunison_connection"

        private const val KEY_PAIRED = "is_paired"
        private const val KEY_LAST_SERVER_HOST = "last_server_host"
        private const val KEY_LAST_SERVER_PORT = "last_server_port"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_PAIRING_CODE = "pairing_code"
        private const val KEY_PUBLIC_KEY = "public_key"
    }

    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var isPaired: Boolean
        get() = prefs.getBoolean(KEY_PAIRED, false)
        set(value) = prefs.edit().putBoolean(KEY_PAIRED, value).apply()

    var lastServerHost: String?
        get() = prefs.getString(KEY_LAST_SERVER_HOST, null)
        set(value) = prefs.edit().putString(KEY_LAST_SERVER_HOST, value).apply()

    var lastServerPort: Int
        get() = prefs.getInt(KEY_LAST_SERVER_PORT, 8765)
        set(value) = prefs.edit().putInt(KEY_LAST_SERVER_PORT, value).apply()

    var deviceId: String?
        get() = prefs.getString(KEY_DEVICE_ID, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var deviceName: String?
        get() = prefs.getString(KEY_DEVICE_NAME, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_NAME, value).apply()

    var pairingCode: String?
        get() = prefs.getString(KEY_PAIRING_CODE, null)
        set(value) = prefs.edit().putString(KEY_PAIRING_CODE, value).apply()

    var publicKey: String?
        get() = prefs.getString(KEY_PUBLIC_KEY, null)
        set(value) = prefs.edit().putString(KEY_PUBLIC_KEY, value).apply()

    fun saveConnection(host: String, port: Int, deviceId: String?, deviceName: String?) {
        prefs.edit().apply {
            putBoolean(KEY_PAIRED, true)
            putString(KEY_LAST_SERVER_HOST, host)
            putInt(KEY_LAST_SERVER_PORT, port)
            putString(KEY_DEVICE_ID, deviceId)
            putString(KEY_DEVICE_NAME, deviceName)
            apply()
        }
        Log.i(TAG, "Saved connection: $host:$port, device=$deviceName")
    }

    fun clearConnection() {
        prefs.edit().apply {
            putBoolean(KEY_PAIRED, false)
            remove(KEY_LAST_SERVER_HOST)
            remove(KEY_LAST_SERVER_PORT)
            remove(KEY_DEVICE_ID)
            remove(KEY_DEVICE_NAME)
            remove(KEY_PAIRING_CODE)
            remove(KEY_PUBLIC_KEY)
            apply()
        }
        Log.i(TAG, "Cleared saved connection")
    }

    fun hasSavedConnection(): Boolean {
        return isPaired && !lastServerHost.isNullOrEmpty()
    }
}
