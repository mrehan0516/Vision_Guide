package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.example.data.database.CommandHistoryEntity
import com.example.data.service.DetectedUiElement
import com.example.ui.theme.*
import com.example.ui.viewmodel.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@Composable
fun VisionPilotApp(viewModel: MainViewModel) {
    val workflow by viewModel.workflowState.collectAsState()

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Crossfade(targetState = workflow, label = "WorkflowTransition") { state ->
            when (state) {
                AppWorkflow.SPLASH -> SplashScreen()
                AppWorkflow.ONBOARDING -> OnboardingScreen(viewModel)
                AppWorkflow.LOGIN -> LoginScreen(viewModel)
                AppWorkflow.DASHBOARD -> MainDashboardScreen(viewModel)
            }
        }
    }
}

// ================= SPLASH SCREEN =================
@Composable
fun SplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "SplashTransition")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OLEDBlack)
            .semantics { contentDescription = "Vision Pilot Splash Loading Screen" },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(PrimaryNeonBlue, DarkGreyBg)))
                    .semantics { contentDescription = "VisionPilot Glowing Logo Icon" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    tint = SlateLightText,
                    modifier = Modifier.size(54.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "VISION PILOT",
                style = MaterialTheme.typography.displayMedium.copy(color = SlateLightText),
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                modifier = Modifier.semantics { contentDescription = "Vision Pilot App Header Label" }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "AI Assistive Agent Gateway",
                style = MaterialTheme.typography.bodyMedium.copy(color = SlateMutedText),
                modifier = Modifier.semantics { contentDescription = "Subtitle description" }
            )
            Spacer(modifier = Modifier.height(48.dp))
            CircularProgressIndicator(
                color = PrimaryNeonBlue,
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = "Loading environment resources" }
            )
        }
    }
}

// ================= ONBOARDING SCREEN =================
@Composable
fun OnboardingScreen(viewModel: MainViewModel) {
    val pageIndex by viewModel.onboardingIndex.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkGreyBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .semantics { contentDescription = "Onboarding flow screen" }
    ) {
        // Upper Skip segment
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = { viewModel.skipOnboarding() },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("skip_button")
            ) {
                Text(
                    text = "SKIP",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = SlateMutedText,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Center Content Carousel State
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(4f),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(targetState = pageIndex, label = "OnboardingPageTransition") { page ->
                OnboardingContent(page = page)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Onboarding Indicators Dot Indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            for (i in 0..3) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (i == pageIndex) PrimaryNeonBlue else CardBorder)
                )
            }
        }

        // Action Buttons Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pageIndex > 0) {
                OutlinedButton(
                    onClick = { viewModel.previousOnboarding() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateLightText),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("onboarding_back_button")
                ) {
                    Text(
                        text = "BACK",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
            }

            Button(
                onClick = { viewModel.nextOnboarding() },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeonBlue),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("onboarding_next_button")
            ) {
                Text(
                    text = if (pageIndex == 3) "GET STARTED" else "NEXT",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
fun OnboardingContent(page: Int) {
    val (icon, title, descString) = when (page) {
        0 -> Triple(
            Icons.Default.SupportAgent,
            "Your AI Guide",
            "A vocal interface ready to assist you anytime. We act as your digital assistant companion to make smartphone use easy."
        )
        1 -> Triple(
            Icons.Default.PhotoCamera,
            "See the world through AI",
            "Utilize your camera feed to read letters aloud, recognize daily utilities and identify active physical settings."
        )
        2 -> Triple(
            Icons.Default.SettingsVoice,
            "Control your phone with voice",
            "Perform actions, type content, dial calls or extract notifications easily by simple voice commands."
        )
        3 -> Triple(
            Icons.Default.Security,
            "Permissions Walkthrough",
            "We require standard Android Accessibility and Camera access to capture screen feeds and translate actions securely."
        )
        else -> Triple(Icons.Default.Error, "", "")
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(CardSlate)
                .border(2.dp, CardBorder, RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SecondaryNeonCyan,
                modifier = Modifier.size(64.dp)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.displayMedium.copy(color = SlateLightText),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { contentDescription = "Stage title: $title" }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = descString,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = SlateMutedText,
                lineHeight = 26.sp
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { contentDescription = "Step details: $descString" }
        )
    }
}

// ================= LOGIN SCREEN =================
@Composable
fun LoginScreen(viewModel: MainViewModel) {
    var activeAuthTab by remember { mutableStateOf(0) } // 0: Email, 1: Phone, 2: Google, 3: Guest
    var isSignUpMode by remember { mutableStateOf(false) }

    // Email states
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }

    // Phone states
    var phoneInput by remember { mutableStateOf("") }
    var phoneInputError by remember { mutableStateOf<String?>(null) }
    var otpSentCode by remember { mutableStateOf<String?>(null) }
    var verificationId by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }
    var otpInputError by remember { mutableStateOf<String?>(null) }
    var isOtpVerifying by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val localContext = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OLEDBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .semantics { contentDescription = "Security Access Portal" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CardSlate),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.VpnKey,
                contentDescription = null,
                tint = PrimaryNeonBlue,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "VisionPilot Portal",
            style = MaterialTheme.typography.headlineLarge.copy(
                color = SlateLightText,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            ),
            textAlign = TextAlign.Center
        )

        Text(
            text = "Establish secure credentials link to connect screen automation",
            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMutedText),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // AUTHENTICATION TAB CONTROLLERS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardSlate)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val tabsLabel = listOf("EMAIL", "PHONE", "GOOGLE", "GUEST")
            tabsLabel.forEachIndexed { index, title ->
                val isSelected = activeAuthTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) PrimaryNeonBlue else Color.Transparent)
                        .semantics { 
                            contentDescription = "Select $title authentication tab"
                        }
                        .clickable {
                            activeAuthTab = index
                            val speakText = when (index) {
                                0 -> "Selected email authentication credentials entry."
                                1 -> "Selected phone verification and code entry."
                                2 -> "Selected Google single sign-on hub chooser."
                                else -> "Selected secure localized client guest entry."
                            }
                            viewModel.ttsHelper.speak(speakText)
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (isSelected) OLEDBlack else SlateMutedText,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // DYNAMIC CONTENT ACCORDING TO AUTH TAB
        when (activeAuthTab) {
            0 -> {
                // EMAIL & PASSWORD PANEL (SIGN IN / SIGN UP)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, CardBorder, RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardSlate),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = if (isSignUpMode) "CREATE NEW ACCOUNT" else "SECURE EMAIL LOGIN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = PulseVocalCyan,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isSignUpMode) {
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Display Name") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryNeonBlue,
                                    unfocusedBorderColor = CardBorder,
                                    focusedLabelColor = PrimaryNeonBlue,
                                    unfocusedLabelColor = SlateMutedText,
                                    focusedTextColor = SlateLightText,
                                    unfocusedTextColor = SlateLightText
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_name_field"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = {
                                emailInput = it
                                emailError = null
                            },
                            label = { Text("Email Address") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryNeonBlue,
                                unfocusedBorderColor = CardBorder,
                                focusedLabelColor = PrimaryNeonBlue,
                                unfocusedLabelColor = SlateMutedText,
                                focusedTextColor = SlateLightText,
                                unfocusedTextColor = SlateLightText
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_email_field"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (emailError != null) {
                            Text(
                                text = emailError!!,
                                color = WarningRed,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Secret Password") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryNeonBlue,
                                unfocusedBorderColor = CardBorder,
                                focusedLabelColor = PrimaryNeonBlue,
                                unfocusedLabelColor = SlateMutedText,
                                focusedTextColor = SlateLightText,
                                unfocusedTextColor = SlateLightText
                            ),
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_password_field"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (isSignUpMode) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = confirmPasswordInput,
                                onValueChange = { confirmPasswordInput = it },
                                label = { Text("Confirm Password") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryNeonBlue,
                                    unfocusedBorderColor = CardBorder,
                                    focusedLabelColor = PrimaryNeonBlue,
                                    unfocusedLabelColor = SlateMutedText,
                                    focusedTextColor = SlateLightText,
                                    unfocusedTextColor = SlateLightText
                                ),
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_confirm_password_field"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                if (emailInput.isBlank() || !emailInput.contains("@")) {
                                    emailError = "Please input a valid email registration"
                                    viewModel.ttsHelper.speak("Incorrect email format check. Please revalidate input fields.")
                                    return@Button
                                }
                                if (passwordInput.length < 5) {
                                    viewModel.ttsHelper.speak("Password is too brief. Standard security requirement is at least five characters.")
                                    return@Button
                                }
                                if (isSignUpMode) {
                                    if (passwordInput != confirmPasswordInput) {
                                        viewModel.ttsHelper.speak("Password confirmations missmatch. Recheck security fields.")
                                        return@Button
                                    }
                                    
                                    com.google.firebase.auth.FirebaseAuth.getInstance().createUserWithEmailAndPassword(emailInput, passwordInput)
                                        .addOnCompleteListener { task ->
                                            if (task.isSuccessful) {
                                                val user = task.result?.user
                                                viewModel.loginWithSession(user?.email ?: emailInput, nameInput.ifBlank { "User" }, "Secure Email Auth")
                                                viewModel.ttsHelper.speak("Account registered successfully.")
                                            } else {
                                                viewModel.ttsHelper.speak("Registration failed: ${task.exception?.message ?: "Unknown error"}")
                                            }
                                        }
                                } else {
                                    com.google.firebase.auth.FirebaseAuth.getInstance().signInWithEmailAndPassword(emailInput, passwordInput)
                                        .addOnCompleteListener { task ->
                                            if (task.isSuccessful) {
                                                val user = task.result?.user
                                                viewModel.loginWithSession(user?.email ?: emailInput, user?.displayName ?: emailInput.substringBefore("@"), "Secure Email Auth")
                                                viewModel.ttsHelper.speak("Login successful.")
                                            } else {
                                                viewModel.ttsHelper.speak("Login failed: ${task.exception?.message ?: "Unknown error"}")
                                            }
                                        }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeonBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("email_auth_submit_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (isSignUpMode) "REGISTER & SETUP" else "ACCOUNT LOGIN",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(
                            onClick = {
                                isSignUpMode = !isSignUpMode
                                val prompt = if (isSignUpMode) {
                                    "Switched to user registration screen. Tell us your name, email address, password, and confirm password keys."
                                } else {
                                    "Switched to registered user sign in login screen. Type email and passphrase credentials."
                                }
                                viewModel.ttsHelper.speak(prompt)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isSignUpMode) "Already have credentials? Sign In" else "New client registration? Sign Up",
                                color = SecondaryNeonCyan,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            1 -> {
                // PHONE OTP PANEL
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, CardBorder, RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardSlate),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "SECURE PHONE SMS HANDSHAKE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = PulseVocalCyan,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val countryCodes = listOf("+1" to "US/CA", "+44" to "UK", "+91" to "IN", "+61" to "AU", "+81" to "JP", "+49" to "DE", "+33" to "FR", "+86" to "CN", "+55" to "BR", "+7" to "RU/KZ")
                        var selectedCountryCode by remember { mutableStateOf(countryCodes[0].first) }
                        var showCountryMenu by remember { mutableStateOf(false) }

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                Button(
                                    onClick = { showCountryMenu = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = CardBorder),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(56.dp)
                                ) {
                                    Text(selectedCountryCode, color = SlateLightText)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select country code", tint = SlateLightText)
                                }
                                DropdownMenu(
                                    expanded = showCountryMenu,
                                    onDismissRequest = { showCountryMenu = false },
                                    modifier = Modifier.background(CardSlate)
                                ) {
                                    countryCodes.forEach { (code, name) ->
                                        DropdownMenuItem(
                                            text = { Text("$name ($code)", color = SlateLightText) },
                                            onClick = {
                                                selectedCountryCode = code
                                                showCountryMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { 
                                    phoneInput = it
                                    val digitsOnly = it.replace(Regex("[^0-9]"), "")
                                    if (digitsOnly.isNotEmpty() && digitsOnly.length < 5) {
                                        phoneInputError = "Phone number must be at least 5 digits"
                                    } else if (digitsOnly.isNotEmpty() && digitsOnly.length > 15) {
                                        phoneInputError = "Phone number cannot exceed 15 digits"
                                    } else {
                                        phoneInputError = null
                                    }
                                },
                                isError = phoneInputError != null,
                                supportingText = {
                                    if (phoneInputError != null) {
                                        Text(
                                            text = phoneInputError!!,
                                            color = WarningRed,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.semantics { error(phoneInputError!!) }
                                        )
                                    }
                                },
                                label = { Text("Phone Number") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryNeonBlue,
                                    unfocusedBorderColor = CardBorder,
                                    errorBorderColor = WarningRed,
                                    errorLabelColor = WarningRed,
                                    errorSupportingTextColor = WarningRed,
                                    focusedLabelColor = PrimaryNeonBlue,
                                    unfocusedLabelColor = SlateMutedText,
                                    focusedTextColor = SlateLightText,
                                    unfocusedTextColor = SlateLightText
                                ),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("phone_input_field")
                                    .semantics {
                                        if (phoneInputError != null) {
                                            error(phoneInputError!!)
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (otpSentCode == null) {
                            Button(
                                onClick = {
                                    if (phoneInputError != null) {
                                        viewModel.ttsHelper.speak("Invalid phone number format: $phoneInputError")
                                        return@Button
                                    }
                                    if (phoneInput.length < 5) {
                                        viewModel.ttsHelper.speak("Invalid phone digit length. Please enter valid phone layout.")
                                        return@Button
                                    }
                                    val fullPhone = selectedCountryCode + phoneInput
                                    
                                    val callbacks = object : com.google.firebase.auth.PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                        override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                                            com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential).addOnCompleteListener { task ->
                                                if (task.isSuccessful) {
                                                    val user = task.result?.user
                                                    viewModel.loginWithSession(user?.phoneNumber ?: fullPhone, "Phone User", "Secure Phone Auth")
                                                }
                                            }
                                        }

                                        override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                                            viewModel.ttsHelper.speak("Phone verification failed: ${e.message ?: "Unknown error"}")
                                        }

                                        override fun onCodeSent(
                                            verificationIdSent: String,
                                            token: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken
                                        ) {
                                            verificationId = verificationIdSent
                                            otpSentCode = "sent"
                                            viewModel.ttsHelper.speak("Firebase SMS gateway sent code to $fullPhone.")
                                        }
                                    }

                                    val options = com.google.firebase.auth.PhoneAuthOptions.newBuilder(com.google.firebase.auth.FirebaseAuth.getInstance())
                                        .setPhoneNumber(fullPhone)
                                        .setTimeout(60L, java.util.concurrent.TimeUnit.SECONDS)
                                        .setActivity(localContext as android.app.Activity)
                                        .setCallbacks(callbacks)
                                        .build()
                                    com.google.firebase.auth.PhoneAuthProvider.verifyPhoneNumber(options)

                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("send_otp_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("REQUEST VERIFICATION SMS", color = SlateLightText, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text(
                                text = "OTP verification text code sent. Input the digits below.",
                                style = MaterialTheme.typography.bodySmall.copy(color = AccentuatingGreen),
                                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                            )

                            OutlinedTextField(
                                value = otpInput,
                                onValueChange = { 
                                    otpInput = it
                                    val digitsOnly = it.replace(Regex("[^0-9]"), "")
                                    if (digitsOnly.length != 6) {
                                        otpInputError = "Verification code must be exactly 6 digits"
                                    } else {
                                        otpInputError = null
                                    }
                                },
                                isError = otpInputError != null,
                                supportingText = {
                                    if (otpInputError != null) {
                                        Text(
                                            text = otpInputError!!,
                                            color = WarningRed,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.semantics { error(otpInputError!!) }
                                        )
                                    }
                                },
                                label = { Text("Security Verification Pin") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryNeonBlue,
                                    unfocusedBorderColor = CardBorder,
                                    errorBorderColor = WarningRed,
                                    errorLabelColor = WarningRed,
                                    errorSupportingTextColor = WarningRed,
                                    focusedLabelColor = PrimaryNeonBlue,
                                    unfocusedLabelColor = SlateMutedText,
                                    focusedTextColor = SlateLightText,
                                    unfocusedTextColor = SlateLightText
                                ),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("otp_code_field")
                                    .semantics {
                                        if (otpInputError != null) {
                                            error(otpInputError!!)
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (otpInputError != null || otpInput.length != 6) {
                                        viewModel.ttsHelper.speak("Invalid verification code format. The code must be exactly 6 digits.")
                                        return@Button
                                    }
                                    val credential = com.google.firebase.auth.PhoneAuthProvider.getCredential(verificationId, otpInput)
                                    com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential)
                                        .addOnCompleteListener { task ->
                                            if (task.isSuccessful) {
                                                val fullPhone = selectedCountryCode + phoneInput
                                                viewModel.loginWithSession(fullPhone, "Phone User", "Secure Phone Auth")
                                                viewModel.ttsHelper.speak("Phone authentication successful.")
                                            } else {
                                                viewModel.ttsHelper.speak("Invalid verification code: ${task.exception?.message ?: "Unknown error"}")
                                            }
                                        }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeonBlue),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("verify_otp_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("VERIFY & SIGN IN", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            TextButton(
                                onClick = {
                                    otpSentCode = null
                                    otpInput = ""
                                    viewModel.ttsHelper.speak("Cleared OTP states. Please revalidate phone submission.")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Resend SMS Connection Code", color = SecondaryNeonCyan)
                            }
                        }
                    }
                }
            }

            2 -> {
                // GOOGLE ACCOUNT SELECTOR HOYT COMPONENT
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, CardBorder, RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardSlate),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "GOOGLE CONNECTIVITY CHOOSE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = PulseVocalCyan,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Connect Google Cloud secure accounts securely utilizing hardware authentication layers.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMutedText)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                viewModel.ttsHelper.speak("Opening native Google Single Sign-On Account chooser dialog panel.")
                                coroutineScope.launch {
                                    try {
                                        val googleIdOption = com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
                                            .setFilterByAuthorizedAccounts(false)
                                            .setServerClientId(com.example.BuildConfig.GOOGLE_WEB_CLIENT_ID)
                                            .setAutoSelectEnabled(false)
                                            .build()

                                        val request = androidx.credentials.GetCredentialRequest.Builder()
                                            .addCredentialOption(googleIdOption)
                                            .build()

                                        val credentialManager = androidx.credentials.CredentialManager.create(localContext)
                                        val result = credentialManager.getCredential(
                                            request = request,
                                            context = localContext
                                        )
                                        
                                        val credential = result.credential
                                        if (credential is androidx.credentials.CustomCredential && credential.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                            val googleIdTokenCredential = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data)
                                            val idToken = googleIdTokenCredential.idToken
                                            val email = googleIdTokenCredential.id
                                            val name = googleIdTokenCredential.displayName ?: "Google User"

                                            com.example.auth.FirebaseAuthManager.signInWithGoogle(idToken, onSuccess = { firebaseUser ->
                                                val fName = firebaseUser.displayName ?: name
                                                val fEmail = firebaseUser.email ?: email
                                                viewModel.loginWithSession(fEmail, fName, "Real Google Account Link")
                                                viewModel.ttsHelper.speak("Successfully linked to Google Cloud.")
                                            }, onFailure = {
                                                viewModel.loginWithSession(email, name, "Fallback Google Sign In")
                                                viewModel.ttsHelper.speak("Firebase signin failed. Utilizing manual credential.")
                                            })
                                        } else {
                                            viewModel.ttsHelper.speak("Unknown credential type returned.")
                                        }
                                    } catch (e: Exception) {
                                        viewModel.ttsHelper.speak("Google Sign In failed. Ensure GOOGLE_WEB_CLIENT_ID is properly configured.")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("google_login_dialog_trigger"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AccountCircle, null, tint = OLEDBlack)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SIGN IN USING GOOGLE HUB", color = OLEDBlack, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            3 -> {
                // SANDBOX GUEST ACCESS
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, CardBorder, RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardSlate),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "LOCAL CLIENT EMBEDDED GUEST",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = PulseVocalCyan,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Continue using an isolated offline sandbox guest file profile. Features will operate locally with client mock capabilities.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMutedText)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { viewModel.login(isGuest = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = CardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("guest_mode_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, tint = SlateLightText)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("CONTINUE AS SANDBOX GUEST", color = SlateLightText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

// ================= MAIN HOST DASHBOARD =================
@Composable
fun MainDashboardScreen(viewModel: MainViewModel) {
    val activeTab by viewModel.activeTab.collectAsState()

    // Sub-page selections
    var currentSubPage by remember { mutableStateOf<String?>("Home") } // "Home", "ScreenUnderstanding", "DeviceControl", "CameraGuide"

    Scaffold(
        bottomBar = {
            if (currentSubPage == "Home") {
                NavigationBar(
                    containerColor = CardSlate,
                    tonalElevation = 8.dp,
                    windowInsets = WindowInsets.navigationBars,
                    modifier = Modifier
                        .border(BorderStroke(1.dp, CardBorder))
                        .testTag("bottom_nav_bar")
                ) {
                    val tabs = listOf(
                        Triple(NavigationTab.HOME, Icons.Default.Home, Icons.Outlined.Home),
                        Triple(NavigationTab.ACTIVITY, Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Outlined.List),
                        Triple(NavigationTab.DEVICES, Icons.Default.DeveloperBoard, Icons.Outlined.DeveloperBoard),
                        Triple(NavigationTab.PROFILE, Icons.Default.Person, Icons.Outlined.Person)
                    )

                    tabs.forEach { (tab, filledIcon, emptyIcon) ->
                        val isSelected = activeTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { viewModel.selectTab(tab) },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) filledIcon else emptyIcon,
                                    contentDescription = "${tab.name} screen tab"
                                )
                            },
                            label = { Text(text = tab.name.lowercase().replaceFirstChar { it.titlecase() }) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SlateLightText,
                                unselectedIconColor = SlateMutedText,
                                indicatorColor = PrimaryNeonBlue
                            )
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OLEDBlack)
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentSubPage,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "SubPageNavigation"
            ) { subPage ->
                when (subPage) {
                    "Home" -> {
                        when (activeTab) {
                            NavigationTab.HOME -> DashboardHome(viewModel, onNavigate = { currentSubPage = it })
                            NavigationTab.ACTIVITY -> ActivityPage(viewModel)
                            NavigationTab.DEVICES -> DevicesPage(viewModel)
                            NavigationTab.PROFILE -> ProfilePage(viewModel)
                        }
                    }
                    "ScreenUnderstanding" -> ScreenUnderstandingPage(viewModel, onBack = { currentSubPage = "Home" })
                    "DeviceControl" -> DeviceControlPage(viewModel, onBack = { currentSubPage = "Home" })
                    "CameraGuide" -> CameraGuidePage(viewModel, onBack = { currentSubPage = "Home" })
                }
            }
        }
    }
}

// ================= SUB-PANEL: DASHBOARD HOME =================
@Composable
fun DashboardHome(viewModel: MainViewModel, onNavigate: (String) -> Unit) {
    val assistantState by viewModel.assistantStatus.collectAsState()
    val heights by viewModel.waveformHeights.collectAsState()
    val speakInput by viewModel.spokenInputText.collectAsState()
    val isAccEnabled by viewModel.isAccessibilityEnabled.collectAsState()
    val isHandsFree by viewModel.isHandsFreeMode.collectAsState()
    val wakeWordState by viewModel.wakeWordState.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.updatePermissionsState()
        viewModel.triggerPermissionSync(context)
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.CALL_PHONE
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // App Launcher Title Header bar matching the "Bold Typography" design theme
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Circular logo bg with inner rotated accent
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(PrimaryNeonBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(45f)
                            .background(DeepContrastingText, RoundedCornerShape(2.dp))
                    )
                }
                Column {
                    Text(
                        text = "VISIONPILOT",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Text(
                        text = "AI Assistive Agent",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SlateMutedText,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Connected Pulse Status Badge from the theme
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFF333537))
                        .border(1.dp, CardBorder, RoundedCornerShape(32.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(AccentuatingGreen)
                    )
                    Text(
                        text = "CONNECTED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = AccentuatingGreen,
                            fontWeight = FontWeight.Black,
                            fontSize = 9.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
                }

                // Settings icon
                IconButton(
                    onClick = { onNavigate("Settings") },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CardSlate)
                        .border(1.dp, CardBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Navigate to settings panel",
                        tint = SlateLightText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ASSISTANT CONNECTION STATUS CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(24.dp))
                .border(1.5.dp, CardBorder, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = CardSlate),
            shape = RoundedCornerShape(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (color, label, icon) = when (assistantState) {
                    AssistantStatus.CONNECTED -> Triple(AccentuatingGreen, "Connected", Icons.Default.CloudQueue)
                    AssistantStatus.LISTENING -> Triple(PulseVocalCyan, "Listening Voice Feed...", Icons.Default.Mic)
                    AssistantStatus.CONFIRMING -> Triple(PrimaryNeonBlue, "Confirming Speech...", Icons.Default.QuestionAnswer)
                    AssistantStatus.PROCESSING -> Triple(SecondaryNeonCyan, "Processing Request...", Icons.Default.Autorenew)
                    AssistantStatus.OFFLINE -> Triple(StatusOfflineGrey, "Offline Mode", Icons.Default.CloudOff)
                }

                // Breathing colored pulse point
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AGENT STATUS",
                        style = MaterialTheme.typography.labelSmall.copy(color = SlateMutedText)
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = SlateLightText,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.semantics { contentDescription = "Active Status: $label" }
                    )
                }

                // Accessibility Active indicator pill
                if (isAccEnabled) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(PrimaryNeonBlue.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "A11Y ACTIVE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = PrimaryNeonBlue,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ASSISTANT CONTROL CENTER OR ACTIVE MICROPHONE PANEL
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, CardBorder, RoundedCornerShape(24.dp))
                .shadow(4.dp, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = CardSlate),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (assistantState == AssistantStatus.LISTENING) "SPEAK YOUR COMMAND" else "TAP MICROPHONE TO AWAKEN AGENT",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = SlateMutedText,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(OLEDBlack.copy(alpha = 0.5f))
                        .border(1.dp, if (isHandsFree) PulseVocalCyan else CardBorder, RoundedCornerShape(16.dp))
                        .clickable { viewModel.toggleHandsFreeMode() }
                        .padding(12.dp)
                        .semantics {
                            contentDescription = "Hands-free continuous voice wake up mode. Currently " + 
                                if (isHandsFree) "Enabled" else "Disabled"
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CONTINUOUS WAKE WAKEWORD FLOW",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isHandsFree) PulseVocalCyan else SlateMutedText,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                        Text(
                            text = if (isHandsFree) {
                                when (wakeWordState) {
                                    WakeWordState.WAITING_FOR_WAKE -> "Awake word active. Say 'Hi' or 'Hello' anytime."
                                    WakeWordState.LISTENING_FOR_COMMAND -> "Awake! Speak a command now."
                                    else -> "Awake word is active."
                                }
                            } else {
                                "Hands-free wakeup mode is OFF."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(color = SlateLightText)
                        )
                    }

                    Switch(
                        checked = isHandsFree,
                        onCheckedChange = { viewModel.toggleHandsFreeMode() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OLEDBlack,
                            checkedTrackColor = PulseVocalCyan,
                            uncheckedThumbColor = SlateMutedText,
                            uncheckedTrackColor = CardBorder
                        ),
                        modifier = Modifier.testTag("hands_free_mode_switch")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // WAVEFORM ANIMATION GRAPHICS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .semantics { contentDescription = "Voice microphone soundwave heights" },
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    heights.forEachIndexed { idx, heightVal ->
                        val barHeight by animateFloatAsState(
                            targetValue = heightVal,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "WaveHeight_$idx"
                        )
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(barHeight.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (assistantState == AssistantStatus.LISTENING) PulseVocalCyan
                                    else SlateMutedText.copy(alpha = 0.4f)
                                )
                        )
                    }
                }

                // Text field to type commands when speech isn't possible (high access versatility)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = speakInput,
                    onValueChange = { viewModel.updateManualInputText(it) },
                    placeholder = { Text("Or enter command text manually here...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SlateLightText,
                        unfocusedTextColor = SlateLightText,
                        focusedBorderColor = PrimaryNeonBlue,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = OLEDBlack,
                        unfocusedContainerColor = OLEDBlack
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("manual_command_input"),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Send
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = { viewModel.submitManualVoiceQuery() }
                    ),
                    trailingIcon = {
                        IconButton(onClick = { viewModel.submitManualVoiceQuery() }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Command", tint = PrimaryNeonBlue)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (assistantState == AssistantStatus.CONFIRMING) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .border(1.5.dp, PrimaryNeonBlue, RoundedCornerShape(16.dp))
                            .shadow(8.dp, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = OLEDBlack)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "CONFIRM COGNITIVE VOICE COMMAND",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = PrimaryNeonBlue,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "\"$speakInput\"",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("recognized_speech_confirmation_text")
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.cancelVoiceCommand() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("cancel_voice_command_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = CardBorder),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "CANCEL",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            color = SlateLightText,
                                            fontWeight = FontWeight.Black
                                        )
                                    )
                                }
                                Button(
                                    onClick = { viewModel.confirmAndSendVoiceCommand() },
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(48.dp)
                                        .border(1.dp, Color.White, RoundedCornerShape(12.dp))
                                        .testTag("confirm_voice_command_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeonBlue),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "CONFIRM & SEND",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            color = DeepContrastingText,
                                            fontWeight = FontWeight.Black
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // GIANT FLOATING MICROPHONE BUTTON
                IconButton(
                    onClick = { viewModel.toggleAssistantListening() },
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(PrimaryNeonBlue, PulseVocalCyan)
                            )
                        )
                        .shadow(12.dp, CircleShape)
                        .testTag("voice_input_fab"),
                ) {
                    Icon(
                        imageVector = if (assistantState == AssistantStatus.LISTENING) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Trigger Speech Command Input",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // RECENT COMMAND SELECTION HUB
        Text(
            text = "EASY QUICK COMMANDS",
            style = MaterialTheme.typography.labelLarge.copy(
                color = SlateMutedText,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        val dynamicCommands by viewModel.quickCommands.collectAsState()

        Column(modifier = Modifier.fillMaxWidth()) {
            dynamicCommands.forEach { cmdText ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .semantics { 
                            contentDescription = "Execute quick command: $cmdText"
                        }
                        .clickable {
                            viewModel.updateManualInputText(cmdText)
                            viewModel.submitManualVoiceQuery()
                        },
                    colors = CardDefaults.cardColors(containerColor = CardSlate),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CardBorder),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = SecondaryNeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = cmdText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = SlateLightText,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Trigger immediate query",
                            tint = SlateMutedText
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // CORE WORKSPACE MODULE CARDS
        Text(
            text = "ASSISTIVE MODULE CHANNELS",
            style = MaterialTheme.typography.labelLarge.copy(
                color = SlateMutedText,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        val modules = listOf(
            Triple("Screen Understanding", "Screen analysis and spatial nodes mapping", "ScreenUnderstanding"),
            Triple("Device Controller", "Inject typing, swipes, scrolls & app execution", "DeviceControl"),
            Triple("Camera Guide Scanner", "Acoustic layout mapping lens, item detections", "CameraGuide")
        )

        modules.forEach { (title, subtitle, route) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .semantics { 
                        contentDescription = "Navigate to \$title tool"
                    }
                    .clickable { onNavigate(route) }
                    .border(1.dp, CardBorder, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSlate),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = SlateLightText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMutedText)
                        )
                    }
                    IconButton(
                        onClick = { onNavigate(route) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(PrimaryNeonBlue.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Launch,
                            contentDescription = "Launch $title page model",
                            tint = PrimaryNeonBlue
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

// ================= MODULE SCREEN: SCREEN UNDERSTANDING =================
@Composable
fun ScreenUnderstandingPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val detectedList by viewModel.detectedUiElements.collectAsState()
    val isProcessing by viewModel.screenshotProcessing.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Upper back and title anchor
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CardSlate)
                    .testTag("back_to_home")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Return to Dashboard",
                    tint = SlateLightText
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Screen Context",
                style = MaterialTheme.typography.displayMedium.copy(color = SlateLightText),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // VIEWPORT SIMULATOR CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .border(1.5.dp, CardBorder, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = CardSlate),
            shape = RoundedCornerShape(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(OLEDBlack)
                    .drawBehind {
                        // Drawing premium architectural gridlines representing screen nodes traversal
                        val dashWidth = 8f
                        val dashGap = 12f
                        val gridSpace = 100f
                        for (x in 0 until size.width.toInt() step gridSpace.toInt()) {
                            drawLine(
                                color = CardBorder.copy(alpha = 0.25f),
                                start = Offset(x.toFloat(), 0f),
                                end = Offset(x.toFloat(), size.height),
                                strokeWidth = 2f
                            )
                        }
                        for (y in 0 until size.height.toInt() step gridSpace.toInt()) {
                            drawLine(
                                color = CardBorder.copy(alpha = 0.25f),
                                start = Offset(0f, y.toFloat()),
                                end = Offset(size.width, y.toFloat()),
                                strokeWidth = 2f
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = PulseVocalCyan)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Analyzing screen hierarchy tree...",
                            style = MaterialTheme.typography.bodyLarge.copy(color = SlateMutedText)
                        )
                    }
                } else if (detectedList.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Screenshot,
                            contentDescription = null,
                            tint = SlateMutedText.copy(alpha = 0.5f),
                            modifier = Modifier.size(60.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No screenshot frame selected",
                            style = MaterialTheme.typography.titleMedium.copy(color = SlateMutedText),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Initiate analysis below to trace on-screen buttons and inputs.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMutedText),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Screens mock nodes rendering overlaid visual bounding cards
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Background placeholder visual widgets
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "[SCREEN CAPTURE MOCK: WHATSAPP SETTINGS]",
                                style = MaterialTheme.typography.labelSmall.copy(color = PrimaryNeonBlue),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryNeonBlue.copy(alpha = 0.2f))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(SecondaryNeonCyan.copy(alpha = 0.2f))
                                )
                            }
                        }

                        // Drawing our extracted coordinates nodes as glowing borders!
                        detectedList.forEach { element ->
                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = (element.left / 4).dp,
                                        y = (element.top / 4).dp
                                    )
                                    .size(
                                        width = ((element.right - element.left) / 4).dp,
                                        height = ((element.bottom - element.top) / 4).dp
                                    )
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PulseVocalCyan.copy(alpha = 0.15f))
                                    .border(2.dp, PulseVocalCyan, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = element.type,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = SlateLightText,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Trigger Analysis control bar
        Button(
            onClick = { viewModel.runScreenAnalysis() },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeonBlue),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("extract_hierarchy_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DocumentScanner,
                contentDescription = null,
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Analyze Active Screen Layout",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        if (detectedList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "DETECTED NODE ELEMENT LIST",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = SlateMutedText,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            detectedList.forEach { element ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = CardSlate),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val ic = when (element.type) {
                                    "Button" -> Icons.Default.SmartButton
                                    "Text" -> Icons.AutoMirrored.Filled.Notes
                                    "Input" -> Icons.Default.Keyboard
                                    else -> Icons.Default.Android
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(CardBorder),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = ic,
                                        contentDescription = null,
                                        tint = SecondaryNeonCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = element.type.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = SecondaryNeonCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }

                            // Confidence score pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AccentuatingGreen.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Confidence: ${(element.confidence * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = AccentuatingGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "\"${element.label}\"",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = SlateLightText,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Bounds Rect: [left=${element.left}, top=${element.top}, right=${element.right}, bottom=${element.bottom}]",
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMutedText)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

// ================= MODULE SCREEN: PHYSICAL DEVICE CONTROLLER =================
@Composable
fun DeviceControlPage(viewModel: MainViewModel, onBack: () -> Unit) {
    val commandLog by viewModel.lastActionExecuted.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Upper navigation header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CardSlate)
                    .testTag("back_to_home")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Return to Dashboard",
                    tint = SlateLightText
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Device Control",
                style = MaterialTheme.typography.displayMedium.copy(color = SlateLightText),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // FEEDBACK METRICS SHELF
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, CardBorder, RoundedCornerShape(24.dp))
                .shadow(4.dp, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = CardSlate),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "ACTION CONTROLLER FEEDBACK",
                    style = MaterialTheme.typography.labelSmall.copy(color = SlateMutedText)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = commandLog.ifBlank { "Awaiting node action trigger..." },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = if (commandLog.startsWith("Error")) WarningRed else SlateLightText,
                        lineHeight = 24.sp
                    ),
                    modifier = Modifier.semantics { contentDescription = "Last action: $commandLog" }
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "TRIGGER TEST AUTOMATION HARNESS (SAFE)",
            style = MaterialTheme.typography.labelLarge.copy(
                color = SlateMutedText,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        val controls = listOf(
            Triple("Tap Action", "Simulates triggering a standard button click", "TAP"),
            Triple("Scroll Action", "Simulates viewport finger swiping", "SCROLL"),
            Triple("Open App Action", "Triggers WhatsApp package shell gateway", "OPEN_APP"),
            Triple("Type Text Action", "Inputs text strings inside focus box", "TYPE")
        )

        controls.forEach { (title, subtitle, action) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .border(1.dp, CardBorder, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSlate),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = SlateLightText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMutedText)
                        )
                    }

                    Button(
                        onClick = { viewModel.executeDeviceAction(action, "Target_Node_Center") },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeonBlue),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("action_${action.lowercase()}_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "RUN", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = Color.White))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

// ================= MODULE SCREEN: CAMERA GUIDE LENS =================
@OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
fun CameraGuidePage(viewModel: MainViewModel, onBack: () -> Unit) {
    val processing by viewModel.screenshotProcessing.collectAsState()
    var isCameraRunning by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Nav Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CardSlate)
                    .testTag("back_to_home")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Return to Dashboard",
                    tint = SlateLightText
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Camera Guide",
                style = MaterialTheme.typography.displayMedium.copy(color = SlateLightText),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // CAMERA SIMULATOR FRAME - SEEING NOW CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .border(1.dp, CardBorder, RoundedCornerShape(28.dp))
                .shadow(6.dp, RoundedCornerShape(28.dp)),
            colors = CardDefaults.cardColors(containerColor = CardSlate),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SEEING\nNOW",
                        style = MaterialTheme.typography.displayLarge.copy(
                            color = PrimaryNeonBlue,
                            lineHeight = 32.sp
                        ),
                        fontWeight = FontWeight.Black
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardBorder)
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                @OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
                val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(OLEDBlack)
                        .border(1.5.dp, CardBorder, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (processing) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = PulseVocalCyan)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Analyzing frames...",
                                style = MaterialTheme.typography.bodyLarge.copy(color = SlateMutedText)
                            )
                        }
                    } else if (isCameraRunning) {
                        if (cameraPermissionState.status.isGranted) {
                            // REAL LIFE CAMERAX LIVE FEED PREVIEW
                            val localContext = androidx.compose.ui.platform.LocalContext.current
                            val lifecycleOwner = LocalLifecycleOwner.current
                            val previewView = remember { PreviewView(localContext) }

                            LaunchedEffect(isCameraRunning) {
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(localContext)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            cameraSelector,
                                            preview
                                        )
                                    } catch (e: Exception) {
                                        android.util.Log.e("CameraGuidePage", "Use case binding failed", e)
                                    }
                                }, ContextCompat.getMainExecutor(localContext))
                            }

                            Box(modifier = Modifier.fillMaxSize()) {
                                AndroidView(
                                    factory = { previewView },
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Overlay framing
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val stroke = 3f
                                    val len = 30f
                                    drawCircle(
                                        color = PrimaryNeonBlue.copy(alpha = 0.3f),
                                        radius = 80f,
                                        center = center,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                                    )
                                    drawLine(PrimaryNeonBlue, Offset(20f, 20f), Offset(20f, 20f + len), stroke)
                                    drawLine(PrimaryNeonBlue, Offset(20f, 20f), Offset(20f + len, 20f), stroke)
                                    drawLine(PrimaryNeonBlue, Offset(size.width - 20f, size.height - 20f), Offset(size.width - 20f, size.height - 20f - len), stroke)
                                    drawLine(PrimaryNeonBlue, Offset(size.width - 20f, size.height - 20f), Offset(size.width - 20f - len, size.height - 20f), stroke)
                                }
                            }
                        } else {
                            // Permission request prompt
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Camera Permission Required",
                                    style = MaterialTheme.typography.titleMedium.copy(color = SlateLightText, fontWeight = FontWeight.Bold),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { cameraPermissionState.launchPermissionRequest() },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeonBlue),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("GRANT CAMERA ACCESS", style = MaterialTheme.typography.labelLarge.copy(color = DeepContrastingText, fontWeight = FontWeight.Black))
                                }
                            }
                        }
                    } else {
                        // FEED STOPPED / PAUSED STATE
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.VideocamOff,
                                contentDescription = "Camera stream paused",
                                tint = SlateMutedText,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "CAMERA FEED STREAM PAUSED",
                                style = MaterialTheme.typography.labelLarge.copy(color = SlateMutedText, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { isCameraRunning = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isCameraRunning) WarningRed else CardBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("stop_camera_feed_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "STOP FEED",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Black
                            )
                        )
                    }

                    Button(
                        onClick = { isCameraRunning = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCameraRunning) AccentuatingGreen else CardBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("start_camera_feed_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (isCameraRunning) DeepContrastingText else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "START FEED",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = if (isCameraRunning) DeepContrastingText else Color.White,
                                fontWeight = FontWeight.Black
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "GUIDANCE DISPATCH CHANNELS",
            style = MaterialTheme.typography.labelLarge.copy(
                color = SlateMutedText,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        // Guide buttons
        val items = listOf(
            Triple("Describe Scene", "Verbal descriptions of environment surroundings", Icons.Default.Landscape),
            Triple("Detect Objects", "Pinpoint utensils, bottles, keyboards & computers", Icons.Default.Category),
            Triple("Read Text", "Optical character reading text detection out loud", Icons.Default.Translate)
        )

        items.forEach { (title, subtitle, icon) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .semantics { 
                        contentDescription = "Trigger camera action: \$title"
                    }
                    .clickable { viewModel.triggerCameraGuideAction(title) }
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = CardSlate),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(CardBorder),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = PulseVocalCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = SlateLightText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMutedText)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.KeyboardVoice,
                        contentDescription = "Speak results",
                        tint = SlateMutedText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

// ================= ACTIVITY TIMELINE PAGE =================
@Composable
fun ActivityPage(viewModel: MainViewModel) {
    val historyList by viewModel.activityHistory.collectAsState()
    val df = remember { SimpleDateFormat("HH:mm:ss (MMM dd)", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Activity Log",
                    style = MaterialTheme.typography.displayMedium.copy(color = SlateLightText),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Historical timeline of local assistant transactions",
                    style = MaterialTheme.typography.bodyMedium.copy(color = SlateMutedText)
                )
            }
            IconButton(
                onClick = { viewModel.clearLogHistory() },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CardSlate)
                    .testTag("clear_logs")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = "Wipe history databases",
                    tint = WarningRed
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.QueryBuilder,
                        contentDescription = null,
                        tint = SlateMutedText.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "History feed clear.",
                        style = MaterialTheme.typography.titleMedium.copy(color = SlateMutedText)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("activity_log_list")
            ) {
                items(historyList) { item ->
                    var isVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(key1 = item.timestamp) {
                        isVisible = true
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isVisible,
                        enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(500)) + 
                                androidx.compose.animation.slideInVertically(
                                    animationSpec = androidx.compose.animation.core.tween(500),
                                    initialOffsetY = { 60 }
                                )
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = CardSlate),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.ChatBubbleOutline,
                                            contentDescription = null,
                                            tint = PrimaryNeonBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = item.command,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = SlateLightText,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Text(
                                        text = df.format(Date(item.timestamp)),
                                        style = MaterialTheme.typography.labelSmall.copy(color = SlateMutedText)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = item.response,
                                    style = MaterialTheme.typography.bodyLarge.copy(color = SlateLightText, lineHeight = 24.sp)
                                )

                                if (!item.error.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(WarningRed.copy(alpha = 0.15f))
                                            .padding(8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.ErrorOutline,
                                                contentDescription = "Error notification",
                                                tint = WarningRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = item.error,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = WarningRed,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================= WORKSPACE TAB: INTEGRATION & CONNECT STATE =================
@Composable
fun DevicesPage(viewModel: MainViewModel) {
    val connectionStatus by viewModel.webSocketLog.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Device Node Gateway",
            style = MaterialTheme.typography.displayMedium.copy(color = SlateLightText),
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Manage WebSocket handshakes and mock controllers",
            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMutedText)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // WEBSOCKET TELEMETRY CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, CardBorder, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = CardSlate),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "REMOTE WEB-SOCKET INTEGRATION",
                    style = MaterialTheme.typography.labelSmall.copy(color = SlateMutedText)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ws://agent/connect",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = PrimaryNeonBlue,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when (connectionStatus) {
                                    "Connected" -> AccentuatingGreen.copy(alpha = 0.2f)
                                    "Connecting..." -> SecondaryNeonCyan.copy(alpha = 0.2f)
                                    else -> StatusOfflineGrey.copy(alpha = 0.15f)
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = connectionStatus.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = when (connectionStatus) {
                                    "Connected" -> AccentuatingGreen
                                    "Connecting..." -> SecondaryNeonCyan
                                    else -> SlateMutedText
                                },
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { viewModel.connectWebSocketStream() },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeonBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("ws_connect_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Establish Stream Socket Bridge",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // HARDWARE ACCELERATION SHELF
        Text(
            text = "HARDWARE SERVICE STATUS",
            style = MaterialTheme.typography.labelLarge.copy(
                color = SlateMutedText,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        val screenState by viewModel.screenCaptureServiceStatus.collectAsState()
        val micState by viewModel.microphoneServiceStatus.collectAsState()
        val camState by viewModel.cameraServiceStatus.collectAsState()

        val devices = listOf(
            Triple("Media Screen capture API", "Active standard screen recording frame dispatcher", screenState),
            Triple("Vocal Mic Microphone Feed", "Speex compression sound processor channel", micState),
            Triple("Focal Camera Scanner", "Back lens optical capture preview camera", camState)
        )

        devices.forEach { (name, info, state) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .clickable { 
                        viewModel.ttsHelper.speak("This is an informational setting for $name. Please manage permissions in your Android OS standard settings app.")
                    },
                colors = CardDefaults.cardColors(containerColor = CardSlate),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = SlateLightText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = info,
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMutedText)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardBorder)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = state,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = SecondaryNeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

// ================= WORKSPACE TAB: ACCESSIBLE SETTINGS =================
@Composable
fun ProfilePage(viewModel: MainViewModel) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isAccEnabled by viewModel.isAccessibilityActive.collectAsState()
    val speechRate by viewModel.speechRate.collectAsState()
    val speechPitch by viewModel.speechPitch.collectAsState()
    val selectedVoice by viewModel.selectedVoice.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()

    val sessionUserEmail by viewModel.sessionUserEmail.collectAsState()
    val sessionUserName by viewModel.sessionUserName.collectAsState()
    val sessionProvider by viewModel.sessionProvider.collectAsState()

    var showLangMenu by remember { mutableStateOf(false) }
    var showVoiceMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings Center",
            style = MaterialTheme.typography.displayMedium.copy(color = SlateLightText),
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Adjust speech rates, localized languages, and display criteria",
            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMutedText)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ACTIVE USER PROFILE SESSION CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, PrimaryNeonBlue, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = CardSlate),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "ACTIVE SECURE CLIENT LINK",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = PulseVocalCyan,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(PrimaryNeonBlue.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (sessionUserName ?: "U").take(1).uppercase(),
                            color = PrimaryNeonBlue,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sessionUserName ?: "VisionPilot User",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = SlateLightText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = sessionUserEmail ?: "offline@visionpilot.ai",
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMutedText)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PulseVocalCyan.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Via: ${sessionProvider ?: "Guest Access"}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = PulseVocalCyan,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { viewModel.logout() },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningRed.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .border(1.5.dp, WarningRed, RoundedCornerShape(12.dp))
                        .testTag("auth_logout_button")
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = WarningRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TERMINATE DEVICE SESSION",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = WarningRed,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 1: SYSTEM CONTROLS
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, CardBorder, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = CardSlate),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "A11Y & VISIBILITY CONTROLS",
                    style = MaterialTheme.typography.labelSmall.copy(color = SlateMutedText)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Row 1: High Contrast Dark Mode Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "OLED High Contrast Mode",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = SlateLightText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Enforce pitch deep black for blind usability protection",
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMutedText)
                        )
                    }

                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { viewModel.toggleDarkMode() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PulseVocalCyan,
                            checkedTrackColor = PrimaryNeonBlue
                        ),
                        modifier = Modifier.testTag("dark_mode_toggle")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = CardBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // Row 2: accessibility capture engine handshake
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Accessibility Service Hook",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = SlateLightText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Traverse view trees and compile elements automatically",
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMutedText)
                        )
                    }

                    Switch(
                        checked = isAccEnabled,
                        onCheckedChange = { viewModel.toggleAccessibility() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PulseVocalCyan,
                            checkedTrackColor = PrimaryNeonBlue
                        ),
                        modifier = Modifier.testTag("accessibility_toggle")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = CardBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // Row 3: Battery Optimization
                val context = LocalContext.current
                var isIgnoringBatteryOptimizations by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                    isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(context.packageName)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (!isIgnoringBatteryOptimizations) {
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = android.net.Uri.parse("package:${context.packageName}")
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.util.Log.e("Settings", "Failed to launch battery options", e)
                            }
                        } else {
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback if general settings page fails
                            }
                        }
                    },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Unrestricted Battery Mode",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = SlateLightText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = if (isIgnoringBatteryOptimizations) "Optimizations disabled (Wake Word active safely)" else "Enable to prevent Doze Mode from killing background Wake Word",
                            style = MaterialTheme.typography.bodyMedium.copy(color = SlateMutedText)
                        )
                    }
                    
                    Icon(
                        imageVector = if (isIgnoringBatteryOptimizations) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = "Battery Optimization Status",
                        tint = if (isIgnoringBatteryOptimizations) PrimaryNeonBlue else androidx.compose.ui.graphics.Color(0xFFFFA000)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 2: TTS & SOUND SETTINGS
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, CardBorder, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = CardSlate),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "SPEECH TO TEXT & AUDIO TUNING",
                    style = MaterialTheme.typography.labelSmall.copy(color = SlateMutedText)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Speech speed slider
                Text(
                    text = "Text-To-Speech Playback Speed: ${String.format("%.1f", speechRate)}x",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = SlateLightText,
                        fontWeight = FontWeight.Bold
                    )
                )

                Slider(
                    value = speechRate,
                    onValueChange = { viewModel.setSpeechRate(it) },
                    valueRange = 0.5f..2.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = PulseVocalCyan,
                        activeTrackColor = PrimaryNeonBlue,
                        inactiveTrackColor = CardBorder
                    ),
                    modifier = Modifier.testTag("speech_rate_slider")
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = CardBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // Speech Pitch segmented button row
                Text(
                    text = "Text-To-Speech Voice Pitch: ${if (speechPitch < 0.9f) "Low" else if (speechPitch > 1.2f) "High" else "Medium"}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = SlateLightText,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val pitches = listOf("Low" to 0.7f, "Medium" to 1.0f, "High" to 1.4f)
                    pitches.forEach { (name, value) ->
                        val isSelected = java.lang.Math.abs(speechPitch - value) < 0.05f
                        Button(
                            onClick = { viewModel.setSpeechPitch(value) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) PrimaryNeonBlue else CardBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("pitch_${name.lowercase()}"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = name.uppercase(),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = if (isSelected) DeepContrastingText else SlateLightText,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = CardBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // Voice selection row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Selected Voice Profile",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = SlateLightText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = selectedVoice,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = PulseVocalCyan,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Box {
                        Button(
                            onClick = { showVoiceMenu = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CardBorder),
                            modifier = Modifier.testTag("voice_menu_trigger")
                        ) {
                            Text(
                                text = "STYLE",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = SlateLightText,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        DropdownMenu(
                            expanded = showVoiceMenu,
                            onDismissRequest = { showVoiceMenu = false },
                            modifier = Modifier.background(CardSlate)
                        ) {
                            val voices = listOf(
                                "Male - Premium US Voice",
                                "Female - Premium UK Voice",
                                "Synthesizer - Robot Voice",
                                "Cosmic Sage - Deep Voice"
                            )
                            voices.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(text = name, color = SlateLightText) },
                                    onClick = {
                                        viewModel.setSelectedVoice(name)
                                        showVoiceMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = CardBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // Language selection segment
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Default Vocal Language",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = SlateLightText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = selectedLanguage,
                            style = MaterialTheme.typography.bodyMedium.copy(color = SecondaryNeonCyan, fontWeight = FontWeight.Bold)
                        )
                    }

                    Box {
                        Button(
                            onClick = { showLangMenu = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CardBorder),
                            modifier = Modifier.testTag("language_menu_trigger")
                        ) {
                            Text(text = "SELECT", style = MaterialTheme.typography.labelLarge.copy(color = SlateLightText))
                        }

                        DropdownMenu(
                            expanded = showLangMenu,
                            onDismissRequest = { showLangMenu = false },
                            modifier = Modifier.background(CardSlate)
                        ) {
                            val formats = listOf("English (US)", "English (UK)", "Hindi (IN)", "Spanish (ES)", "German (DE)")
                            formats.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(text = lang, color = SlateLightText) },
                                    onClick = {
                                        viewModel.changeLanguage(lang)
                                        showLangMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION: EMERGENCY CONTACT SETTINGS
        val emergencyContactName by viewModel.emergencyContactName.collectAsState()
        val emergencyContactNumber by viewModel.emergencyContactNumber.collectAsState()
        
        var isEditingEmergency by remember { mutableStateOf(false) }
        var tempEmergencyName by remember { mutableStateOf(emergencyContactName) }
        var tempEmergencyNumber by remember { mutableStateOf(emergencyContactNumber) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, WarningRed, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = CardSlate),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "EMERGENCY CONTACT",
                    style = MaterialTheme.typography.labelSmall.copy(color = WarningRed, fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isEditingEmergency) {
                    androidx.compose.material3.OutlinedTextField(
                        value = tempEmergencyName,
                        onValueChange = { tempEmergencyName = it },
                        label = { Text("Contact Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SlateLightText,
                            unfocusedTextColor = SlateMutedText,
                            focusedBorderColor = PulseVocalCyan,
                            unfocusedBorderColor = CardBorder
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = tempEmergencyNumber,
                        onValueChange = { tempEmergencyNumber = it },
                        label = { Text("Phone Number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SlateLightText,
                            unfocusedTextColor = SlateMutedText,
                            focusedBorderColor = PulseVocalCyan,
                            unfocusedBorderColor = CardBorder
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.material3.TextButton(onClick = { isEditingEmergency = false }) {
                            Text("Cancel", color = SlateMutedText)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                viewModel.updateEmergencyContact(tempEmergencyName, tempEmergencyNumber)
                                isEditingEmergency = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WarningRed)
                        ) {
                            Text("Save", color = Color.White)
                        }
                    }
                } else {
                    Text(
                        text = "Say 'call for help' to dial instantly.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = SlateMutedText)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (emergencyContactName.isNotBlank()) "$emergencyContactName - $emergencyContactNumber" else "Not configured",
                        style = MaterialTheme.typography.titleMedium.copy(color = SlateLightText, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            tempEmergencyName = emergencyContactName
                            tempEmergencyNumber = emergencyContactNumber
                            isEditingEmergency = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (emergencyContactNumber.isBlank()) "Setup Emergency Contact" else "Edit Emergency Contact", color = SlateLightText)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SECTION 3: PERMISSIONS MANAGEMENT
        val context = androidx.compose.ui.platform.LocalContext.current
        val isCameraGranted by viewModel.isCameraGranted.collectAsState()
        val isRecordAudioGranted by viewModel.isRecordAudioGranted.collectAsState()
        val isContactsGranted by viewModel.isContactsGranted.collectAsState()
        val isAccessibilityActive by viewModel.isAccessibilityActive.collectAsState()
        val isOverlayGranted by viewModel.isOverlayGranted.collectAsState()

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, CardBorder, RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = CardSlate),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "PERMISSIONS WALKTHROUGH CHECK",
                    style = MaterialTheme.typography.labelSmall.copy(color = SlateMutedText)
                )

                Spacer(modifier = Modifier.height(16.dp))

                val permissions = listOf(
                    Pair("Screen Overlay Frame capture", if (isOverlayGranted) "GRANTED" else "DENIED"),
                    Pair("Accessibility Automation capture", if (isAccessibilityActive) "GRANTED" else "DENIED"),
                    Pair("Hardware Back camera permission", if (isCameraGranted) "GRANTED" else "DENIED"),
                    Pair("Vocal Microphone feed permission", if (isRecordAudioGranted) "GRANTED" else "DENIED"),
                    Pair("Local Phone Contacts access", if (isContactsGranted) "GRANTED" else "DENIED")
                )

                permissions.forEach { (name, status) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = SlateLightText,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (status == "GRANTED") AccentuatingGreen.copy(alpha = 0.2f) else WarningRed.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = status,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (status == "GRANTED") AccentuatingGreen else WarningRed,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    viewModel.updatePermissionsState()
                    viewModel.triggerPermissionSync(context)
                }

                Button(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.CAMERA,
                                android.Manifest.permission.RECORD_AUDIO,
                                android.Manifest.permission.READ_CONTACTS
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeonBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("permissions_sync_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Synchronize System Permissions",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
