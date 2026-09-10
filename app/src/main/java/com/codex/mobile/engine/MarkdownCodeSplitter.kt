package com.codex.mobile.engine

/**
 * Splits a raw assistant message into an ordered list of segments: plain
 * prose or fenced code blocks (```lang ... ```). This is what actually
 * drives `isCodeBlock` on ChatMessage — a prior version never set
 * isCodeBlock to true anywhere, so CodeBlockCard was fully built but never
 * reachable from real model output.
 */
data class MessageSegment(val isCode: Boolean, val language: String, val content: String)

object MarkdownCodeSplitter {

    private val fenceRegex = Regex("```([a-zA-Z0-9_+-]*)\\n([\\s\\S]*?)```")

    fun split(text: String): List<MessageSegment> {
        val segments = mutableListOf<MessageSegment>()
        var lastIndex = 0

        for (match in fenceRegex.findAll(text)) {
            if (match.range.first > lastIndex) {
                val prose = text.substring(lastIndex, match.range.first).trim()
                if (prose.isNotEmpty()) segments.add(MessageSegment(isCode = false, language = "", content = prose))
            }
            val language = match.groupValues[1].ifBlank { "text" }
            val code = match.groupValues[2].trimEnd('\n')
            segments.add(MessageSegment(isCode = true, language = language, content = code))
            lastIndex = match.range.last + 1
        }

        if (lastIndex < text.length) {
            val trailing = text.substring(lastIndex).trim()
            if (trailing.isNotEmpty()) segments.add(MessageSegment(isCode = false, language = "", content = trailing))
        }

        // No fences found at all: whole message is prose.
        if (segments.isEmpty() && text.isNotBlank()) {
            segments.add(MessageSegment(isCode = false, language = "", content = text.trim()))
        }

        return segments
    }
}
