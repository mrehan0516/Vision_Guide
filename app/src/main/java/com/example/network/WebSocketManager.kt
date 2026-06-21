package com.example.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class WebSocketManager(
    private val onMessage: (String) -> Unit,
    private val onConnectionStatus: (Boolean) -> Unit
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
        
    private val WS_URL = "ws://agent/connect"
    private var isConnected = false

    fun connect() {
        val request = Request.Builder().url(WS_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                isConnected = true
                onConnectionStatus(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                onMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                onConnectionStatus(false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.e("WebSocket", "Failure", t)
                isConnected = false
                onConnectionStatus(false)
                CoroutineScope(Dispatchers.IO).launch {
                    delay(5000)
                    connect()
                }
            }
        })
    }
    
    fun send(message: String) {
        if (isConnected) {
            webSocket?.send(message)
        } else {
            Log.w("WebSocket", "Not connected")
        }
    }
    
    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        isConnected = false
        onConnectionStatus(false)
    }
}
