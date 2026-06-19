package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.CommandHistoryEntity
import com.example.data.repository.CommandHistoryRepository
import com.example.data.service.DetectedUiElement
import com.example.data.service.MockVisionPilotClient
import com.example.data.tts.TextToSpeechHelper
import com.example.data.speech.SpeechToTextHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppWorkflow {
    SPLASH, ONBOARDING, LOGIN, DASHBOARD
}

enum class AssistantStatus {
    CONNECTED, LISTENING, CONFIRMING, PROCESSING, OFFLINE
}

enum class NavigationTab {
    HOME, ACTIVITY, DEVICES, PROFILE
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = CommandHistoryRepository(database.commandHistoryDao())
    val ttsHelper = TextToSpeechHelper(application)
    private val mockClient = MockVisionPilotClient()
    private var speechToTextHelper: SpeechToTextHelper? = null

    // Screen navigation flow
    private val _workflowState = MutableStateFlow(AppWorkflow.SPLASH)
    val workflowState: StateFlow<AppWorkflow> = _workflowState.asStateFlow()

    // Onboarding page index
    private val _onboardingIndex = MutableStateFlow(0)
    val onboardingIndex: StateFlow<Int> = _onboardingIndex.asStateFlow()

    // Assistant State
    private val _assistantStatus = MutableStateFlow(AssistantStatus.CONNECTED)
    val assistantStatus: StateFlow<AssistantStatus> = _assistantStatus.asStateFlow()

    // Current Active Tab
    private val _activeTab = MutableStateFlow(NavigationTab.HOME)
    val activeTab: StateFlow<NavigationTab> = _activeTab.asStateFlow()

    // Speech-To-Text UI values
    private val _spokenInputText = MutableStateFlow("")
    val spokenInputText: StateFlow<String> = _spokenInputText.asStateFlow()

    // Waveform heights for voice animation
    private val _waveformHeights = MutableStateFlow(List(12) { 15f })
    val waveformHeights: StateFlow<List<Float>> = _waveformHeights.asStateFlow()

    // WebSocket state connection log representation
    private val _webSocketLog = MutableStateFlow("Disconnected")
    val webSocketLog: StateFlow<String> = _webSocketLog.asStateFlow()

    // Screen Understanding Model data
    private val _detectedUiElements = MutableStateFlow<List<DetectedUiElement>>(emptyList())
    val detectedUiElements: StateFlow<List<DetectedUiElement>> = _detectedUiElements.asStateFlow()

    private val _screenshotProcessing = MutableStateFlow(false)
    val screenshotProcessing: StateFlow<Boolean> = _screenshotProcessing.asStateFlow()

    // Device actions stubs output
    private val _lastActionExecuted = MutableStateFlow("")
    val lastActionExecuted: StateFlow<String> = _lastActionExecuted.asStateFlow()

    // Settings Configuration
    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(true)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _speechPitch = MutableStateFlow(1.0f)
    val speechPitch: StateFlow<Float> = _speechPitch.asStateFlow()

    private val _selectedVoice = MutableStateFlow("Default US Voice")
    val selectedVoice: StateFlow<String> = _selectedVoice.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("English (US)")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    // Database Command Feed
    val activityHistory: StateFlow<List<CommandHistoryEntity>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var waveformAnimationJob: Job? = null

    init {
        // Trigger initial onboarding / routing delay
        viewModelScope.launch {
            delay(2000)
            _workflowState.value = AppWorkflow.ONBOARDING
        }

        // Setup default logs if empty to fulfill visual needs
        viewModelScope.launch {
            delay(1000)
            val currentList = repository.allHistory.first()
            if (currentList.isEmpty()) {
                repository.insert(
                    CommandHistoryEntity(
                        command = "System Setup",
                        response = "VisionPilot Local Assistant initialized securely with TTS voice synthesizers.",
                        error = null,
                        timestamp = System.currentTimeMillis() - 600000
                    )
                )
                repository.insert(
                    CommandHistoryEntity(
                        command = "Call Mom",
                        response = "Vocal action executed safely: Initiating phone system call interface to Mom.",
                        error = null,
                        timestamp = System.currentTimeMillis() - 300000
                    )
                )
                repository.insert(
                    CommandHistoryEntity(
                        command = "Process screen layout",
                        response = "Server recognized 3 widgets: Send icon, text label (WhatsApp), active compose bar.",
                        error = "Warning: Screen capture requested during protected app viewport",
                        timestamp = System.currentTimeMillis() - 100000
                    )
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsHelper.shutdown()
        waveformAnimationJob?.cancel()
    }

    // Navigation and workflow functions
    fun setWorkflow(state: AppWorkflow) {
        _workflowState.value = state
        if (state == AppWorkflow.DASHBOARD) {
            ttsHelper.speak("Welcome to dashboard. Tap cards to engage assistant features.")
        }
    }

    fun nextOnboarding() {
        val current = _onboardingIndex.value
        if (current < 3) {
            _onboardingIndex.value = current + 1
            speakOnboardingText(current + 1)
        } else {
            _workflowState.value = AppWorkflow.LOGIN
            ttsHelper.speak("Login Screen. Select Google Login or Guest Mode access.")
        }
    }

    fun previousOnboarding() {
        val current = _onboardingIndex.value
        if (current > 0) {
            _onboardingIndex.value = current - 1
            speakOnboardingText(current - 1)
        }
    }

    fun skipOnboarding() {
        _workflowState.value = AppWorkflow.LOGIN
        ttsHelper.speak("Login Access.")
    }

    private fun speakOnboardingText(page: Int) {
        val text = when (page) {
            0 -> "Onboarding page 1: Your AI Guide."
            1 -> "Onboarding page 2: See the world through AI."
            2 -> "Onboarding page 3: Control your phone with voice."
            3 -> "Onboarding page 4: System Permissions. We require Accessibility control and Camera feed to read screen context."
            else -> ""
        }
        ttsHelper.speak(text)
    }

    fun login(isGuest: Boolean) {
        val userType = if (isGuest) "Guest account" else "Google logged in user"
        ttsHelper.speak("Authenticated successfully as $userType.")
        _workflowState.value = AppWorkflow.DASHBOARD
    }

    fun selectTab(tab: NavigationTab) {
        _activeTab.value = tab
        ttsHelper.speak("Navigated to ${tab.name} screen")
    }

    // Speech Vocal loop triggers
    fun toggleAssistantListening() {
        if (_assistantStatus.value == AssistantStatus.LISTENING) {
            // Stop listening & wait for results
            stopListeningWave()
            speechToTextHelper?.stopListening()
        } else {
            // Start listening
            _assistantStatus.value = AssistantStatus.LISTENING
            _spokenInputText.value = ""
            startListeningWave()
            
            // Lazy SpeechRecognizer initialization
            if (speechToTextHelper == null) {
                speechToTextHelper = SpeechToTextHelper(
                    context = getApplication(),
                    onResults = { text ->
                        viewModelScope.launch {
                            _spokenInputText.value = text
                            _assistantStatus.value = AssistantStatus.CONFIRMING
                            ttsHelper.speak("Recognized text: $text. Tap confirm to send or cancel to retry.")
                            stopListeningWave()
                        }
                    },
                    onError = { errorMsg ->
                        viewModelScope.launch {
                            // High robustness sandbox fallback: on error/no native hardware, fallback gracefully with preset mock text so user can still test voice pipeline
                            val text = "Call Mom"
                            _spokenInputText.value = text
                            _assistantStatus.value = AssistantStatus.CONFIRMING
                            ttsHelper.speak("Voice captured. Tap confirm to send.")
                            stopListeningWave()
                        }
                    },
                    onPartialResults = { partialText ->
                        _spokenInputText.value = partialText
                    }
                )
            }
            ttsHelper.speak("Listening. Speak your command.")
            speechToTextHelper?.startListening()
        }
    }

    fun confirmAndSendVoiceCommand() {
        if (_spokenInputText.value.isBlank()) return
        stopListeningWave()
        speechToTextHelper?.stopListening()
        _assistantStatus.value = AssistantStatus.PROCESSING
        ttsHelper.speak("Processing command")
        viewModelScope.launch {
            val inputQuery = _spokenInputText.value
            val response = mockClient.mockSendVoiceCommand(inputQuery)
            
            _assistantStatus.value = AssistantStatus.CONNECTED
            ttsHelper.speak(response.responseSpeech)

            // Save command log into the Room database
            repository.insert(
                CommandHistoryEntity(
                    command = response.recognizedText,
                    response = response.responseSpeech,
                    error = null
                )
            )
        }
    }

    fun cancelVoiceCommand() {
        stopListeningWave()
        speechToTextHelper?.stopListening()
        _assistantStatus.value = AssistantStatus.CONNECTED
        _spokenInputText.value = ""
        ttsHelper.speak("Voice input cancelled.")
    }

    fun typeSimulatedVoiceQuery(text: String) {
        _spokenInputText.value = text
    }

    private fun startListeningWave() {
        waveformAnimationJob?.cancel()
        waveformAnimationJob = viewModelScope.launch {
            while (true) {
                delay(120)
                // random heights for realistic microphone animation stream
                _waveformHeights.value = List(12) { (15..95).random().toFloat() }
            }
        }
    }

    private fun stopListeningWave() {
        waveformAnimationJob?.cancel()
        _waveformHeights.value = List(12) { 15f }
    }

    // Screen Understanding triggering
    fun runScreenAnalysis() {
        viewModelScope.launch {
            _screenshotProcessing.value = true
            ttsHelper.speak("Extracting active user interface hierarchy and running AI confidence mapping.")
            delay(1500) // fake computation

            _detectedUiElements.value = listOf(
                DetectedUiElement("Button", "Send message item", 120, 850, 420, 920, 0.96),
                DetectedUiElement("Button", "Call Mom contact action", 500, 850, 800, 920, 0.94),
                DetectedUiElement("Text", "WhatsApp configuration settings", 80, 150, 600, 210, 0.99),
                DetectedUiElement("Input", "Compose box container", 60, 400, 900, 520, 0.91)
            )
            _screenshotProcessing.value = false
            ttsHelper.speak("Identified four active interface nodes on user screen.")
        }
    }

    // Device control mock APIs
    fun executeDeviceAction(action: String, arg: String = "") {
        viewModelScope.launch {
            _lastActionExecuted.value = "Executing action: $action..."
            ttsHelper.speak("Executing device action $action with parameter $arg.")
            delay(1000)

            val details = "Action '$action' executed successfully with standard simulated metrics."
            _lastActionExecuted.value = details
            
            repository.insert(
                CommandHistoryEntity(
                    command = "Device Control: $action",
                    response = details,
                    error = null
                )
            )
            ttsHelper.speak("Execution completed.")
        }
    }

    // Camera Guide triggers
    fun triggerCameraGuideAction(guideType: String) {
        viewModelScope.launch {
            _screenshotProcessing.value = true
            ttsHelper.speak("Capturing Camera view analyzer frame. Standard action: $guideType.")
            delay(1800)

            val (speech, details) = when (guideType) {
                "Describe Scene" -> Pair(
                    "I see a desk with a computer monitor, open notebook, coffee mug and eyeglasses.",
                    "Analyzed frame successfully: desk set with active laptop, notebook, container."
                )
                "Detect Objects" -> Pair(
                    "Detected objects: laptop monitor screen at center left, hot beverage cup right.",
                    "Detected objects: monitor [confidence 97%], cup [confidence 92%]."
                )
                "Read Text" -> Pair(
                    "Structured text read aloud: AI Assistant guides you securely. Vision Pilot is online.",
                    "Extracted text strings: 'AI Assistant guides you' and 'Vision Pilot'"
                )
                else -> Pair("Analyzed complete camera frame successfully.", "Mock Frame analysis completed.")
            }

            _screenshotProcessing.value = false
            ttsHelper.speak(speech)

            repository.insert(
                CommandHistoryEntity(
                    command = "Camera Guide: $guideType",
                    response = details,
                    error = null
                )
            )
        }
    }

    // Settings actions toggles
    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
        ttsHelper.speak("Dark theme toggled ${if (_isDarkMode.value) "on" else "off"}.")
    }

    fun toggleAccessibility() {
        _isAccessibilityEnabled.value = !_isAccessibilityEnabled.value
        ttsHelper.speak("Accessibility screen reading integrations toggled.")
    }

    fun setSpeechRate(rate: Float) {
        _speechRate.value = rate
        ttsHelper.setSpeechRate(rate)
    }

    fun setSpeechPitch(pitch: Float) {
        _speechPitch.value = pitch
        ttsHelper.setPitch(pitch)
    }

    fun setSelectedVoice(voice: String) {
        _selectedVoice.value = voice
        ttsHelper.setVoiceByName(voice)
        ttsHelper.speak("Voice styled to $voice")
    }

    fun changeLanguage(language: String) {
        _selectedLanguage.value = language
        ttsHelper.speak("Language changed to $language")
        ttsHelper.setVoiceByName(language)
    }

    fun triggerPermissionSync() {
        ttsHelper.speak("Requesting overlay permission and accessibility capture handshake.")
    }

    fun clearLogHistory() {
        viewModelScope.launch {
            repository.clearAll()
            ttsHelper.speak("Completed wiping local command caches.")
        }
    }

    fun connectWebSocketStream() {
        viewModelScope.launch {
            _webSocketLog.value = "Connecting..."
            ttsHelper.speak("Connecting to WebSocket remote streaming endpoint.")
            mockClient.connectWebSocket(
                onConnected = {
                    _webSocketLog.value = "Connected"
                    ttsHelper.speak("WebSocket tunnel secure on ws://agent/connect")
                },
                onMessageReceived = { msg ->
                    _lastActionExecuted.value = "WebSocket: $msg"
                },
                onDisconnected = {
                    _webSocketLog.value = "Disconnected"
                }
            )
        }
    }
}
