package com.codex.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "codex_prefs")

/**
 * Stores onboarding state (language, user's name, self-description),
 * personalization settings (tone) and appearance preferences (text scale,
 * motion, haptics). This is the "never forgets" persistent memory the user
 * asked for: it survives app restarts and is threaded back into every
 * conversation as system context (see CodexPromptComposer).
 */
class UserPreferencesStore(private val context: Context) {

    private object Keys {
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val LANGUAGE_NAME = stringPreferencesKey("language_name")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_BIO = stringPreferencesKey("user_bio")
        val TONE = stringPreferencesKey("assistant_tone")
        val CUSTOM_PROMPT = stringPreferencesKey("custom_system_prompt")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    }

    val languageCode: Flow<String?> = context.dataStore.data.map { it[Keys.LANGUAGE_CODE] }
    val languageName: Flow<String?> = context.dataStore.data.map { it[Keys.LANGUAGE_NAME] }
    val userName: Flow<String?> = context.dataStore.data.map { it[Keys.USER_NAME] }
    val userBio: Flow<String?> = context.dataStore.data.map { it[Keys.USER_BIO] }
    val tone: Flow<String> = context.dataStore.data.map { it[Keys.TONE] ?: "balanced" }
    val customPrompt: Flow<String?> = context.dataStore.data.map { it[Keys.CUSTOM_PROMPT] }
    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }
    val textScale: Flow<Float> = context.dataStore.data.map { it[Keys.TEXT_SCALE] ?: 1.0f }
    val reduceMotion: Flow<Boolean> = context.dataStore.data.map { it[Keys.REDUCE_MOTION] ?: false }
    val hapticsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.HAPTICS_ENABLED] ?: true }

    suspend fun setLanguage(code: String, displayName: String) {
        context.dataStore.edit {
            it[Keys.LANGUAGE_CODE] = code
            it[Keys.LANGUAGE_NAME] = displayName
        }
    }

    suspend fun setUserIdentity(name: String, bio: String) {
        context.dataStore.edit {
            it[Keys.USER_NAME] = name
            it[Keys.USER_BIO] = bio
        }
    }

    suspend fun setTone(tone: String) {
        context.dataStore.edit { it[Keys.TONE] = tone }
    }

    suspend fun setCustomPrompt(prompt: String) {
        context.dataStore.edit { it[Keys.CUSTOM_PROMPT] = prompt }
    }

    suspend fun markOnboardingDone() {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    }

    suspend fun setTextScale(scale: Float) {
        context.dataStore.edit { it[Keys.TEXT_SCALE] = scale }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REDUCE_MOTION] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }
}
