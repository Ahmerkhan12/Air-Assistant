package com.ahmer.airassistant.assistant

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

sealed class AssistantLaunchResult {
    data object Success : AssistantLaunchResult()
    data object NoAssistantConfigured : AssistantLaunchResult()
    data class Failure(val message: String) : AssistantLaunchResult()
}

class AssistantLauncher(private val context: Context) {

    private val tag = "AssistantLauncher"

    /**
     * Launches the voice assistant (Google Assistant or Gemini).
     * Specifically triggers the listening / interactive voice overlay rather than opening the Google web app.
     */
    fun launchAssistant(preferGeminiApp: Boolean = true): AssistantLaunchResult {
        Log.d(tag, "Launching voice assistant (preferGeminiApp=$preferGeminiApp)...")

        // 1. If Gemini app is installed, open it directly
        if (preferGeminiApp) {
            try {
                val geminiIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.bard")
                if (geminiIntent != null) {
                    geminiIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    context.startActivity(geminiIntent)
                    Log.d(tag, "Launched Gemini app directly (com.google.android.apps.bard)")
                    return AssistantLaunchResult.Success
                }
            } catch (e: Exception) {
                Log.w(tag, "Gemini app direct launch failed: ${e.message}")
            }
        }

        // 2. Trigger Android Voice Assist Intent (summons the floating voice overlay)
        val voiceIntents = listOf(
            // Direct Google Assistant voice overlay
            Intent("android.intent.action.VOICE_ASSIST").apply {
                setPackage("com.google.android.googlequicksearchbox")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            // Generic VOICE_ASSIST
            Intent("android.intent.action.VOICE_ASSIST").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            // System Default ACTION_VOICE_COMMAND
            Intent(Intent.ACTION_VOICE_COMMAND).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            // System ACTION_ASSIST
            Intent(Intent.ACTION_ASSIST).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )

        for (intent in voiceIntents) {
            try {
                context.startActivity(intent)
                Log.d(tag, "Successfully launched assistant via: ${intent.action}")
                return AssistantLaunchResult.Success
            } catch (e: Exception) {
                Log.d(tag, "Intent failed (${intent.action}): ${e.message}")
            }
        }

        Log.e(tag, "No voice assistant could be launched.")
        return AssistantLaunchResult.NoAssistantConfigured
    }

    /**
     * Opens Android System Settings directly to the Default Digital Assistant page.
     */
    fun openAssistantSettings() {
        val intents = listOf(
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )

        for (intent in intents) {
            try {
                context.startActivity(intent)
                return
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Opens Android Accessibility Settings so the user can enable AirAssistant Key Interceptor.
     */
    fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (ignored: Exception) {}
    }
}
