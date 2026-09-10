package com.openaicodex.app.data

data class WebSource(val url: String, val domain: String)

data class GeneratedFile(val name: String, val path: String)

data class ChatMessage(
    val id: String,
    val role: Role,
    val content: String,
    val timestamp: Long,
    val isCodeBlock: Boolean = false,
    val language: String = "text",
    val attachedFileName: String? = null,
    val attachedFilePath: String? = null,
    val generatedImagePath: String? = null,
    val generatedFiles: List<GeneratedFile> = emptyList(),
    /** Real URLs Codex actually visited during this turn, extracted from web_search item events — never fabricated. */
    val webSources: List<WebSource> = emptyList()
) {
    enum class Role { USER, ASSISTANT, SYSTEM_STEP }
}

data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val messages: List<ChatMessage> = emptyList()
)
