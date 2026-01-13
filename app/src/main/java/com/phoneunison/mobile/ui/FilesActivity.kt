package com.phoneunison.mobile.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.phoneunison.mobile.R
import com.phoneunison.mobile.services.ConnectionService

class FilesActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val service = ConnectionService.instance
            if (service != null && ConnectionService.isConnected) {
                service.fileHandler.sendFileOffer(uri)
                tvStatus.text = "Sending file offer..."
                Toast.makeText(this, "File offer sent", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Not connected to PC", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_files)
        title = "Files"

        tvStatus = findViewById(R.id.tvStatus)
        
        findViewById<Button>(R.id.btnSelectFile).setOnClickListener {
            filePicker.launch("*/*")
        }
    }
}
