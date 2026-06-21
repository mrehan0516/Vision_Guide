package com.example.data.ai

import com.example.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null
)

@Serializable
data class ResponseFormat(
    val text: ResponseFormatText? = null
)

@Serializable
data class ResponseFormatText(
    val mimeType: String,
    val schema: JsonObject? = null
)

@Serializable
data class GenerationConfig(
    val responseFormat: ResponseFormat? = null,
    val temperature: Float? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>
)

@Serializable
data class Candidate(
    val content: Content
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

@Serializable
data class ParsedCommand(
    val action: String, // "call", "whatsapp", "alarm", "setTimer", "openApp", "readScreen", "clickText", "scrollForward", "scrollBackward", "globalHome", "globalBack", "adjustVolume", "startTutorial", "none"
    val contactName: String = "",
    val appName: String = "",
    val durationMinutes: Int = 0,
    val timeHour: Int = 0,
    val timeMinute: Int = 0,
    val isPm: Boolean = false,
    val textToFind: String = "",
    val volumeLevel: Int = -1,
    val volumeDirection: String = "",
    val responseSpeech: String = ""
)

open class CommandParser {
    open suspend fun parseCommand(query: String, contextHistory: List<String> = emptyList()): ParsedCommand = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return@withContext ParsedCommand("none", responseSpeech = "Missing Gemini API key.")
        }
        
        val historyContext = if (contextHistory.isNotEmpty()) "Recent user statements provided for context (resolving pronouns like 'he', 'that', 'it'):\n${contextHistory.joinToString("\n")}\n\n" else ""
        
        val systemInstructionText = """
            You are an advanced accessibility assistant for blind users.
            Parse the user's scattered technical language and jargon. Determine their core intent and extract relevant details.
            $historyContext
            Action must be one of:
            - call: if they want to call someone via phone. Extract 'contactName'.
            - whatsapp: if they want to message/call someone via WhatsApp. Extract 'contactName'.
            - alarm: setup an exact time-of-day alarm. Extract 'timeHour' (1-12), 'timeMinute', and 'isPm'.
            - setTimer: set a relative timer or duration. Extract 'durationMinutes'. Example: 'setup timer for medicine for next 2 months' -> parse the closest actionable duration or note it in response.
            - openApp: open any app. Extract 'appName'. Determine the app's clean canonical name.
            - readScreen: if they want to know what is on the screen, like "read", "describe screen".
            - clickText: if they want to tap or click a specific label or button. Extract 'textToFind'. Example: "click submit", "tap on options".
            - scrollForward: to scroll down or scroll forward.
            - scrollBackward: to scroll up or scroll backward.
            - globalHome: to go to home screen.
            - globalBack: to go back.
            - adjustVolume: increase, decrease, or set system volume. Extract 'volumeLevel' (0-100) if specified via percent or number, or 'volumeDirection' ("up", "down", "max", "mute").
            - startTutorial: start the interactive guide explaining available voice commands.
            - addQuickCommand: if the user wants to add a new quick command for the home screen. Extract 'textToFind' as the command string to add (e.g. "open WhatsApp", "call Mom").
            - none: general query or unrecognized command.
            Verbally verify the task execution in 'responseSpeech'. Example: 'Opening YouTube.' or 'Timer set for medicine.'
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = query)))),
            systemInstruction = Content(
                parts = listOf(Part(text = systemInstructionText))
            ),
            generationConfig = GenerationConfig(
                temperature = 0.1f,
                responseFormat = ResponseFormat(
                    text = ResponseFormatText(
                        mimeType = "application/json",
                        schema = buildJsonObject {
                            put("type", "OBJECT")
                            putJsonObject("properties") {
                                putJsonObject("action") {
                                    put("type", "STRING")
                                    put("description", "One of: call, whatsapp, alarm, setTimer, openApp, readScreen, clickText, scrollForward, scrollBackward, globalHome, globalBack, adjustVolume, startTutorial, addQuickCommand, none")
                                }
                                putJsonObject("contactName") { put("type", "STRING") }
                                putJsonObject("appName") { put("type", "STRING") }
                                putJsonObject("durationMinutes") { put("type", "INTEGER") }
                                putJsonObject("timeHour") { put("type", "INTEGER") }
                                putJsonObject("timeMinute") { put("type", "INTEGER") }
                                putJsonObject("isPm") { put("type", "BOOLEAN") }
                                putJsonObject("textToFind") { put("type", "STRING") }
                                putJsonObject("volumeLevel") { put("type", "INTEGER") }
                                putJsonObject("volumeDirection") { put("type", "STRING") }
                                putJsonObject("responseSpeech") { put("type", "STRING") }
                            }
                            putJsonArray("required") {
                                add("action")
                                add("responseSpeech")
                            }
                        }
                    )
                )
            )
        )
        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "{}"
            val jsonObj = Json.parseToJsonElement(jsonText).jsonObject
            
            val action = jsonObj["action"]?.jsonPrimitive?.content ?: "none"
            val contactName = jsonObj["contactName"]?.jsonPrimitive?.content ?: ""
            val appName = jsonObj["appName"]?.jsonPrimitive?.content ?: ""
            val durationMinutes = jsonObj["durationMinutes"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val timeHour = jsonObj["timeHour"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val timeMinute = jsonObj["timeMinute"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val isPm = jsonObj["isPm"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val textToFind = jsonObj["textToFind"]?.jsonPrimitive?.content ?: ""
            val volumeLevel = jsonObj["volumeLevel"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1
            val volumeDirection = jsonObj["volumeDirection"]?.jsonPrimitive?.content ?: ""
            val responseSpeech = jsonObj["responseSpeech"]?.jsonPrimitive?.content ?: "Action parsed."
            
            ParsedCommand(action, contactName, appName, durationMinutes, timeHour, timeMinute, isPm, textToFind, volumeLevel, volumeDirection, responseSpeech)
        } catch (e: Exception) {
            val s = query.lowercase(java.util.Locale.getDefault())
            if (s.contains("call") || s.contains("dial")) {
                val name = s.replace("call ", "").replace("dial ", "").trim()
                ParsedCommand("call", contactName = name, responseSpeech = "Calling $name.")
            } else if (s.contains("home")) {
                ParsedCommand("globalHome", responseSpeech = "Going home.")
            } else if (s.contains("back")) {
                ParsedCommand("globalBack", responseSpeech = "Going back.")
            } else if (s.contains("whatsapp")) {
                val name = s.replace("whatsapp ", "").replace("send whatsapp to ", "").trim()
                ParsedCommand("whatsapp", contactName = name, responseSpeech = "Opening WhatsApp for $name.")
            } else if (s.contains("read") || s.contains("describe screen") || s.contains("what is on")) {
                ParsedCommand("readScreen", responseSpeech = "Describing screen.")
            } else if (s.contains("scroll out") || s.contains("scroll backward")) {
                ParsedCommand("scrollBackward", responseSpeech = "Scrolling backward.")
            } else if (s.contains("scroll")) {
                ParsedCommand("scrollForward", responseSpeech = "Scrolling forward.")
            } else if (s.contains("volume")) {
                if (s.contains("up") || s.contains("increase")) {
                    ParsedCommand("adjustVolume", volumeDirection = "up", responseSpeech = "Increasing volume.")
                } else if (s.contains("down") || s.contains("decrease") || s.contains("lower")) {
                    ParsedCommand("adjustVolume", volumeDirection = "down", responseSpeech = "Decreasing volume.")
                } else {
                    ParsedCommand("adjustVolume", responseSpeech = "Adjusting volume.")
                }
            } else if (s.contains("tutorial") || s.contains("guide") || s.contains("help") || s.contains("commands")) {
                ParsedCommand("startTutorial", responseSpeech = "Starting tutorial.")
            } else {
                ParsedCommand("none", responseSpeech = "Error parsing string or no internet connection.")
            }
        }
    }
}

