package com.example.auth

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

object FirebaseAuthManager {
    var isInitialized = false
        private set

    fun initialize(context: Context) {
        val apiKey = BuildConfig.FIREBASE_API_KEY
        val appId = BuildConfig.FIREBASE_APP_ID
        val projectId = try { BuildConfig.FIREBASE_PROJECT_ID } catch(e: Exception) { "visionpilot-ai" }

        if (apiKey.isBlank() || appId.isBlank() || apiKey.startsWith("YOUR")) {
            Log.e("FirebaseAuth", "Missing Firebase credentials in .env. Falling back to mock authentication.")
            return
        }

        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey(apiKey)
                    .setApplicationId(appId)
                    .setProjectId(if (projectId.isBlank() || projectId.startsWith("YOUR")) "visionpilot-ai" else projectId)
                    .build()
                FirebaseApp.initializeApp(context, options)
            }
            isInitialized = true
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "Failed to initialize Firebase", e)
        }
    }

    fun getGoogleSignInOptions(): GoogleSignInOptions? {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (clientId.isBlank() || clientId.startsWith("YOUR")) {
            return null
        }
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestEmail()
            .requestProfile()
            .build()
    }

    fun signInWithGoogle(idToken: String, onSuccess: (com.google.firebase.auth.FirebaseUser) -> Unit, onFailure: (Exception) -> Unit) {
        if (!isInitialized) {
            onFailure(Exception("Firebase not initialized"))
            return
        }
        val auth = FirebaseAuth.getInstance()
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        onSuccess(user)
                    } else {
                        onFailure(Exception("User is null after successful sign in"))
                    }
                } else {
                    onFailure(task.exception ?: Exception("Unknown error"))
                }
            }
    }
}
