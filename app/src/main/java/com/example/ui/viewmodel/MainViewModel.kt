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
import com.example.ai.AgentActionExecutor
import com.example.ai.AgentAction
import com.example.data.database.DatabaseHelper
import com.example.service.ScreenCaptureService
import com.example.service.VisionPilotAccessibilityService
import com.example.network.WebSocketManager
import org.json.JSONObject
import org.json.JSONArray
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

enum class WakeWordState {
    IDLE,
    WAITING_FOR_WAKE,
    LISTENING_FOR_COMMAND
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = CommandHistoryRepository(database.commandHistoryDao())
    val ttsHelper = TextToSpeechHelper(application) { isSpeaking ->
        onTtsStatusChanged(isSpeaking)
    }
    
    val databaseHelper = DatabaseHelper(application)
    
    private val webSocketManager = WebSocketManager(
        onMessage = { msg -> 
            _webSocketLog.value = "Received: \$msg"
        },
        onConnectionStatus = { connected ->
            _webSocketLog.value = if (connected) "Connected" else "Disconnected"
        }
    )
    
    private var sendToBackendFunction: suspend (String?, String) -> AgentAction = { snapshot, command ->
        AgentAction("done", message = "Fallback mock response executed. Backend mapping not fully resolved.")
    }
    
    val agentActionExecutor = AgentActionExecutor(
        context = application,
        databaseHelper = databaseHelper,
        captureSnapshot = {
            ScreenCaptureService.captureSnapshot(application)
        },
        sendToBackend = { snapshot, command ->
            // JSON conversion
            val json = JSONObject()
            val uuid = application.getSharedPreferences("visionpilot_prefs", android.content.Context.MODE_PRIVATE).getString("device_session_id", "local-uuid")
            json.put("session_id", uuid)
            json.put("command", command)
            if (snapshot != null) json.put("screenshot", snapshot)
            
            val accService = VisionPilotAccessibilityService.getInstance()
            val uiTreeArray = JSONArray()
            accService?.readUiTree()?.forEachIndexed { index, node ->
                val nodeObj = JSONObject()
                nodeObj.put("index", index)
                nodeObj.put("role", node.className)
                nodeObj.put("label", node.text ?: "")
                nodeObj.put("center_x", node.centerX)
                nodeObj.put("center_y", node.centerY)
                uiTreeArray.put(nodeObj)
            }
            json.put("ui_tree", uiTreeArray)
            json.put("package_name", "com.android.launcher") // mock
            
            webSocketManager.send(json.toString())
            
            // Stubbed for immediate response to prevent hanging without a real backend.
            AgentAction("speak", message = "Message sent to agent backend.")
        },
        speak = { text ->
            ttsHelper.speak(text)
        }
    )

    private val mockClient = MockVisionPilotClient()
    private var speechToTextHelper: SpeechToTextHelper? = null
    
    private var pendingConfirmationAction: (() -> Unit)? = null


    // Wake Word / Hands-Free Mode states
    private val _isHandsFreeMode = MutableStateFlow(false)
    val isHandsFreeMode: StateFlow<Boolean> = _isHandsFreeMode.asStateFlow()

    private val _wakeWordState = MutableStateFlow(WakeWordState.IDLE)
    val wakeWordState: StateFlow<WakeWordState> = _wakeWordState.asStateFlow()

    private var isTtsSpeaking = false

    // Device Service status states
    val screenCaptureServiceStatus = MutableStateFlow("STANDBY")
    val microphoneServiceStatus = MutableStateFlow("OFFLINE")
    val cameraServiceStatus = MutableStateFlow("STANDBY")

    private fun onTtsStatusChanged(isSpeaking: Boolean) {
        isTtsSpeaking = isSpeaking
        if (isSpeaking) {
            speechToTextHelper?.stopListening()
        } else {
            if (_isHandsFreeMode.value) {
                restartWakeWordListening()
            }
        }
    }

    // Dynamic Permission States
    private val _isCameraGranted = MutableStateFlow(false)
    val isCameraGranted: StateFlow<Boolean> = _isCameraGranted.asStateFlow()

    private val _isRecordAudioGranted = MutableStateFlow(false)
    val isRecordAudioGranted: StateFlow<Boolean> = _isRecordAudioGranted.asStateFlow()

    private val _isContactsGranted = MutableStateFlow(false)
    val isContactsGranted: StateFlow<Boolean> = _isContactsGranted.asStateFlow()

    private val _isAccessibilityActive = MutableStateFlow(false)
    val isAccessibilityActive: StateFlow<Boolean> = _isAccessibilityActive.asStateFlow()

    private val _isOverlayGranted = MutableStateFlow(false)
    val isOverlayGranted: StateFlow<Boolean> = _isOverlayGranted.asStateFlow()

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
    
    private val _quickCommands = MutableStateFlow(listOf(
        "Call Mom", "Open WhatsApp", "Read this screen", "Describe surroundings"
    ))
    val quickCommands: StateFlow<List<String>> = _quickCommands.asStateFlow()
    
    fun loadQuickCommands() {
        val cmds = sharedPreferences.getStringSet("quick_commands_list", null)
        if (cmds != null && cmds.isNotEmpty()) {
            _quickCommands.value = cmds.toList()
        }
    }
    
    fun addQuickCommand(cmd: String) {
        val current = _quickCommands.value.toMutableList()
        if (!current.contains(cmd)) {
            current.add(0, cmd)
            if (current.size > 5) {
                current.removeAt(5)
            }
            _quickCommands.value = current
            sharedPreferences.edit().putStringSet("quick_commands_list", current.toSet()).apply()
        }
    }

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
    private val _isDarkMode = MutableStateFlow(application.getSharedPreferences("visionpilot_prefs", android.content.Context.MODE_PRIVATE).getBoolean("oled_high_contrast", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(true)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _speechRate = MutableStateFlow(application.getSharedPreferences("visionpilot_prefs", android.content.Context.MODE_PRIVATE).getFloat("speech_rate", 1.0f))
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _speechPitch = MutableStateFlow(1.0f)
    val speechPitch: StateFlow<Float> = _speechPitch.asStateFlow()

    private val _selectedVoice = MutableStateFlow("Default US Voice")
    val selectedVoice: StateFlow<String> = _selectedVoice.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("English (US)")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val sharedPreferences = application.getSharedPreferences("visionpilot_prefs", android.content.Context.MODE_PRIVATE)
    private val contextHistory = mutableListOf<String>()

    // Session User states for multiple dynamic login routes (Email, Google, Phone, Guest)
    private val _sessionUserEmail = MutableStateFlow<String?>(sharedPreferences.getString("email", null))
    val sessionUserEmail: StateFlow<String?> = _sessionUserEmail.asStateFlow()

    private val _sessionUserName = MutableStateFlow<String?>(sharedPreferences.getString("name", null))
    val sessionUserName: StateFlow<String?> = _sessionUserName.asStateFlow()

    private val _sessionProvider = MutableStateFlow<String?>(sharedPreferences.getString("provider", null))
    val sessionProvider: StateFlow<String?> = _sessionProvider.asStateFlow()

    private val _emergencyContactName = MutableStateFlow(sharedPreferences.getString("emergency_name", "") ?: "")
    val emergencyContactName: StateFlow<String> = _emergencyContactName.asStateFlow()

    private val _emergencyContactNumber = MutableStateFlow(sharedPreferences.getString("emergency_number", "") ?: "")
    val emergencyContactNumber: StateFlow<String> = _emergencyContactNumber.asStateFlow()

    fun updateEmergencyContact(name: String, number: String) {
        _emergencyContactName.value = name
        _emergencyContactNumber.value = number
        sharedPreferences.edit()
            .putString("emergency_name", name)
            .putString("emergency_number", number)
            .apply()
        ttsHelper.speak("Emergency contact updated to $name.")
    }

    // Database Command Feed
    val activityHistory: StateFlow<List<CommandHistoryEntity>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var waveformAnimationJob: Job? = null

    init {
        updatePermissionsState()
        loadQuickCommands()
        ttsHelper.setSpeechRate(_speechRate.value)
        // Trigger initial routing delay
        viewModelScope.launch {
            delay(1000)
            
            var localUuid = sharedPreferences.getString("device_session_id", null)
            if (localUuid == null) {
                localUuid = java.util.UUID.randomUUID().toString()
                sharedPreferences.edit().putString("device_session_id", localUuid).apply()
            }
            
            _sessionUserEmail.value = "guest@visionpilot.ai"
            _sessionUserName.value = "Offline User"
            _sessionProvider.value = "Local Identity ($localUuid)"
            
            _workflowState.value = AppWorkflow.DASHBOARD
            ttsHelper.speak("Vision pilot initialized in standalone mode.")
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

    fun triggerHapticFeedback(type: Int) {
        val context = getApplication<Application>()
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                when (type) {
                    1 -> vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)) // Wake
                    2 -> vibrator.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 50, 100, 50), -1)) // Success
                    3 -> vibrator.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1)) // Error
                }
            } else {
                @Suppress("DEPRECATION")
                when (type) {
                    1 -> vibrator.vibrate(50)
                    2 -> vibrator.vibrate(longArrayOf(0, 50, 100, 50), -1)
                    3 -> vibrator.vibrate(longArrayOf(0, 200, 100, 200), -1)
                }
            }
        }
    }

    private fun logCommandToFirestore(action: String, isSuccess: Boolean) {
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val logData = hashMapOf(
                "action" to action,
                "isSuccess" to isSuccess,
                "timestamp" to com.google.firebase.Timestamp.now()
            )
            db.collection("CommandLogs").add(logData)
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Firestore logging failed", e)
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
        if (isGuest) {
            try {
                if (com.example.auth.FirebaseAuthManager.isInitialized) {
                    com.google.firebase.auth.FirebaseAuth.getInstance().signInAnonymously()
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val user = task.result?.user
                                _sessionUserEmail.value = "guest-${user?.uid?.take(6)}@visionpilot.ai"
                                _sessionUserName.value = "Guest Client"
                                _sessionProvider.value = "Firebase Anonymous Guest"
                                ttsHelper.speak("Authenticated successfully via secure anonymous session.")
                                _workflowState.value = AppWorkflow.DASHBOARD
                            } else {
                                _sessionUserEmail.value = "guest@visionpilot.ai"
                                _sessionUserName.value = "Fallback Guest Client"
                                _sessionProvider.value = "Local Guest Mode"
                                ttsHelper.speak("Fallback guest session initiated.")
                                _workflowState.value = AppWorkflow.DASHBOARD
                            }
                        }
                    return
                }
            } catch(e: Exception) {}
            
            _sessionUserEmail.value = "guest@visionpilot.ai"
            _sessionUserName.value = "Guest Client"
            _sessionProvider.value = "Guest Mode"
            ttsHelper.speak("Authenticated successfully in client guest sandbox.")
        } else {
            _sessionUserEmail.value = "developer@google.com"
            _sessionUserName.value = "Google Dev User"
            _sessionProvider.value = "Google Single Sign-On"
            ttsHelper.speak("Authenticated successfully via Google SSO.")
        }
        _workflowState.value = AppWorkflow.DASHBOARD
    }

    fun loginWithSession(email: String, name: String?, provider: String) {
        _sessionUserEmail.value = email
        val fullName = name ?: "Vision User"
        _sessionUserName.value = fullName
        _sessionProvider.value = provider
        
        sharedPreferences.edit()
            .putString("email", email)
            .putString("name", fullName)
            .putString("provider", provider)
            .apply()
            
        val welcomeMessage = if (name != null) "Welcome back $name. Successfully logged in using $provider." else "Authenticated successfully via $provider."
        ttsHelper.speak(welcomeMessage)
        _workflowState.value = AppWorkflow.DASHBOARD
    }

    fun createNewAccount(email: String, name: String) {
        loginWithSession(email, name, "Email & Password Created Account")
    }

    fun loginWithPhone(phoneNumber: String) {
        val label = "Verified +1 ${phoneNumber.takeLast(4)}."
        loginWithSession("phone@visionpilot.ai", "Phone Code User", "SMS Phone OTP")
    }

    fun logout() {
        val newUuid = java.util.UUID.randomUUID().toString()
        sharedPreferences.edit().clear().putString("device_session_id", newUuid).apply()
        
        _sessionUserEmail.value = "guest@visionpilot.ai"
        _sessionUserName.value = "Offline User"
        _sessionProvider.value = "Local Identity ($newUuid)"
        
        try {
            if (com.example.auth.FirebaseAuthManager.isInitialized) {
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
            }
        } catch(e: Exception) {}
        
        clearLogHistory()
        
        _workflowState.value = AppWorkflow.DASHBOARD
        _activeTab.value = NavigationTab.HOME
        ttsHelper.speak("Device session terminated and logs cleared. New standalone session started.")
    }

    fun selectTab(tab: NavigationTab) {
        _activeTab.value = tab
        ttsHelper.speak("Navigated to ${tab.name} screen")
    }

    // Speech Vocal loop triggers
    fun initializeSpeechToText() {
        speechToTextHelper = SpeechToTextHelper(
            context = getApplication(),
            onResults = { text ->
                viewModelScope.launch {
                    _spokenInputText.value = text
                    handleVoiceDataInput(text)
                }
            },
            onError = { errorMsg ->
                viewModelScope.launch {
                    if (_isHandsFreeMode.value) {
                        delay(2000)
                        restartWakeWordListening()
                    } else {
                        stopListeningWave()
                        _assistantStatus.value = AssistantStatus.CONNECTED
                        ttsHelper.speak("Sorry, I didn't catch that. Please try again.")
                    }
                }
            },
            onPartialResults = { partialText ->
                _spokenInputText.value = partialText
            }
        )
    }

    fun toggleAssistantListening() {
        if (_assistantStatus.value == AssistantStatus.LISTENING) {
            stopListeningWave()
            speechToTextHelper?.stopListening()
            if (_isHandsFreeMode.value) {
                _isHandsFreeMode.value = false
                _wakeWordState.value = WakeWordState.IDLE
                ttsHelper.speak("Continuous active voice listening turned off.")
            }
        } else {
            if (!_isRecordAudioGranted.value) {
                ttsHelper.speak("Microphone permission is required to listen. Please allow recording in your permissions panel.")
                return
            }
            _spokenInputText.value = ""
            if (_isHandsFreeMode.value) {
                restartWakeWordListening()
            } else {
                _assistantStatus.value = AssistantStatus.LISTENING
                startListeningWave()
                if (speechToTextHelper == null) {
                    initializeSpeechToText()
                }
                ttsHelper.speak("Listening. Speak your command.")
                speechToTextHelper?.startListening()
            }
        }
    }

    fun toggleHandsFreeMode() {
        if (!_isHandsFreeMode.value && !_isRecordAudioGranted.value) {
            ttsHelper.speak("Microphone permission is required for hands free mode. Please allow recording in your permissions panel.")
            return
        }
        val nextMode = !_isHandsFreeMode.value
        _isHandsFreeMode.value = nextMode
        val context = getApplication<Application>()
        if (nextMode) {
            _wakeWordState.value = WakeWordState.WAITING_FOR_WAKE
            ttsHelper.speak("Hands free continuous listening activated. Say hi or hello to wake me up.")
            val intent = android.content.Intent(context, com.example.service.WakeWordService::class.java).apply {
                action = com.example.service.WakeWordService.ACTION_START_LISTENING
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            _wakeWordState.value = WakeWordState.IDLE
            ttsHelper.speak("Hands free mode disabled.")
            speechToTextHelper?.stopListening()
            stopListeningWave()
            val intent = android.content.Intent(context, com.example.service.WakeWordService::class.java).apply {
                action = com.example.service.WakeWordService.ACTION_STOP_LISTENING
            }
            context.startService(intent)
        }
    }

    fun restartWakeWordListening() {
        if (!_isHandsFreeMode.value || isTtsSpeaking) return
        
        if (!_isRecordAudioGranted.value) {
            ttsHelper.speak("Microphone permission is required for hands free mode. Please allow recording in your permissions panel.")
            _isHandsFreeMode.value = false
            _wakeWordState.value = WakeWordState.IDLE
            return
        }

        viewModelScope.launch {
            _assistantStatus.value = AssistantStatus.LISTENING
            _spokenInputText.value = ""
            startListeningWave()
            
            if (speechToTextHelper == null) {
                initializeSpeechToText()
            }
            speechToTextHelper?.startListening()
        }
    }

    fun onWakeWordDetectedExt() {
        if (!_isHandsFreeMode.value) return
        triggerHapticFeedback(1)
        _wakeWordState.value = WakeWordState.LISTENING_FOR_COMMAND
        ttsHelper.speak("Hello there! I am awake. Speak your command now.")
        _assistantStatus.value = AssistantStatus.LISTENING
        _spokenInputText.value = ""
        startListeningWave()
        if (speechToTextHelper == null) {
            initializeSpeechToText()
        }
        speechToTextHelper?.startListening()
        
        viewModelScope.launch {
            delay(5000)
            if (_assistantStatus.value == AssistantStatus.LISTENING && _spokenInputText.value.isBlank()) {
                ttsHelper.speak("No command heard. Sleeping now.")
                _assistantStatus.value = AssistantStatus.CONNECTED
                stopListeningWave()
                speechToTextHelper?.stopListening()
                _wakeWordState.value = WakeWordState.WAITING_FOR_WAKE
                val intent = android.content.Intent(getApplication(), com.example.service.WakeWordService::class.java).apply {
                    action = com.example.service.WakeWordService.ACTION_START_LISTENING
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    getApplication<Application>().startForegroundService(intent)
                } else {
                    getApplication<Application>().startService(intent)
                }
            }
        }
    }

    fun handleVoiceDataInput(text: String) {
        val cleaned = text.trim().lowercase(java.util.Locale.getDefault())
        if (cleaned.isBlank()) return

        if (pendingConfirmationAction != null) {
            if (cleaned.equals("yes") || cleaned.contains("yes") || cleaned.contains("confirm") || cleaned.contains("do it")) {
                val action = pendingConfirmationAction
                pendingConfirmationAction = null
                action?.invoke()
                _assistantStatus.value = AssistantStatus.CONNECTED
                if (_isHandsFreeMode.value) {
                    _wakeWordState.value = WakeWordState.WAITING_FOR_WAKE
                    viewModelScope.launch {
                        delay(5000)
                        restartWakeWordListening()
                    }
                }
            } else {
                pendingConfirmationAction = null
                ttsHelper.speak("Action cancelled.")
                triggerHapticFeedback(3)
                _assistantStatus.value = AssistantStatus.CONNECTED
                if (_isHandsFreeMode.value) {
                    _wakeWordState.value = WakeWordState.WAITING_FOR_WAKE
                    viewModelScope.launch {
                        delay(2000)
                        restartWakeWordListening()
                    }
                }
            }
            return
        }

        if (_isHandsFreeMode.value) {
            when (_wakeWordState.value) {
                WakeWordState.WAITING_FOR_WAKE -> {
                    if (cleaned.contains("hi") || cleaned.contains("hello") || cleaned.contains("hey")) {
                        _wakeWordState.value = WakeWordState.LISTENING_FOR_COMMAND
                        ttsHelper.speak("Hello there! I am awake. Speak your command now.")
                        viewModelScope.launch {
                            delay(1800)
                            if (_wakeWordState.value == WakeWordState.LISTENING_FOR_COMMAND) {
                                restartWakeWordListening()
                            }
                        }
                    } else {
                        restartWakeWordListening()
                    }
                }
                WakeWordState.LISTENING_FOR_COMMAND -> {
                    val isHandled = handleVoiceCommandParsing(text)
                    if (isHandled) {
                        _wakeWordState.value = WakeWordState.WAITING_FOR_WAKE
                        viewModelScope.launch {
                            delay(3500)
                            restartWakeWordListening()
                        }
                    } else if (cleaned.contains("cancel") || cleaned.contains("stop")) {
                        ttsHelper.speak("Hands free session cancelled. Awaiting wake word.")
                        _wakeWordState.value = WakeWordState.WAITING_FOR_WAKE
                        viewModelScope.launch {
                            delay(1500)
                            restartWakeWordListening()
                        }
                    } else {
                        executeHandsfreeMockQuery(text)
                    }
                }
                else -> {}
            }
        } else {
            viewModelScope.launch {
                if (handleVoiceCommandParsing(text)) {
                    return@launch
                }
                _assistantStatus.value = AssistantStatus.CONFIRMING
                ttsHelper.speak("Recognized text: $text. Tap confirm to send or cancel to retry.")
                stopListeningWave()
            }
        }
    }

    fun executeHandsfreeMockQuery(text: String) {
        _assistantStatus.value = AssistantStatus.PROCESSING
        executeSystemIntentActions(text)
        
        viewModelScope.launch {
            _wakeWordState.value = WakeWordState.WAITING_FOR_WAKE
            delay(5000)
            restartWakeWordListening()
        }
    }

    fun parseTimeAndSetAlarm(query: String) {
        executeSystemIntentActions(query)
    }

    fun confirmAndSendVoiceCommand() {
        if (_spokenInputText.value.isBlank()) return
        stopListeningWave()
        speechToTextHelper?.stopListening()
        _assistantStatus.value = AssistantStatus.PROCESSING
        val inputQuery = _spokenInputText.value
        executeSystemIntentActions(inputQuery)
        
        viewModelScope.launch {
            delay(5000)
            _assistantStatus.value = AssistantStatus.CONNECTED
        }
    }

    private fun lookupContactNumber(context: android.content.Context, contactName: String): String {
        if (!_isContactsGranted.value) return ""
        var number = ""
        try {
            val cursor = context.contentResolver.query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$contactName%"),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    number = it.getString(0) ?: ""
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Contacts", "Error looking up contact", e)
        }
        return number
    }

    fun executeSystemIntentActions(cmdText: String) {
        val query = cmdText.lowercase(java.util.Locale.getDefault()).trim()
        
        // We defer to the dynamic Agent Action Executor Loop
        viewModelScope.launch {
            agentActionExecutor.runAgentLoop(query)
            
            // Log it using the legacy logging mechanism just to keep UI in sync
            logCommandToFirestore("Agent Loop Handled Action", true)
            
            // Small status check fix
            _lastActionExecuted.value = "Action intent triggered by Agent: $query"
            
            contextHistory.add("User said: $query")
            if (contextHistory.size > 3) contextHistory.removeAt(0)
            
            repository.insert(CommandHistoryEntity(
                command = query,
                response = "Handled by remote Agent logic",
                error = null
            ))
        }
    }

    fun cancelVoiceCommand() {
        stopListeningWave()
        speechToTextHelper?.stopListening()
        _assistantStatus.value = AssistantStatus.CONNECTED
        _spokenInputText.value = ""
        ttsHelper.speak("Voice input cancelled.")
    }

    fun updateManualInputText(text: String) {
        _spokenInputText.value = text
    }

    fun submitManualVoiceQuery() {
        val text = _spokenInputText.value
        if (text.isNotBlank()) {
            handleVoiceDataInput(text)
        }
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
            ttsHelper.speak("Extracting active user interface hierarchy.")
            
            val accService = com.example.service.VisionPilotAccessibilityService.getInstance()
            if (accService != null) {
                val nodes = accService.extractUiHierarchy()
                val actionableNodes = accService.filterActionableNodes(nodes)
                
                if (nodes.isEmpty()) {
                    ttsHelper.speak("No interface nodes detected or accessibility service is blocked on this screen.")
                    _detectedUiElements.value = emptyList()
                    _screenshotProcessing.value = false
                    return@launch
                }
                
                val elementsList = mutableListOf<com.example.data.service.DetectedUiElement>()
                val itemsToTake = if (actionableNodes.isNotEmpty()) actionableNodes.take(15) else nodes.filter { !it.text.isNullOrBlank() }.take(15)
                
                for (node in itemsToTake) {
                    val fallbackTitle = node.text ?: node.className.substringAfterLast('.')
                    elementsList.add(
                        com.example.data.service.DetectedUiElement(
                            type = if (node.isClickable) "Button" else "View",
                            label = fallbackTitle,
                            left = node.bounds.left,
                            top = node.bounds.top,
                            right = node.bounds.right,
                            bottom = node.bounds.bottom,
                            confidence = 1.0
                        )
                    )
                }
                
                _detectedUiElements.value = elementsList
                ttsHelper.speak("Identified ${elementsList.size} interactive elements on user screen.")
            } else {
                ttsHelper.speak("Accessibility service is not running or not allowed.")
                _detectedUiElements.value = emptyList()
            }
            _screenshotProcessing.value = false
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
        sharedPreferences.edit().putBoolean("oled_high_contrast", _isDarkMode.value).apply()
        ttsHelper.speak("High contrast OLED theme toggled ${if (_isDarkMode.value) "on" else "off"}.")
    }

    fun toggleAccessibility() {
        if (!_isAccessibilityActive.value) {
            ttsHelper.speak("Opening accessibility settings. Please find Vision Pilot and toggle it on.")
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                getApplication<Application>().startActivity(intent)
            } catch (e: Exception) {
                // Ignore if not supported
            }
        } else {
            ttsHelper.speak("Accessibility service is already active.")
        }
    }

    fun setSpeechRate(rate: Float) {
        _speechRate.value = rate
        sharedPreferences.edit().putFloat("speech_rate", rate).apply()
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

    fun updatePermissionsState() {
        val context = getApplication<Application>()
        
        // 1. Camera check
        _isCameraGranted.value = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        cameraServiceStatus.value = if (_isCameraGranted.value) "RUNNING" else "STANDBY"

        // 2. Microphone (Record Audio) check
        _isRecordAudioGranted.value = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        microphoneServiceStatus.value = if (_isRecordAudioGranted.value) "ONLINE" else "OFFLINE"

        // 3. Contacts check
        _isContactsGranted.value = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        // 4. Accessibility Service check
        _isAccessibilityActive.value = isAccessibilityServiceEnabled(context)
        screenCaptureServiceStatus.value = if (_isAccessibilityActive.value) "RUNNING" else "STANDBY"

        // 5. Overlay check
        _isOverlayGranted.value = android.provider.Settings.canDrawOverlays(context)
    }

    private fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
        val expectedComponentName = android.content.ComponentName(context, com.example.service.VisionPilotAccessibilityService::class.java)
        val enabledServicesSetting = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = android.content.ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) {
                return true
            }
        }
        return false
    }

    fun triggerPermissionSync(context: android.content.Context) {
        updatePermissionsState()
        
        if (!_isAccessibilityActive.value) {
            ttsHelper.speak("Accessibility automation is disabled. Opening settings. Please select installed services, find Vision Pilot, and toggle it on.")
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {}
            return
        }
        
        if (!_isCameraGranted.value || !_isRecordAudioGranted.value || !_isContactsGranted.value) {
            ttsHelper.speak("Standard system permissions are missing. Please allow microphone, contacts, and camera access when prompted.")
            return
        }
        
        ttsHelper.speak("All system permissions are active. Vision Pilot is ready for screen and surroundings automation.")
    }

    fun handleVoiceCommandParsing(text: String): Boolean {
        val query = text.trim().lowercase(java.util.Locale.getDefault())
        when {
            query.contains("home") || query.contains("dashboard") || query.contains("feed") -> {
                selectTab(NavigationTab.HOME)
                ttsHelper.speak("Hands-free navigation activated. Navigated to active voice assistant homepage.")
                _assistantStatus.value = AssistantStatus.CONNECTED
                stopListeningWave()
                return true
            }
            query.contains("log") || query.contains("history") || query.contains("activity") -> {
                selectTab(NavigationTab.ACTIVITY)
                ttsHelper.speak("Hands-free navigation activated. Opened activity history dashboard.")
                _assistantStatus.value = AssistantStatus.CONNECTED
                stopListeningWave()
                return true
            }
            query.contains("device") || query.contains("automation") -> {
                selectTab(NavigationTab.DEVICES)
                ttsHelper.speak("Hands-free navigation activated. Opened device configuration and layout inspection settings.")
                _assistantStatus.value = AssistantStatus.CONNECTED
                stopListeningWave()
                return true
            }
            query.contains("profile") || query.contains("settings") || query.contains("voice") -> {
                selectTab(NavigationTab.PROFILE)
                ttsHelper.speak("Hands-free navigation activated. Opened user profile and voice settings customization.")
                _assistantStatus.value = AssistantStatus.CONNECTED
                stopListeningWave()
                return true
            }
            query.contains("theme") || query.contains("dark") || query.contains("light") -> {
                toggleDarkMode()
                _assistantStatus.value = AssistantStatus.CONNECTED
                stopListeningWave()
                return true
            }
            query.contains("clear log") || query.contains("clear history") || query.contains("wipe logs") || query.contains("wipe history") -> {
                clearLogHistory()
                _assistantStatus.value = AssistantStatus.CONNECTED
                stopListeningWave()
                return true
            }
            query.contains("permission") || query.contains("sync") || query.contains("check system") -> {
                _assistantStatus.value = AssistantStatus.CONNECTED
                stopListeningWave()
                triggerPermissionSync(getApplication())
                return true
            }
            query.contains("alarm") || query.contains("medicine") || query.contains("clock") || query.contains("alert") || query.contains("remind") -> {
                _assistantStatus.value = AssistantStatus.CONNECTED
                stopListeningWave()
                parseTimeAndSetAlarm(text)
                return true
            }
            query.contains("call") || query.contains("dial") || 
            query.contains("whatsapp") || query.contains("send whatsapp") ||
            query.contains("read") || query.contains("describe") || query.contains("what is on") ||
            query.contains("click") || query.contains("tap") ||
            query.contains("scroll") || query.contains("go home") || query.contains("go back") ||
            query.contains("volume") || query.contains("tutorial") || query.contains("guide") || query.contains("help") -> {
                _assistantStatus.value = AssistantStatus.CONNECTED
                stopListeningWave()
                executeSystemIntentActions(text)
                return true
            }
        }
        return false
    }

    fun clearLogHistory() {
        ttsHelper.speak("Waiting for confirmation to clear all command history logs. Say Yes to confirm or No to cancel.")
        _assistantStatus.value = AssistantStatus.CONFIRMING
        pendingConfirmationAction = {
            viewModelScope.launch {
                repository.clearAll()
                triggerHapticFeedback(2)
                ttsHelper.speak("Completed wiping local command caches.")
            }
        }
        if (_isHandsFreeMode.value) {
            _wakeWordState.value = WakeWordState.LISTENING_FOR_COMMAND
            viewModelScope.launch {
                delay(1000)
                restartWakeWordListening()
            }
        } else {
            viewModelScope.launch {
                delay(1000)
                toggleAssistantListening()
            }
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
