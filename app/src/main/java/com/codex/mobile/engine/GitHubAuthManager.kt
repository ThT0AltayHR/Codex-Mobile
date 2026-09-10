package com.codex.mobile.engine

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.delay
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * GitHub connection using OAuth Device Flow — GitHub's own documented
 * flow for native apps that cannot securely hold a client secret.
 *
 * Why Device Flow and not the same PKCE+localhost approach used for
 * OpenAI login: GitHub's OAuth Apps token endpoint requires a
 * client_secret for the standard authorization-code exchange. Embedding
 * that secret in this APK would violate the requirement that it never
 * ship client-side. Device Flow needs only the client_id: the app
 * requests a device code, shows the user a short code to enter at
 * github.com/login/device in their own browser, and polls for a token.
 * No secret, no custom redirect URI, and no shared callback mechanism
 * with the OpenAI/Codex login (which uses a local HTTP server on
 * 127.0.0.1 — see CodexAuthManager). These two flows never touch the
 * same code path.
 */
class GitHubAuthManager(context: Context) {

    private val client = OkHttpClient()

    private val prefs by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "codex_github_store",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    data class DeviceCodeInfo(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val expiresInSeconds: Int,
        val pollIntervalSeconds: Int
    )

    fun isConnected(): Boolean = prefs.getString("access_token", null) != null

    fun connectedLogin(): String? = prefs.getString("login", null)

    suspend fun requestDeviceCode(): Result<DeviceCodeInfo> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .add("client_id", GITHUB_CLIENT_ID)
                .add("scope", "repo")
                .build()
            val request = Request.Builder()
                .url("https://github.com/login/device/code")
                .header("Accept", "application/json")
                .post(body)
                .build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful || json.has("error")) {
                return@withContext Result.failure(RuntimeException(json.optString("error_description", "GitHub device code request failed")))
            }
            Result.success(
                DeviceCodeInfo(
                    deviceCode = json.getString("device_code"),
                    userCode = json.getString("user_code"),
                    verificationUri = json.getString("verification_uri"),
                    expiresInSeconds = json.optInt("expires_in", 900),
                    pollIntervalSeconds = json.optInt("interval", 5)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pollForToken(deviceCode: String, intervalSeconds: Int, expiresInSeconds: Int): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        var currentInterval = intervalSeconds
        val deadline = System.currentTimeMillis() + expiresInSeconds * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(currentInterval * 1000L)
            try {
                val body = FormBody.Builder()
                    .add("client_id", GITHUB_CLIENT_ID)
                    .add("device_code", deviceCode)
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .build()
                val request = Request.Builder()
                    .url("https://github.com/login/oauth/access_token")
                    .header("Accept", "application/json")
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                val json = JSONObject(response.body?.string().orEmpty())

                val error = json.optString("error", "")
                if (error.isEmpty()) {
                    val token = json.optString("access_token", null)
                    if (token != null) {
                        persistToken(token)
                        fetchAndStoreLogin(token)
                        return@withContext Result.success(token)
                    }
                } else when (error) {
                    "authorization_pending" -> continue
                    "slow_down" -> currentInterval += 5
                    "expired_token" -> return@withContext Result.failure(RuntimeException("Kod süresi doldu, tekrar deneyin"))
                    "access_denied" -> return@withContext Result.failure(RuntimeException("Bağlantı reddedildi"))
                    else -> return@withContext Result.failure(RuntimeException(json.optString("error_description", "Bilinmeyen hata")))
                }
            } catch (e: Exception) {
                // Transient network error while polling — keep trying until the deadline.
            }
        }
        Result.failure(RuntimeException("Zaman aşımı"))
    }

    private fun fetchAndStoreLogin(token: String) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/user")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .build()
            val response = client.newCall(request).execute()
            val json = JSONObject(response.body?.string().orEmpty())
            val login = json.optString("login", null)
            if (login != null) prefs.edit { putString("login", login) }
        } catch (_: Exception) {
        }
    }

    private fun persistToken(token: String) {
        prefs.edit { putString("access_token", token) }
    }

    /** Real token for Codex's child process environment — never written to AGENTS.md or prompt text, never logged. */
    fun tokenForProcessEnv(): String? = prefs.getString("access_token", null)

    fun disconnect() {
        prefs.edit { clear() }
    }

    companion object {
        const val GITHUB_CLIENT_ID = "Ov23litjSFZ4TJiyRlPn"
    }
}
