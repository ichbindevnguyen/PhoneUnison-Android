package com.phoneunison.mobile.services

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.phoneunison.mobile.protocol.Message
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.source

class FileHandler(private val context: Context, private val connectionService: ConnectionService) {

    companion object {
        private const val TAG = "FileHandler"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(0, TimeUnit.SECONDS) // No timeout for upload
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

    fun sendFileOffer(uri: Uri) {
        val meta = getFileMetadata(uri) ?: return
        val fileName = meta.first
        val fileSize = meta.second

        val message =
                Message(
                        Message.FILE_OFFER,
                        mapOf(
                                "fileName" to fileName,
                                "fileSize" to fileSize,
                                "uri" to uri.toString() // Store URI for upload later
                        )
                )
        connectionService.sendMessage(message)
        Log.i(TAG, "Sent file offer: $fileName ($fileSize bytes)")
    }

    fun uploadFile(uriString: String, fileName: String) {
        val uri = Uri.parse(uriString)
        val host = connectionService.serverHost ?: return
        val port = connectionService.serverPort

        scope.launch {
            try {
                Log.i(TAG, "Starting upload: $fileName to $host:$port")

                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch

                // Create RequestBody from InputStream
                val requestBody =
                        object : RequestBody() {
                            override fun contentType() =
                                    "application/octet-stream".toMediaTypeOrNull()

                            override fun contentLength(): Long {
                                return getFileMetadata(uri)?.second ?: -1
                            }

                            override fun writeTo(sink: okio.BufferedSink) {
                                inputStream.use { stream -> sink.writeAll(stream.source()) }
                            }
                        }

                val url = "http://$host:$port/upload?filename=$fileName"

                val request = Request.Builder().url(url).post(requestBody).build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.i(TAG, "Upload successful: $fileName")
                        // Send FILE_COMPLETE message if needed, or rely on PC to know
                    } else {
                        Log.e(TAG, "Upload failed: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading file", e)
            }
        }
    }

    private fun getFileMetadata(uri: Uri): Pair<String, Long>? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                    val name = if (nameIndex != -1) cursor.getString(nameIndex) else "unknown_file"
                    val size = if (sizeIndex != -1) cursor.getLong(sizeIndex) else -1L

                    Pair(name, size)
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file metadata", e)
            null
        }
    }
}
