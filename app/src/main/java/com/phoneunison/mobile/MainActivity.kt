package com.phoneunison.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.phoneunison.mobile.databinding.ActivityMainBinding
import com.phoneunison.mobile.services.ConnectionService
import com.phoneunison.mobile.ui.CallsActivity
import com.phoneunison.mobile.ui.FilesActivity
import com.phoneunison.mobile.ui.MessagesActivity
import com.phoneunison.mobile.ui.NotificationsActivity
import com.phoneunison.mobile.ui.PairingActivity
import com.phoneunison.mobile.ui.SettingsActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requiredPermissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
    }

    private val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    permissions ->
                val allGranted = permissions.values.all { it }
                if (allGranted) {
                    checkNotificationListenerPermission()
                } else {
                    showPermissionRationale()
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateConnectionStatus()
    }

    private fun setupUI() {
        binding.apply {
            btnPair.setOnClickListener {
                startActivity(Intent(this@MainActivity, PairingActivity::class.java))
            }

            btnSettings.setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }

            cardNotifications.setOnClickListener {
                startActivity(Intent(this@MainActivity, NotificationsActivity::class.java))
            }

            cardMessages.setOnClickListener {
                startActivity(Intent(this@MainActivity, MessagesActivity::class.java))
            }

            cardCalls.setOnClickListener {
                startActivity(Intent(this@MainActivity, CallsActivity::class.java))
            }

            cardFiles.setOnClickListener {
                startActivity(Intent(this@MainActivity, FilesActivity::class.java))
            }

            btnDisconnect.setOnClickListener { disconnectDevice() }
        }
    }

    private fun checkPermissions() {
        val missingPermissions =
                requiredPermissions.filter {
                    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            checkNotificationListenerPermission()
        }
    }

    private fun checkNotificationListenerPermission() {
        val enabledListeners =
                Settings.Secure.getString(contentResolver, "enabled_notification_listeners")

        if (enabledListeners == null || !enabledListeners.contains(packageName)) {
            AlertDialog.Builder(this)
                    .setTitle("Notification Access Required")
                    .setMessage(
                            "PhoneUnison needs notification access to sync your notifications to PC. Please enable it in settings."
                    )
                    .setPositiveButton("Open Settings") { _, _ ->
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                    .setNegativeButton("Later", null)
                    .show()
        }
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage(
                        "PhoneUnison needs these permissions to sync notifications, SMS, and calls with your PC."
                )
                .setPositiveButton("Grant Permissions") { _, _ -> checkPermissions() }
                .setNegativeButton("Cancel", null)
                .show()
    }

    private fun updateConnectionStatus() {
        val isConnected = ConnectionService.isConnected
        val deviceName = ConnectionService.connectedDeviceName

        binding.apply {
            if (isConnected && deviceName != null) {
                tvStatus.text = "Connected"
                tvDeviceName.text = deviceName
                statusCircle.setBackgroundResource(R.drawable.circle_connected)
                btnDisconnect.isEnabled = true
                btnPair.text = "Reconnect"
            } else {
                tvStatus.text = "Not Connected"
                tvDeviceName.text = "Tap below to connect to your PC"
                statusCircle.setBackgroundResource(R.drawable.circle_accent)
                btnDisconnect.isEnabled = false
                btnPair.text = "Pair with PC"
            }
        }
    }

    private fun disconnectDevice() {
        stopService(Intent(this, ConnectionService::class.java))
        Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
        updateConnectionStatus()
    }
}
