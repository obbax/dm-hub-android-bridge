package com.itsazni.notificationforwarder.relay

import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.itsazni.notificationforwarder.data.AppDatabase
import com.itsazni.notificationforwarder.data.ReplyReceipt
import com.itsazni.notificationforwarder.data.ReplyReceiptStatus
import com.itsazni.notificationforwarder.data.ReplyReceiptTransitions
import com.itsazni.notificationforwarder.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

/**
 * Minimal local HTTP server for the reply relay -- a hand-rolled ServerSocket loop, not a
 * library dependency (NanoHTTPD or similar). Chosen because the surface is exactly two
 * endpoints with a trivial request shape (short JSON POST, header-only GET) that dm-hub itself
 * controls end to end: a ~150-line HTTP/1.1 responder is smaller, adds zero new Maven
 * dependency (no risk to the gradle offline/online build), and is easier to audit for the one
 * thing that actually matters -- constant-time auth before any RemoteInput ever fires.
 *
 * Lifecycle: started/stopped by AppNotificationListenerService.onListenerConnected /
 * onListenerDisconnected -- no new foreground service, no new process (task 4).
 */
class RelayServer(
    private val context: Context,
    private val registry: ReplyActionRegistry,
    private val port: Int
) {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptJob: Job? = null
    private val gson = Gson()

    fun start() {
        if (serverSocket != null) return
        val socket = runCatching { ServerSocket(port) }.getOrElse {
            Log.w(TAG, "failed to bind relay port $port: ${it.message}")
            return
        }
        serverSocket = socket
        acceptJob = scope.launch {
            while (isActive) {
                val client = try {
                    socket.accept()
                } catch (e: Exception) {
                    if (isActive) Log.w(TAG, "accept loop stopped: ${e.message}")
                    break
                }
                launch { handleClient(client) }
            }
        }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private suspend fun handleClient(client: Socket) {
        client.use { sock ->
            runCatching {
                sock.soTimeout = SOCKET_TIMEOUT_MS
                val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                val requestLine = reader.readLine() ?: return@use
                val parts = requestLine.split(" ")
                if (parts.size < 2) {
                    writeResponse(sock.getOutputStream(), 400, mapOf("error" to "bad request"))
                    return@use
                }
                val method = parts[0]
                val path = parts[1].substringBefore('?')

                val headers = mutableMapOf<String, String>()
                var line = reader.readLine()
                while (!line.isNullOrEmpty()) {
                    val idx = line.indexOf(':')
                    if (idx > 0) {
                        headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                    }
                    line = reader.readLine()
                }

                // Auth first, before any endpoint logic runs -- fail-closed on every route.
                val settings = SettingsStore(context)
                val providedSecret = headers["x-dm-hub-secret"]
                if (!RelayAuth.secretsMatch(providedSecret, settings.relaySecret)) {
                    writeResponse(sock.getOutputStream(), 401, null)
                    return@use
                }

                when {
                    method == "GET" && path == "/healthz" -> {
                        writeResponse(
                            sock.getOutputStream(), 200,
                            mapOf("ok" to true, "registeredActions" to registry.size())
                        )
                    }

                    method == "POST" && path == "/reply" -> {
                        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                        val body = readBody(reader, contentLength)
                        handleReply(body, sock.getOutputStream())
                    }

                    else -> writeResponse(sock.getOutputStream(), 404, mapOf("error" to "not found"))
                }
            }.onFailure { e -> Log.w(TAG, "handleClient error: ${e.message}") }
        }
    }

    private fun readBody(reader: BufferedReader, contentLength: Int): String {
        if (contentLength <= 0) return ""
        val buf = CharArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val n = reader.read(buf, read, contentLength - read)
            if (n == -1) break
            read += n
        }
        return String(buf, 0, read)
    }

    private suspend fun handleReply(body: String, out: OutputStream) {
        val payload = try {
            gson.fromJson(body, ReplyRequest::class.java)
        } catch (e: JsonSyntaxException) {
            writeResponse(out, 400, mapOf("error" to "invalid json"))
            return
        }
        val notificationKey = payload?.notificationKey
        val text = payload?.text
        if (notificationKey.isNullOrBlank() || text.isNullOrBlank()) {
            writeResponse(out, 400, mapOf("error" to "notificationKey and text are required"))
            return
        }

        val registered = registry.find(notificationKey)
        if (registered == null) {
            // Notification isn't active/recently seen anymore -- dm-hub surfaces this as
            // "device reply unavailable" per the checkpoint 8 spec.
            writeResponse(out, 404, mapOf("error" to "notification not found in registry"))
            return
        }

        val dao = AppDatabase.getInstance(context).replyReceiptDao()
        val receiptId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()
        // Persist SENT_ATTEMPTED *before* firing the PendingIntent (task 3) so a crash between
        // send() and the status update still leaves durable evidence an attempt was made.
        dao.insert(
            ReplyReceipt(
                id = receiptId,
                notificationKey = notificationKey,
                status = ReplyReceiptStatus.SENT_ATTEMPTED,
                createdAt = createdAt,
                updatedAt = createdAt
            )
        )

        try {
            val resultsIntent = Intent()
            val results = Bundle()
            registered.remoteInputs.forEach { results.putCharSequence(it.resultKey, text) }
            RemoteInput.addResultsToIntent(registered.remoteInputs, resultsIntent, results)
            registered.actionIntent.send(context, 0, resultsIntent)

            dao.updateStatus(
                receiptId,
                ReplyReceiptTransitions.confirm(ReplyReceiptStatus.SENT_ATTEMPTED),
                System.currentTimeMillis()
            )
            writeResponse(out, 200, mapOf("id" to receiptId))
        } catch (e: Exception) {
            Log.w(TAG, "reply send failed for $notificationKey: ${e.message}")
            dao.updateStatus(
                receiptId,
                ReplyReceiptTransitions.fail(ReplyReceiptStatus.SENT_ATTEMPTED),
                System.currentTimeMillis()
            )
            writeResponse(out, 500, mapOf("error" to "relay send failed"))
        }
    }

    private fun writeResponse(out: OutputStream, status: Int, body: Map<String, Any?>?) {
        val statusText = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "Internal Server Error"
        }
        val json = if (body != null) gson.toJson(body) else ""
        val bytes = json.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $status $statusText\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        out.write(header.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private data class ReplyRequest(val notificationKey: String?, val text: String?)

    companion object {
        private const val TAG = "RelayServer"
        private const val SOCKET_TIMEOUT_MS = 5000
    }
}
