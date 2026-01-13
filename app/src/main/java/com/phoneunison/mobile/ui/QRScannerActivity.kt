package com.phoneunison.mobile.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.phoneunison.mobile.databinding.ActivityQrScannerBinding
import com.phoneunison.mobile.services.ConnectionService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QRScannerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "QRScannerActivity"
    }

    private lateinit var binding: ActivityQrScannerBinding
    private lateinit var cameraExecutor: ExecutorService
    private var barcodeScanner: BarcodeScanner? = null
    private var isProcessing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupBarcodeScanner()
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupBarcodeScanner() {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, QRCodeAnalyzer())
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private inner class QRCodeAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            if (isProcessing) {
                imageProxy.close()
                return
            }

            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                barcodeScanner?.process(image)
                    ?.addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            barcode.rawValue?.let { value ->
                                if (!isProcessing) {
                                    isProcessing = true
                                    processQRCode(value)
                                }
                            }
                        }
                    }
                    ?.addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }

    private fun processQRCode(content: String) {
        Log.d(TAG, "QR Code scanned: $content")

        try {
            val pairingInfo = Gson().fromJson(content, PairingInfo::class.java)
            
            runOnUiThread {
                binding.tvStatus.text = "Connecting to ${pairingInfo.ip}..."
            }

            val intent = Intent(this, ConnectionService::class.java).apply {
                action = ConnectionService.ACTION_CONNECT
                putExtra(ConnectionService.EXTRA_HOST, pairingInfo.ip)
                putExtra(ConnectionService.EXTRA_PORT, pairingInfo.port)
                putExtra(ConnectionService.EXTRA_CODE, pairingInfo.code)
                putExtra(ConnectionService.EXTRA_PUBLIC_KEY, pairingInfo.key)
            }
            startForegroundService(intent)

            binding.root.postDelayed({
                if (ConnectionService.isConnected) {
                    Toast.makeText(this, "Connected successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    isProcessing = false
                    binding.tvStatus.text = "Connection failed. Please try again."
                }
            }, 3000)

        } catch (e: Exception) {
            Log.e(TAG, "Invalid QR code", e)
            runOnUiThread {
                Toast.makeText(this, "Invalid QR code", Toast.LENGTH_SHORT).show()
                isProcessing = false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        barcodeScanner?.close()
    }

    data class PairingInfo(
        val ip: String,
        val port: Int,
        val code: String,
        val key: String
    )
}
