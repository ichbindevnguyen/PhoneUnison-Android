package com.phoneunison.mobile.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.phoneunison.mobile.R
import com.phoneunison.mobile.databinding.ActivityPairingBinding
import com.phoneunison.mobile.network.DiscoveredDevice
import com.phoneunison.mobile.network.UDPDiscovery
import com.phoneunison.mobile.services.ConnectionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PairingActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PairingActivity"
    }

    private lateinit var binding: ActivityPairingBinding
    private lateinit var udpDiscovery: UDPDiscovery
    private var discoveryJob: Job? = null
    
    private val discoveredDevices = mutableListOf<DiscoveredDevice>()
    private lateinit var devicesAdapter: DevicesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        udpDiscovery = UDPDiscovery(this)
        setupUI()
        startDiscovery()
    }

    private fun setupUI() {
        binding.apply {
            toolbar.setNavigationOnClickListener { finish() }
            
            devicesAdapter = DevicesAdapter(discoveredDevices) { device ->
                connectToDevice(device)
            }
            
            rvDevices.layoutManager = LinearLayoutManager(this@PairingActivity)
            rvDevices.adapter = devicesAdapter
            
            btnScanQR.setOnClickListener {
                if (hasCameraPermission()) {
                    startQRScanner()
                } else {
                    ActivityCompat.requestPermissions(
                        this@PairingActivity,
                        arrayOf(Manifest.permission.CAMERA),
                        100
                    )
                }
            }
            
            btnManualEntry.setOnClickListener {
                showManualEntryDialog()
            }
            
            btnRefresh.setOnClickListener {
                discoveredDevices.clear()
                devicesAdapter.notifyDataSetChanged()
                tvDevicesHeader.visibility = View.GONE
                rvDevices.visibility = View.GONE
                progressDiscovery.visibility = View.VISIBLE
                tvDiscoveryStatus.text = "Searching for PC on WiFi..."
                startDiscovery()
            }
        }
    }
    
    private fun showManualEntryDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_manual_entry, null)
        
        val etIpAddress = dialogView.findViewById<android.widget.EditText>(R.id.etIpAddress)
        val etPairingCode = dialogView.findViewById<android.widget.EditText>(R.id.etPairingCode)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Connect", null)
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val ip = etIpAddress.text.toString().trim()
                val code = etPairingCode.text.toString().trim().replace(" ", "")

                if (ip.isEmpty()) {
                    Toast.makeText(this, "Please enter the PC IP address", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (code.length != 6) {
                    Toast.makeText(this, "Please enter the 6-digit code", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                connectWithCode(ip, 8765, code)
                dialog.dismiss()
            }
        }
        
        dialog.show()
    }
    
    private fun connectWithCode(ip: String, port: Int, code: String) {
        binding.apply {
            progressDiscovery.visibility = View.VISIBLE
            tvDiscoveryStatus.text = "Connecting..."
            tvDiscoveryHint.text = ip
        }
        
        val intent = Intent(this, ConnectionService::class.java).apply {
            action = ConnectionService.ACTION_CONNECT
            putExtra(ConnectionService.EXTRA_HOST, ip)
            putExtra(ConnectionService.EXTRA_PORT, port)
            putExtra(ConnectionService.EXTRA_CODE, code)
            putExtra(ConnectionService.EXTRA_PUBLIC_KEY, "")
        }
        startForegroundService(intent)

        binding.root.postDelayed({
            if (ConnectionService.isConnected) {
                Toast.makeText(this, "Connected successfully!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                binding.apply {
                    progressDiscovery.visibility = View.GONE
                    tvDiscoveryStatus.text = "Connection failed"
                    tvDiscoveryHint.text = "Invalid code or PC not responding"
                }
            }
        }, 4000)
    }
    
    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun startQRScanner() {
        val intent = Intent(this, QRScannerActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun startDiscovery() {
        discoveryJob?.cancel()
        
        binding.apply {
            progressDiscovery.visibility = View.VISIBLE
            tvDiscoveryStatus.text = "Searching for PC..."
            tvDiscoveryHint.text = "Using UDP multicast discovery"
        }
        
        discoveryJob = lifecycleScope.launch {
            try {
                val devices = withContext(Dispatchers.IO) {
                    udpDiscovery.scanOnce()
                }
                
                runOnUiThread {
                    if (devices.isEmpty()) {
                        binding.apply {
                            progressDiscovery.visibility = View.GONE
                            tvDiscoveryStatus.text = "No PC found on network"
                            tvDiscoveryHint.text = "Make sure PhoneUnison is running on your PC"
                        }
                    } else {
                        discoveredDevices.clear()
                        discoveredDevices.addAll(devices)
                        devicesAdapter.notifyDataSetChanged()
                        
                        binding.apply {
                            progressDiscovery.visibility = View.GONE
                            tvDiscoveryStatus.text = "Found ${devices.size} PC(s)"
                            tvDiscoveryHint.text = "Tap a device to connect"
                            tvDevicesHeader.visibility = View.VISIBLE
                            rvDevices.visibility = View.VISIBLE
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Discovery error", e)
                runOnUiThread {
                    binding.apply {
                        progressDiscovery.visibility = View.GONE
                        tvDiscoveryStatus.text = "Discovery failed"
                        tvDiscoveryHint.text = "Check your WiFi connection"
                    }
                }
            }
        }
    }

    private fun connectToDevice(device: DiscoveredDevice) {
        binding.apply {
            progressDiscovery.visibility = View.VISIBLE
            tvDiscoveryStatus.text = "Connecting to ${device.alias}..."
            tvDiscoveryHint.text = device.host
        }
        
        showPairingCodeDialog(device)
    }
    
    private fun showPairingCodeDialog(device: DiscoveredDevice) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pairing_code, null)
        val etCode = dialogView.findViewById<android.widget.EditText>(R.id.etPairingCode)
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Enter Pairing Code")
            .setMessage("Enter the 6-digit code shown on ${device.alias}")
            .setView(dialogView)
            .setPositiveButton("Connect") { _, _ ->
                val code = etCode.text.toString().trim().replace(" ", "")
                if (code.length == 6) {
                    connectWithCode(device.host, device.port, code)
                } else {
                    Toast.makeText(this, "Please enter a valid 6-digit code", Toast.LENGTH_SHORT).show()
                    binding.progressDiscovery.visibility = View.GONE
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                binding.progressDiscovery.visibility = View.GONE
            }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && 
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startQRScanner()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        discoveryJob?.cancel()
    }
    
    inner class DevicesAdapter(
        private val devices: List<DiscoveredDevice>,
        private val onDeviceClick: (DiscoveredDevice) -> Unit
    ) : RecyclerView.Adapter<DevicesAdapter.ViewHolder>() {
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvDeviceName)
            val tvAddress: TextView = view.findViewById(R.id.tvDeviceAddress)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_discovered_device, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val device = devices[position]
            holder.tvName.text = device.alias
            holder.tvAddress.text = "${device.host}:${device.port}"
            holder.itemView.setOnClickListener { onDeviceClick(device) }
        }
        
        override fun getItemCount() = devices.size
    }
}
