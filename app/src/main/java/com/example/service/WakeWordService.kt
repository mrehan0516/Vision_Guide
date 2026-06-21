package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class WakeWordService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val CHANNEL_ID = "WakeWordServiceChannel"

    companion object {
        const val ACTION_START_LISTENING = "com.example.action.START_LISTENING"
        const val ACTION_STOP_LISTENING = "com.example.action.STOP_LISTENING"
        const val BROADCAST_WAKE_WORD_DETECTED = "com.example.broadcast.WAKE_WORD_DETECTED"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VisionPilot Background Assistant")
            .setContentText("Listening for wake words 'Hi' or 'Hello'...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        startForeground(1, notification)

        when (intent?.action) {
            ACTION_START_LISTENING -> startListening()
            ACTION_STOP_LISTENING -> {
                stopListening()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startListening() {
        if (isListening) return

        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e("WakeWordService", "RECORD_AUDIO permission not granted")
            stopSelf()
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("WakeWordService", "Speech recognition not available")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                isListening = false
                Log.d("WakeWordService", "Error: \$error. Restarting...")
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    Log.e("WakeWordService", "Insufficient permissions. Stopping service.")
                    stopSelf()
                    return
                }
                restartListeningSafely()
            }

            override fun onResults(results: android.os.Bundle?) {
                handleResults(results)
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) {
                handleResults(partialResults)
            }

            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
        isListening = true
    }

    private fun handleResults(results: android.os.Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val text = matches[0].lowercase(java.util.Locale.getDefault())
            if (text.contains("hi") || text.contains("hello") || text.contains("hey")) {
                // Wake word detected! Notify the app
                val broadcastIntent = Intent(BROADCAST_WAKE_WORD_DETECTED)
                sendBroadcast(broadcastIntent)
                Log.d("WakeWordService", "Wake word detected!")
            }
        }
        
        // Restart loop since it ended
        isListening = false
        restartListeningSafely()
    }

    private fun restartListeningSafely() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (speechRecognizer != null) {
                try {
                    startListening()
                } catch (e: Exception) {
                    Log.e("WakeWordService", "Failed to restart listening", e)
                }
            }
        }, 500)
    }

    private fun stopListening() {
        isListening = false
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        speechRecognizer = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Hands Free Wake Word Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
    }
}
