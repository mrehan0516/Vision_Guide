package com.example.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.util.Log
import com.example.data.database.DatabaseHelper
import com.example.service.VisionPilotAccessibilityService
import kotlinx.coroutines.delay
import java.net.URLEncoder

data class AgentAction(
    val type: String, // "tap", "type", "scroll", "back", "home", "launch_app", "call", "whatsapp", "speak", "done", "error"
    val x: Int? = null,
    val y: Int? = null,
    val text: String? = null,
    val packageId: String? = null,
    val contactName: String? = null,
    val message: String? = null
)

class AgentActionExecutor(
    private val context: Context,
    private val databaseHelper: DatabaseHelper,
    private val captureSnapshot: suspend () -> String?, // returns JSON with snapshot
    private val sendToBackend: suspend (String?, String) -> AgentAction,
    private val speak: (String) -> Unit
) {

    suspend fun runAgentLoop(command: String) {
        speak("On it")
        var steps = 0
        while (steps < 10) {
            val snapshot = captureSnapshot()
            val action = sendToBackend(snapshot, command)
            
            if (action.type == "done") {
                speak(action.message ?: "Task completed")
                databaseHelper.insertActivity(command, action.type, action.message ?: "Success", true)
                break
            }
            if (action.type == "error") {
                speak(action.message ?: "Task failed")
                databaseHelper.insertActivity(command, action.type, action.message ?: "Error", false)
                break
            }
            
            executeAction(action)
            delay(1200)
            steps++
        }
        if (steps == 10) {
            speak("I could not complete that. Please try a simpler command.")
            databaseHelper.insertActivity(command, "timeout", "Too many steps", false)
        }
    }

    private fun executeAction(action: AgentAction) {
        Log.d("AgentExecutor", "Executing action: \${action.type}")
        val accessibilityService = VisionPilotAccessibilityService.getInstance()
        
        when (action.type) {
            "tap" -> {
                // Not perfectly supported with arbitrary x, y without sending gestures, but we'll simulate.
                if (action.text != null) {
                    accessibilityService?.clickNodeWithText(action.text)
                } else if (action.x != null && action.y != null) {
                    // Fallback using gesture in the service if you implemented it.
                    // accessibilityService?.dispatchTapGesture(action.x, action.y)
                }
            }
            "type" -> {
                // Focus and type - requires custom accessibility support
                action.text?.let { accessibilityService?.clickNodeWithText(it) } // naive
            }
            "scroll" -> {
                accessibilityService?.performScroll(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) // Or backward depending on action.text etc.
            }
            "back" -> {
                accessibilityService?.performGlobalActionCode(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            }
            "home" -> {
                accessibilityService?.performGlobalActionCode(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            }
            "launch_app" -> {
                val pkg = action.packageId ?: return
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
            "call" -> {
                val contactName = action.contactName ?: action.text ?: return
                val number = lookupContactNumber(contactName) ?: contactName
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:\$number"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                } catch (e: SecurityException) {
                    speak("Call permission is missing.")
                }
            }
            "whatsapp" -> {
                val contactName = action.contactName ?: return
                val number = lookupContactNumber(contactName) ?: return
                val encodedMsg = action.message?.let { URLEncoder.encode(it, "UTF-8") } ?: ""
                
                // Format number: Remove generic characters
                val formattedNumber = number.replace("[\\\\+\\\\-\\\\s\\\\(\\\\)]".toRegex(), "")
                
                val uri = Uri.parse("https://wa.me/\$formattedNumber?text=\$encodedMsg")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            "speak" -> {
                action.message?.let { speak(it) }
            }
            else -> {
                Log.w("AgentExecutor", "Unknown action type: \${action.type}")
            }
        }
    }

    private fun lookupContactNumber(contactName: String): String? {
        val cr = context.contentResolver
        val cursor = cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "\${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%\$contactName%"),
            null
        )
        
        var number: String? = null
        if (cursor != null && cursor.moveToFirst()) {
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (numberIndex >= 0) {
                number = cursor.getString(numberIndex)
            }
            cursor.close()
        }
        return number
    }
}
