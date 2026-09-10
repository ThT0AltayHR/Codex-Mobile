package com.codex.mobile.engine

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Secure Secret Vault: Keystore-backed encrypted storage for secrets
 * (API tokens, etc) that Codex's child process may need. Secrets are
 * NEVER written into AGENTS.md, the prompt text sent to Codex, chat
 * messages, conversations.json, or logs — they are only ever injected as
 * process environment variables for the single task that needs them (see
 * CodexNativeRuntime.launch's extraEnv parameter), then that env map is
 * discarded when the process exits.
 */
class SecretVault(context: Context) {

    private val prefs by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "codex_secret_vault",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun has(name: String): Boolean = prefs.contains(name)

    fun get(name: String): String? = prefs.getString(name, null)

    fun set(name: String, value: String) {
        prefs.edit { putString(name, value) }
    }

    fun delete(name: String) {
        prefs.edit { remove(name) }
    }

    fun listNames(): Set<String> = prefs.all.keys

    fun clearAll() {
        prefs.edit { clear() }
    }
}

/**
 * Detects when Codex's stderr/output is asking for a named secret it
 * doesn't have (e.g. "EXPO_TOKEN is required", "Missing API key: X"),
 * so the app can pause and show a secret-entry form instead of the task
 * just failing. This is heuristic — it only recognizes patterns Codex's
 * own CLI actually emits for missing env vars, it does not invent secret
 * names that were never mentioned.
 */
object SecretRequestDetector {
    private val patterns = listOf(
        Regex("([A-Z][A-Z0-9_]{2,})\\s+(?:is required|environment variable is not set|not set|is missing)", RegexOption.IGNORE_CASE),
        Regex("Missing (?:API key|token|secret)[:\\s]+([A-Z][A-Z0-9_]{2,})", RegexOption.IGNORE_CASE),
        Regex("([A-Z][A-Z0-9_]{2,})\\s+environment variable"),
    )

    fun detectMissingSecretName(text: String): String? {
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val candidate = match.groupValues.getOrNull(1) ?: continue
            if (candidate.length in 3..64) return candidate.uppercase()
        }
        return null
    }
}
