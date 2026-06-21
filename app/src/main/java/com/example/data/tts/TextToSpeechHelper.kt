package com.example.data.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

class TextToSpeechHelper(
    context: Context,
    private val onSpeechStatusChanged: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    private var pendingRate: Float = 1.0f
    private var pendingPitch: Float = 1.0f
    private var pendingVoiceName: String? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TextToSpeechHelper", "US language pack missing or not supported")
            } else {
                isInitialized = true
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        onSpeechStatusChanged?.invoke(true)
                    }

                    override fun onDone(utteranceId: String?) {
                        onSpeechStatusChanged?.invoke(false)
                    }

                    override fun onError(utteranceId: String?) {
                        onSpeechStatusChanged?.invoke(false)
                    }
                })
                // Apply any pending parameters set before initialization finished
                setSpeechRate(pendingRate)
                setPitch(pendingPitch)
                pendingVoiceName?.let { setVoiceByName(it) }
                speak("Vision Pilot Initialized")
            }
        } else {
            Log.e("TextToSpeechHelper", "TTS Initialization failed")
        }
    }

    fun setSpeechRate(rate: Float) {
        pendingRate = rate
        if (isInitialized) {
            tts?.setSpeechRate(rate)
        }
    }

    fun setPitch(pitch: Float) {
        pendingPitch = pitch
        if (isInitialized) {
            tts?.setPitch(pitch)
        }
    }

    /**
     * Predefined fallback list combined with physical system voices
     */
    fun getAvailableVoices(): List<String> {
        val fallback = listOf("Default US Voice", "English UK Voice", "English Canada Voice", "English India Voice")
        if (!isInitialized) return fallback
        return try {
            val systemVoices = tts?.voices?.map { it.name }?.filter { it.contains("en", ignoreCase = true) } ?: emptyList()
            if (systemVoices.isEmpty()) {
                fallback
            } else {
                // Return a combined list of native voices and friendly descriptions to make UI readable
                (systemVoices.take(6) + fallback).distinct()
            }
        } catch (e: Exception) {
            fallback
        }
    }

    fun setVoiceByName(voiceName: String) {
        pendingVoiceName = voiceName
        if (!isInitialized) return
        try {
            // Check if there is a system voice matching this name
            val matches = tts?.voices?.firstOrNull { it.name == voiceName }
            if (matches != null) {
                tts?.voice = matches
            } else {
                // Map friendly locale/name to structured locale preset
                val locale = when (voiceName) {
                    "English UK Voice" -> Locale.UK
                    "English Canada Voice" -> Locale.CANADA
                    "English India Voice" -> java.util.Locale.Builder().setLanguage("en").setRegion("IN").build()
                    else -> Locale.US
                }
                tts?.setLanguage(locale)
            }
        } catch (e: Exception) {
            Log.e("TextToSpeechHelper", "Error setting voice: $voiceName", e)
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "VisionPilotTTS")
        } else {
            Log.w("TextToSpeechHelper", "TTS engine not fully initialized yet. Command: $text")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
