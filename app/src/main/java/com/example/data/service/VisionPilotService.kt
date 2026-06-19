package com.example.data.service

import kotlinx.coroutines.delay
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// Data classes for request and response payloads
data class VoiceCommandRequest(val audioBase64: String, val timestamp: Long)
data class VoiceCommandResponse(val recognizedText: String, val responseSpeech: String, val executeAction: Boolean)

data class ScreenContextRequest(val screenshotBase64: String, val hierarchyJson: String)
data class ScreenContextResponse(val success: Boolean, val detectedElements: List<DetectedUiElement>)

data class DetectedUiElement(
    val type: String, // "Button", "Text", "Input", "Icon"
    val label: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Double
)

data class CameraFrameRequest(val frameBase64: String)
data class CameraFrameResponse(val sceneDescription: String, val objectsCount: Int, val detectedObjects: List<String>)

data class ActionExecutionRequest(val actionType: String, val targetSelector: String, val argValue: String? = null)
data class ActionExecutionResponse(val success: Boolean, val errorReason: String? = null)

/**
 * Service representing real, integration-ready REST endpoints for VisionPilot.
 */
interface VisionPilotService {
    @POST("/voice-command")
    suspend fun sendVoiceCommand(@Body request: VoiceCommandRequest): Response<VoiceCommandResponse>

    @POST("/screen-context")
    suspend fun sendScreenContext(@Body request: ScreenContextRequest): Response<ScreenContextResponse>

    @POST("/camera-frame")
    suspend fun sendCameraFrame(@Body request: CameraFrameRequest): Response<CameraFrameResponse>

    @POST("/execute-action")
    suspend fun executeAction(@Body request: ActionExecutionRequest): Response<ActionExecutionResponse>
}

/**
 * Mock interface client implementation showing exactly how WebSocket connection and REST calls operate
 * asynchronously in VisionPilot, providing simulated realistic latency, data variations, and hooks.
 */
class MockVisionPilotClient {
    private var webSocketConnected = false

    fun getWebSocketUrl(): String = "ws://agent/connect"

    suspend fun connectWebSocket(
        onConnected: () -> Unit,
        onMessageReceived: (String) -> Unit,
        onDisconnected: () -> Unit
    ) {
        delay(1200)
        webSocketConnected = true
        onConnected()
        delay(500)
        onMessageReceived("Welcome to standard VisionPilot agent connection stream on ws://agent/connect.")
    }

    suspend fun disconnectWebSocket() {
        delay(600)
        webSocketConnected = false
    }

    suspend fun mockSendVoiceCommand(spokenText: String): VoiceCommandResponse {
        delay(1000)
        return when (spokenText.lowercase().trim()) {
            "call mom" -> VoiceCommandResponse(
                recognizedText = "Call Mom",
                responseSpeech = "Calling Mom right away.",
                executeAction = true
            )
            "open whatsapp" -> VoiceCommandResponse(
                recognizedText = "Open WhatsApp",
                responseSpeech = "Opening WhatsApp Messenger.",
                executeAction = true
            )
            "read this screen" -> VoiceCommandResponse(
                recognizedText = "Read this screen",
                responseSpeech = "Identified three interactive elements: 'Send' button, 'Call' button, and a layout page title.",
                executeAction = false
            )
            "describe surroundings" -> VoiceCommandResponse(
                recognizedText = "Describe surroundings",
                responseSpeech = "Detected a cozy desk space containing a computer, laptop, coffee cup, and balanced light.",
                executeAction = false
            )
            else -> VoiceCommandResponse(
                recognizedText = spokenText,
                responseSpeech = "Understood command: '$spokenText'. Initiating Remote Agent workflow.",
                executeAction = true
            )
        }
    }
}
