package com.openaicodex.app.engine

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
/**
 * Per-conversation vault. Each conversationId gets its own encrypted
 * SharedPreferences file, so secrets stored while working in one chat are
 * never visible to, injected into, or even enumerable from any other chat.
 *
 * IMPORTANT: values are intentionally NEVER exposed after being written.
 * There is no `get(name)` that returns a raw value to any UI-reachable
 * caller in this class other than [valueForInjection], which is used
 * exclusively by the process-launch code path (CodexProcessService) to
 * build an environment map for the child process. The UI-facing API
 * surface (see [listNames], [has]) only ever returns secret *names*.
 */
class SecretVault(private val context: Context, private val conversationId: String) {

    private fun prefsFileName(id: String): String {
        // Derive the file name from a SHA-256 hash of the raw conversationId
        // rather than a filtered/truncated version of the id itself. This
        // guarantees collision-resistance (two different ids can never map
        // to the same vault file, which a simple character filter cannot
        // guarantee once truncation or filtering is involved) and keeps the
        // file name within a safe, fixed length regardless of id content.
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(id.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "codex_secret_vault_$hex"
    }

    private val prefs by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            prefsFileName(conversationId),
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** True if a secret with this name exists in THIS conversation's vault only. */
    fun has(name: String): Boolean = prefs.contains(name)

    fun set(name: String, value: String) {
        prefs.edit { putString(name, value) }
    }

    fun delete(name: String) {
        prefs.edit { remove(name) }
    }

    /**
     * Names only — never values. Internal lock-metadata keys (pattern
     * hash, attempt counter, lockout timestamp — see below) are filtered
     * out so they never appear in the UI's secret list. This is the only
     * listing method and is safe to call from any settings/vault UI:
     * nothing here can be copied or displayed as a secret value because
     * no value is ever returned.
     */
    fun listNames(): Set<String> = prefs.all.keys.filterNot { it.startsWith("__") }.toSet()

    fun clearAll() {
        prefs.edit { clear() }
    }

    /**
     * Returns the raw secret value for env-var injection into the Codex
     * child process for this conversation's task only. Not for UI use —
     * callers outside the process-launch path must not surface this.
     */
    fun valueForInjection(name: String): String? = prefs.getString(name, null)

    // --- Pattern lock + brute-force lockout, scoped to THIS conversation's
    // vault file — a pattern set while working in one chat never applies
    // to, or is checked against, any other chat's vault. Metadata keys are
    // prefixed with "__" and excluded from listNames() above. ---

    private fun sha256(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun hasPatternLock(): Boolean = prefs.contains(KEY_PATTERN_HASH)

    fun setPatternLock(cellSequence: List<Int>) {
        prefs.edit {
            putString(KEY_PATTERN_HASH, sha256(cellSequence.joinToString(",")))
            putInt(KEY_FAILED_ATTEMPTS, 0)
            remove(KEY_LOCKOUT_UNTIL)
        }
    }

    fun removePatternLock() {
        prefs.edit {
            remove(KEY_PATTERN_HASH)
            remove(KEY_FAILED_ATTEMPTS)
            remove(KEY_LOCKOUT_UNTIL)
        }
    }

    /** Millis since epoch until which the vault is locked out, or 0 if not locked out. */
    fun lockoutUntil(): Long = prefs.getString(KEY_LOCKOUT_UNTIL, "0")?.toLongOrNull() ?: 0L

    fun isLockedOut(now: Long = System.currentTimeMillis()): Boolean = now < lockoutUntil()

    /**
     * Returns true if the pattern matches. On failure, increments the
     * attempt counter and — once [MAX_ATTEMPTS] consecutive failures are
     * reached — sets an exponentially growing lockout window (30s, 60s,
     * 120s, ... capped at 30 min) before another attempt is accepted.
     */
    fun verifyPattern(cellSequence: List<Int>): Boolean {
        if (isLockedOut()) return false
        val expected = prefs.getString(KEY_PATTERN_HASH, null) ?: return false
        val matches = sha256(cellSequence.joinToString(",")) == expected
        if (matches) {
            prefs.edit { putInt(KEY_FAILED_ATTEMPTS, 0) }
            return true
        }
        val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        prefs.edit { putInt(KEY_FAILED_ATTEMPTS, attempts) }
        if (attempts >= MAX_ATTEMPTS) {
            val extraLockouts = attempts - MAX_ATTEMPTS
            val backoffSeconds = (30L * (1L shl extraLockouts.coerceAtMost(6))).coerceAtMost(30 * 60L)
            val until = System.currentTimeMillis() + backoffSeconds * 1000
            prefs.edit { putString(KEY_LOCKOUT_UNTIL, until.toString()) }
        }
        return false
    }

    fun remainingAttemptsBeforeLockout(): Int =
        (MAX_ATTEMPTS - prefs.getInt(KEY_FAILED_ATTEMPTS, 0)).coerceAtLeast(0)

    companion object {
        private const val KEY_PATTERN_HASH = "__pattern_lock_hash"
        private const val KEY_FAILED_ATTEMPTS = "__pattern_failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "__pattern_lockout_until"
        private const val MAX_ATTEMPTS = 5

        /** Vault for a conversation, isolated from every other conversation's vault. */
        fun forConversation(context: Context, conversationId: String): SecretVault =
            SecretVault(context.applicationContext, conversationId)
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
