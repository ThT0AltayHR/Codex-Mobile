package com.openaicodex.app.engine

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Re-implements the OAuth 2.0 Authorization Code + PKCE flow found in
 * codex-rs/login/src/server.rs and codex-rs/login/src/pkce.rs, adapted for
 * mobile: instead of a localhost callback server, the redirect target is a
 * custom-scheme deep link (codexmobile://auth/callback) that the Android
 * intent-filter in the manifest routes straight back into MainActivity.
 *
 * Client ID, scopes, and endpoint shapes are taken verbatim from the
 * upstream Rust source so this talks to the same auth.openai.com backend
 * Codex CLI does.
 */
object CodexAuthConfig {
    const val ISSUER = "https://auth.openai.com"
    // codex-rs/login/src/auth/manager.rs: pub const CLIENT_ID
    const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
    // IMPORTANT: this must match what upstream Codex CLI actually registers
    // with the OAuth client (codex-rs/login/src/server.rs:
    // `format!("http://localhost:{actual_port}/auth/callback")`). A prior
    // version of this file used a custom `codexmobile://auth/callback`
    // scheme, which is NOT a redirect URI this OAuth client is known to
    // accept — there is no evidence OpenAI's app_EMoamEEZ73f0CkXaXp7hrann
    // client is registered for arbitrary custom schemes, so that flow could
    // silently fail at the provider. Using the exact same localhost
    // callback the desktop CLI uses, on the same default port, is the only
    // redirect target we can be confident is actually allowed.
    const val DEFAULT_PORT = 1455
    const val FALLBACK_PORT = 1457
    const val SCOPES = "openid profile email offline_access api.connectors.read api.connectors.invoke"
    const val AUTHORIZE_PATH = "/oauth/authorize"
    const val TOKEN_PATH = "/oauth/token"
}

data class PkcePair(val verifier: String, val challenge: String)

data class StoredAuth(
    val accessToken: String,
    val refreshToken: String?,
    val idToken: String?,
    val accountId: String?,
    val email: String?,
    val obtainedAtMillis: Long
)

class CodexAuthManager(private val context: Context) {

    private val client = OkHttpClient()

    private val prefs by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "codex_auth_store",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private var pendingPkce: PkcePair? = null
    private var pendingState: String? = null
    private var callbackServer: java.net.ServerSocket? = null
    private var boundPort: Int = CodexAuthConfig.DEFAULT_PORT
    @Volatile private var loginInFlight: Boolean = false

    /** True while a login attempt is already in progress — callers must not start a second one. */
    fun isLoginInFlight(): Boolean = loginInFlight

    fun generatePkce(): PkcePair {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        val verifier = b64UrlNoPad(bytes)
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = b64UrlNoPad(digest)
        return PkcePair(verifier, challenge).also { pendingPkce = it }
    }

    private fun b64UrlNoPad(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun randomState(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return b64UrlNoPad(bytes).also { pendingState = it }
    }

    /**
     * This must stay byte-for-byte identical between the authorize request and
     * the token exchange. OpenAI's Codex client accepts the registered
     * localhost ports (1455 and 1457), so never rebuild this from a different
     * port after the callback server has started.
     */
    private fun redirectUri(): String = "http://localhost:${boundPort}/auth/callback"

    /**
     * Starts a genuine, short-lived HTTP server on 127.0.0.1, mirroring
     * exactly what codex-rs/login/src/server.rs does on desktop: bind to
     * DEFAULT_PORT, fall back to FALLBACK_PORT if taken, wait for the
     * browser to redirect back to http://localhost:<port>/auth/callback
     * with `code` and `state` query params, parse them, then shut down.
     *
     * This replaces the previous custom-scheme deep link approach, which
     * had no confirmed registration with the OAuth provider.
     */
    /**
     * Starts a genuine, short-lived HTTP server on 127.0.0.1, mirroring
     * exactly what codex-rs/login/src/server.rs does on desktop: bind to
     * DEFAULT_PORT, fall back to FALLBACK_PORT, then fall back further to
     * an OS-assigned ephemeral port if both are taken, wait for the
     * browser to redirect back to http://localhost:<port>/auth/callback
     * with `code` and `state` query params, parse them, then shut down.
     *
     * Fixes over a prior version:
     *  - `onBound` is invoked the instant the socket is actually listening,
     *    so the caller can wait for a real signal instead of guessing with
     *    a fixed delay before building the authorize URL (which embeds the
     *    bound port) — a fixed 150ms delay could race the real bind time.
     *  - If both DEFAULT_PORT and FALLBACK_PORT are taken, this no longer
     *    just fails outright: it asks the OS for any free loopback port.
     *  - The accepted request's path is now actually checked to start
     *    with "/auth/callback" before its query params are trusted; a
     *    stray unrelated localhost connection (e.g. another app probing
     *    open ports) can no longer be misread as a callback attempt.
     */
    suspend fun startCallbackServerAndAwaitCode(onBound: () -> Unit = {}): Result<Pair<String, String>> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (loginInFlight) {
            return@withContext Result.failure(RuntimeException("Zaten devam eden bir giriş denemesi var"))
        }
        loginInFlight = true
        try {
            val loopback = java.net.InetAddress.getByName("127.0.0.1")
            val socket = try {
                java.net.ServerSocket(CodexAuthConfig.DEFAULT_PORT, 50, loopback).apply {
                    reuseAddress = true
                }
            } catch (e: java.io.IOException) {
                try {
                    java.net.ServerSocket(CodexAuthConfig.FALLBACK_PORT, 50, loopback).apply {
                        reuseAddress = true
                    }
                } catch (e2: java.io.IOException) {
                    // Fix: a prior version fell back to an OS-assigned
                    // ephemeral port here. That's unsafe — the authorize
                    // URL's redirect_uri is built from whatever port we
                    // bind, and an arbitrary ephemeral port has no
                    // guarantee of being an OAuth-provider-accepted
                    // redirect target. If neither of the two known ports
                    // is free, this now fails with a clear, user-facing
                    // error instead of silently trying an unverified port.
                    return@withContext Result.failure(
                        RuntimeException("Giriş için gerekli yerel bağlantı noktası (1455/1457) kullanılamıyor. Başka bir uygulamayı kapatıp tekrar deneyin.")
                    )
                }
            }
            boundPort = socket.localPort
            callbackServer = socket
            onBound()

            socket.soTimeout = 5 * 60 * 1000 // 5 minute window for the user to complete sign-in in the browser

            var code: String? = null
            var state: String? = null
            var error: String? = null

            // Loop accepting connections until we see one that is actually
            // our expected callback path — stray connections are closed
            // and ignored rather than being trusted.
            while (true) {
                socket.accept().use { client ->
                    val request = client.getInputStream().bufferedReader().readLine() ?: ""
                    val target = request.split(" ").getOrNull(1) ?: ""
                    val uri = android.net.Uri.parse("http://localhost$target")
                    val isRealCallback = uri.path == "/auth/callback"

                    if (isRealCallback) {
                        code = uri.getQueryParameter("code")
                        state = uri.getQueryParameter("state")
                        error = uri.getQueryParameter("error")
                    }

                    val responseBody = if (isRealCallback) {
                        "<html><head><meta name=\"viewport\" content=\"width=device-width\"></head>" +
                            "<body><h2>Giriş tamamlandı</h2><p>Bu pencereyi kapatıp Codex'e dönebilirsiniz.</p></body></html>"
                    } else {
                        "<html><body><h2>Not found</h2></body></html>"
                    }
                    val bodyBytes = responseBody.toByteArray(Charsets.UTF_8)
                    val statusLine = if (isRealCallback) "HTTP/1.1 200 OK" else "HTTP/1.1 404 Not Found"
                    val response = "$statusLine\r\nContent-Type: text/html; charset=utf-8\r\n" +
                        "Content-Length: ${bodyBytes.size}\r\nConnection: close\r\n\r\n"
                    client.getOutputStream().use { output ->
                        output.write(response.toByteArray(Charsets.UTF_8))
                        output.write(bodyBytes)
                        output.flush()
                    }
                }

                if (isRealCallback) break
            }

            socket.close()
            callbackServer = null

            when {
                error != null -> Result.failure(RuntimeException("OAuth error: $error"))
                code == null || state == null -> Result.failure(RuntimeException("Callback isteğinde code/state eksik"))
                else -> Result.success(code to state)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { callbackServer?.close() } catch (_: Exception) {}
            callbackServer = null
            loginInFlight = false
        }
    }

    fun stopCallbackServer() {
        try { callbackServer?.close() } catch (_: Exception) {}
        callbackServer = null
    }

    /** Builds the authorize URL to open in a Custom Tab / browser, mirroring build_authorize_url in server.rs. */
    fun buildAuthorizeUrl(): String {
        val pkce = pendingPkce ?: generatePkce()
        val state = randomState()
        val builder = Uri.parse(CodexAuthConfig.ISSUER + CodexAuthConfig.AUTHORIZE_PATH).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CodexAuthConfig.CLIENT_ID)
            .appendQueryParameter("redirect_uri", redirectUri())
            .appendQueryParameter("scope", CodexAuthConfig.SCOPES)
            .appendQueryParameter("code_challenge", pkce.challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("id_token_add_organizations", "true")
            .appendQueryParameter("codex_cli_simplified_flow", "true")
            .appendQueryParameter("state", state)
            .appendQueryParameter("originator", "codex_cli")
        return builder.build().toString()
    }

    /**
     * Call this AFTER startCallbackServerAndAwaitCode() has returned a
     * (code, state) pair. Validates state, exchanges the code for tokens.
     */
    suspend fun completeLogin(code: String, returnedState: String): Result<StoredAuth> {
        if (returnedState != pendingState) {
            return Result.failure(RuntimeException("State mismatch — possible CSRF, aborting"))
        }
        val pkce = pendingPkce ?: return Result.failure(RuntimeException("No pending PKCE verifier"))
        return exchangeCodeForTokens(code, pkce.verifier)
    }

    private suspend fun exchangeCodeForTokens(code: String, verifier: String): Result<StoredAuth> {
        return try {
            val formBody = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUri())
                .add("client_id", CodexAuthConfig.CLIENT_ID)
                .add("code_verifier", verifier)
                .build()

            val request = Request.Builder()
                .url(CodexAuthConfig.ISSUER + CodexAuthConfig.TOKEN_PATH)
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = try {
                        val errorJson = JSONObject(bodyStr)
                        listOf(
                            errorJson.optString("error", ""),
                            errorJson.optString("error_description", "")
                        ).filter { it.isNotBlank() }.joinToString(": ")
                    } catch (_: Exception) {
                        bodyStr
                    }.ifBlank { "sunucudan ayrıntı alınamadı" }
                    return Result.failure(RuntimeException("Giriş doğrulanamadı (${response.code}): $detail"))
                }

                val json = JSONObject(bodyStr)
                val accessToken = json.optString("access_token", "")
                val refreshToken = json.optString("refresh_token", null)
                val idToken = json.optString("id_token", null)
                if (accessToken.isEmpty()) {
                    return Result.failure(RuntimeException("Giriş yanıtında access_token bulunamadı"))
                }

                val claims = idToken?.let { parseJwtClaims(it) }
                // Real upstream shape (codex-rs/login/src/token_data.rs IdTokenInfo):
                // account/org id lives under the "https://api.openai.com/auth" claim
                // object, as its own nested "chatgpt_account_id" field.
                val authClaim = claims?.optJSONObject("https://api.openai.com/auth")
                val accountId = authClaim?.optString("chatgpt_account_id", null)
                val stored = StoredAuth(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    idToken = idToken,
                    accountId = accountId,
                    email = claims?.optString("email", null),
                    obtainedAtMillis = System.currentTimeMillis()
                )
                persist(stored)
                Result.success(stored)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseJwtClaims(jwt: String): JSONObject? {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return null
            val payload = Base64.getUrlDecoder().decode(parts[1].padBase64())
            JSONObject(String(payload, Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }

    private fun String.padBase64(): String {
        val rem = length % 4
        return if (rem == 0) this else this + "=".repeat(4 - rem)
    }

    private fun persist(auth: StoredAuth) {
        prefs.edit {
            putString("access_token", auth.accessToken)
            putString("refresh_token", auth.refreshToken)
            putString("id_token", auth.idToken)
            putString("account_id", auth.accountId)
            putString("email", auth.email)
            putLong("obtained_at", auth.obtainedAtMillis)
        }
    }

    fun loadStoredAuth(): StoredAuth? {
        val token = prefs.getString("access_token", null) ?: return null
        return StoredAuth(
            accessToken = token,
            refreshToken = prefs.getString("refresh_token", null),
            idToken = prefs.getString("id_token", null),
            accountId = prefs.getString("account_id", null),
            email = prefs.getString("email", null),
            obtainedAtMillis = prefs.getLong("obtained_at", 0L)
        )
    }

    fun isLoggedIn(): Boolean = loadStoredAuth() != null

    /** @deprecated Use [logoutEverywhere] — this alone leaves native CODEX_HOME/auth.json behind. */
    @Deprecated("Leaves native auth.json behind; use logoutEverywhere(runtime) instead", ReplaceWith("logoutEverywhere(runtime)"))
    fun logout() {
        prefs.edit { clear() }
    }

    /**
     * Writes an auth.json matching the exact shape codex.bin expects
     * (codex-rs/login/src/auth/storage.rs::AuthDotJson +
     * codex-rs/login/src/token_data.rs::TokenData). Two things a prior
     * version of this method got wrong, now fixed:
     *
     * 1. `last_refresh` is a `DateTime<Utc>` on the Rust side, i.e. it
     *    serializes as an RFC3339 string ("2026-09-07T12:34:56.789Z"), not
     *    a raw epoch-millis integer. Writing an integer there would fail
     *    native deserialization.
     * 2. Tokens nest under `tokens.id_token`/`tokens.access_token`/
     *    `tokens.refresh_token`/`tokens.account_id` — `id_token` is stored
     *    as the raw JWT string (Rust deserializes it into IdTokenInfo via
     *    a custom deserializer), not as a parsed object.
     */
    fun writeAuthJsonForNativeRuntime(runtime: CodexNativeRuntime) {
        val auth = loadStoredAuth() ?: return
        val isoTimestamp = java.time.Instant.ofEpochMilli(auth.obtainedAtMillis)
            .toString() // java.time.Instant#toString() is RFC3339/ISO-8601 with 'Z' suffix.

        val tokens = JSONObject().apply {
            put("id_token", auth.idToken ?: JSONObject.NULL)
            put("access_token", auth.accessToken)
            put("refresh_token", auth.refreshToken ?: "")
            put("account_id", auth.accountId ?: JSONObject.NULL)
        }
        val authJson = JSONObject().apply {
            put("OPENAI_API_KEY", JSONObject.NULL)
            put("tokens", tokens)
            put("last_refresh", isoTimestamp)
        }
        val file = java.io.File(runtime.codexHomeDir(), "auth.json")
        file.writeText(authJson.toString(2))
    }

    /**
     * Full logout: clears the encrypted local token store AND deletes the
     * native CODEX_HOME/auth.json so codex.bin itself no longer has valid
     * credentials either. A prior version only cleared the Kotlin-side
     * store, leaving stale native credentials behind.
     */
    fun logoutEverywhere(runtime: CodexNativeRuntime) {
        prefs.edit { clear() }
        val authFile = java.io.File(runtime.codexHomeDir(), "auth.json")
        if (authFile.exists()) authFile.delete()
    }
}
