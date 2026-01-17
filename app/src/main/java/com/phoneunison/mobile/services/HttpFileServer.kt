package com.phoneunison.mobile.services

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileOutputStream

class HttpFileServer(private val context: Context, private val port: Int = 8766) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "HttpFileServer"
    }

    private val downloadDir: File by lazy {
        val dir =
                File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_DOWNLOADS
                        ),
                        "PhoneUnison"
                )
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        Log.d(TAG, "Request: $method $uri from ${session.remoteIpAddress}")

        return try {
            when {
                method == Method.POST && uri == "/upload" -> handleUpload(session)
                method == Method.GET && uri == "/ping" -> newFixedLengthResponse("pong")
                else ->
                        newFixedLengthResponse(
                                Response.Status.NOT_FOUND,
                                MIME_PLAINTEXT,
                                "Not Found"
                        )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling request", e)
            newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    MIME_PLAINTEXT,
                    "Error: ${e.message}"
            )
        }
    }

    private fun handleUpload(session: IHTTPSession): Response {
        return try {
            val params = session.parms ?: emptyMap()
            var fileName = params["filename"] ?: "received_file_${System.currentTimeMillis()}"
            fileName = sanitizeFileName(fileName)

            val contentLength = session.headers["content-length"]?.toLongOrNull() ?: 0L
            Log.d(TAG, "Upload request: filename=$fileName, contentLength=$contentLength")

            if (contentLength <= 0) {
                Log.w(TAG, "No content-length header or zero length")
                return newFixedLengthResponse(
                        Response.Status.BAD_REQUEST,
                        MIME_PLAINTEXT,
                        "No content"
                )
            }

            val destFile = getUniqueFile(fileName)
            var totalRead = 0L

            FileOutputStream(destFile).use { fos ->
                val inputStream = session.inputStream
                val buffer = ByteArray(8192)
                var bytesRead: Int

                while (totalRead < contentLength) {
                    bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    fos.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                }
                fos.flush()
            }

            Log.i(TAG, "File saved successfully: ${destFile.absolutePath} ($totalRead bytes)")
            newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    MIME_PLAINTEXT,
                    "Error: ${e.message}"
            )
        }
    }

    private fun sanitizeFileName(name: String): String {
        val decoded =
                try {
                    java.net.URLDecoder.decode(name, "UTF-8")
                } catch (e: Exception) {
                    name
                }
        return decoded.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    private fun getUniqueFile(fileName: String): File {
        var file = File(downloadDir, fileName)
        var counter = 1

        while (file.exists()) {
            val nameWithoutExt = fileName.substringBeforeLast(".")
            val ext = if (fileName.contains(".")) ".${fileName.substringAfterLast(".")}" else ""
            file = File(downloadDir, "${nameWithoutExt}_$counter$ext")
            counter++
        }

        return file
    }

    fun getDownloadDirectory(): File = downloadDir

    fun startServer() {
        try {
            start(SOCKET_READ_TIMEOUT, false)
            Log.i(TAG, "HTTP File Server started on port $port")
            Log.i(TAG, "Download directory: ${downloadDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start HTTP server", e)
        }
    }

    fun stopServer() {
        try {
            stop()
            Log.i(TAG, "HTTP File Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
    }
}
