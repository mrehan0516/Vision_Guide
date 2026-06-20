package com.example.data.service

import kotlinx.coroutines.delay
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// ── Existing request/response payloads ──────────────────────────────

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

// ── Agent request/response payloads ─────────────────────────────────

data class AgentGoalRequest(val goal: String)
data class AgentPlanResponse(
    val goal: String,
    val steps: List<String>,
    val totalSteps: Int,
    val narration: String
)

data class AgentStepRequest(
    val success: Boolean,
    val screenElementsJson: String = "[]",
    val screenDescription: String = "",
    val error: String = ""
)

data class AgentActionResponse(
    val action: String,       // "tap", "type_text", "open_app", "scroll_down", "press_back", "done", "fail", etc.
    val target: String,
    val value: String,
    val narration: String,
    val x: Int,
    val y: Int,
    val confidence: Double,
    val planStatus: String,   // "executing", "completed", "failed"
    val currentStep: Int,
    val totalSteps: Int
)

data class AgentStatusResponse(
    val hasActivePlan: Boolean,
    val goal: String,
    val status: String,
    val currentStep: Int,
    val totalSteps: Int,
    val narration: String,
    val actionsExecuted: Int
)

/**
 * Retrofit service — all REST endpoints including the new agent routes.
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

    // ── Agent endpoints ─────────────────────────────────────────
    @POST("/agent/plan")
    suspend fun agentPlan(@Body request: AgentGoalRequest): Response<AgentPlanResponse>

    @POST("/agent/next-action")
    suspend fun agentNextAction(@Body request: AgentStepRequest): Response<AgentActionResponse>

    @GET("/agent/status")
    suspend fun agentStatus(): Response<AgentStatusResponse>

    @POST("/agent/reset")
    suspend fun agentReset(): Response<Map<String, String>>
}

/**
 * Mock client that simulates the agent flow locally when the backend is unreachable.
 * Now includes agentic plan/step simulation so the app can demo the full flow offline.
 */
class MockVisionPilotClient {
    private var webSocketConnected = false
    private var mockPlanSteps: List<String> = emptyList()
    private var mockCurrentStep = 0

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
        onMessageReceived("VisionPilot agent connected. Ready for voice commands.")
    }

    suspend fun disconnectWebSocket() {
        delay(600)
        webSocketConnected = false
    }

    suspend fun mockSendVoiceCommand(spokenText: String): VoiceCommandResponse {
        delay(800)
        return VoiceCommandResponse(
            recognizedText = spokenText,
            responseSpeech = "Got it: '$spokenText'. Let me plan the steps for you.",
            executeAction = true
        )
    }

    suspend fun mockPlanGoal(goal: String): AgentPlanResponse {
        delay(1000)
        val steps = when {
            "search" in goal.lowercase() && "google" in goal.lowercase() -> listOf(
                "Open Chrome browser",
                "Tap the search bar",
                "Type the search query",
                "Tap Search on keyboard",
                "Wait for results to load"
            )
            "open" in goal.lowercase() -> {
                val app = goal.lowercase().substringAfter("open").trim()
                listOf("Open the $app app", "Wait for $app to load")
            }
            "call" in goal.lowercase() -> {
                val contact = goal.lowercase().substringAfter("call").trim()
                listOf("Open Phone app", "Search for $contact", "Tap $contact", "Tap call button")
            }
            else -> listOf("Analyze screen", "Execute: $goal")
        }
        mockPlanSteps = steps
        mockCurrentStep = 0
        return AgentPlanResponse(
            goal = goal,
            steps = steps,
            totalSteps = steps.size,
            narration = "I'll help you $goal. I've planned ${steps.size} steps."
        )
    }

    suspend fun mockNextAction(success: Boolean): AgentActionResponse {
        delay(600)
        if (!success && mockCurrentStep > 0) {
            return AgentActionResponse(
                action = "wait",
                target = "",
                value = "",
                narration = "That didn't work. Let me try again.",
                x = 0, y = 0,
                confidence = 0.7,
                planStatus = "executing",
                currentStep = mockCurrentStep,
                totalSteps = mockPlanSteps.size
            )
        }

        if (mockCurrentStep >= mockPlanSteps.size) {
            return AgentActionResponse(
                action = "done",
                target = "",
                value = "",
                narration = "All steps completed successfully!",
                x = 0, y = 0,
                confidence = 1.0,
                planStatus = "completed",
                currentStep = mockCurrentStep,
                totalSteps = mockPlanSteps.size
            )
        }

        val step = mockPlanSteps[mockCurrentStep]
        mockCurrentStep++

        val action = when {
            step.lowercase().startsWith("open") -> "open_app"
            step.lowercase().contains("tap") || step.lowercase().contains("click") -> "tap"
            step.lowercase().startsWith("type") || step.lowercase().contains("search for") -> "type_text"
            step.lowercase().contains("wait") -> "wait"
            step.lowercase().contains("scroll") -> "scroll_down"
            else -> "tap"
        }

        return AgentActionResponse(
            action = action,
            target = step,
            value = if (action == "type_text") step.substringAfter(":").trim().ifEmpty { step.substringAfter("for").trim() } else "",
            narration = "Step ${mockCurrentStep} of ${mockPlanSteps.size}: $step",
            x = 540, y = 960,
            confidence = 0.9,
            planStatus = "executing",
            currentStep = mockCurrentStep,
            totalSteps = mockPlanSteps.size
        )
    }
}
