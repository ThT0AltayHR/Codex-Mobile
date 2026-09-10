package com.codex.mobile.engine

import com.codex.mobile.data.UserPreferencesStore
import kotlinx.coroutines.flow.firstOrNull
import java.io.File

/**
 * Turns stored user preferences (name, bio, tone, language, custom prompt)
 * into an AGENTS.md file written at the root of the Codex workspace
 * directory before every turn.
 *
 * This exists because AGENTS.md is Codex's own, real, documented mechanism
 * for injecting persistent user/project instructions into every turn (see
 * codex-rs/core/src/agents_md.rs — DEFAULT_AGENTS_MD_FILENAME). Writing to
 * this file is not a workaround; it is the sanctioned integration point.
 * Before this class existed, UserPreferencesStore only wrote to local
 * DataStore and nothing carried that data into the model's context — the
 * "Codex never forgets" claim was UI-only. This closes that gap for real.
 */
class CodexPromptComposer(
    private val prefsStore: UserPreferencesStore,
    private val runtime: CodexNativeRuntime
) {

    /**
     * Regenerates AGENTS.md from current stored preferences. Call this
     * before starting a turn so the native process picks up the latest
     * values (name/bio/tone/language may have changed since the app
     * started).
     */
    /**
     * Regenerates AGENTS.md for a specific conversation's isolated
     * workspace. Each conversation now has its own workspace directory
     * (see CodexNativeRuntime.workspaceDir(conversationId)), so its
     * AGENTS.md is written there rather than to one shared root file —
     * personalization/memory content is the same across conversations,
     * but files/attachments never leak between them.
     */
    suspend fun syncAgentsMd(conversationId: String? = null) {
        val name = prefsStore.userName.firstOrNull()
        val bio = prefsStore.userBio.firstOrNull()
        val tone = prefsStore.tone.firstOrNull() ?: "balanced"
        val languageName = prefsStore.languageName.firstOrNull()
        val customPrompt = prefsStore.customPrompt.firstOrNull()

        val builder = StringBuilder()
        builder.appendLine("# Kullanıcı Bağlamı (Codex Mobile tarafından otomatik oluşturuldu)")
        builder.appendLine()
        builder.appendLine("Bu dosya, uygulama ayarlarında saklanan tercihlerden otomatik üretilir.")
        builder.appendLine("Elle düzenleme; bir sonraki mesajda üzerine yazılır.")
        builder.appendLine()

        if (!languageName.isNullOrBlank()) {
            builder.appendLine("## Dil")
            builder.appendLine("Kullanıcıyla her zaman **$languageName** dilinde konuş, aksi açıkça istenmedikçe.")
            builder.appendLine()
        }

        if (!name.isNullOrBlank()) {
            builder.appendLine("## Kullanıcı")
            builder.appendLine("Kullanıcının adı: $name. Ona bu isimle hitap edebilirsin.")
            if (!bio.isNullOrBlank()) {
                builder.appendLine("Kullanıcı kendini şöyle tanımladı: \"$bio\"")
            }
            builder.appendLine()
        }

        builder.appendLine("## Konuşma tonu")
        builder.appendLine(
            when (tone) {
                "friendly" -> "Sıcak, samimi ve rahat bir üslup kullan."
                "professional" -> "Profesyonel, net ve öz bir üslup kullan."
                "playful" -> "Enerjik, esprili ve hevesli bir üslup kullan."
                else -> "Dengeli, yardımsever bir üslup kullan."
            }
        )
        builder.appendLine()

        if (!customPrompt.isNullOrBlank()) {
            builder.appendLine("## Özel talimatlar")
            builder.appendLine(customPrompt)
            builder.appendLine()
        }

        val agentsFile = File(runtime.workspaceDir(conversationId), "AGENTS.md")
        agentsFile.writeText(builder.toString())
    }
}
