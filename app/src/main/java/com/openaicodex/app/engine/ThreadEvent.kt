package com.openaicodex.app.engine

import org.json.JSONObject

/**
 * Mirrors codex-rs/exec/src/exec_events.rs exactly.
 *
 * A prior version of this parser read a nonexistent `item_type` field
 * (with `type` as a fallback) and pulled agent text from `text` OR a
 * nonexistent `content` field. The real schema is:
 *   { "type": "item.completed", "item": { "id": "...", "type": "agent_message", "text": "..." } }
 * i.e. the discriminator is `item.type`, not `item.item_type`, and
 * AgentMessageItem/ReasoningItem both only ever have a `text` field — there
 * is no `content` fallback in the real payload. Reading the wrong field
 * silently produced empty assistant messages whenever the real schema was
 * hit, which is exactly the kind of schema-drift breakage described as a
 * known risk for downstream parsers of this event stream.
 */
sealed class ThreadEvent {
    data class ThreadStarted(val threadId: String) : ThreadEvent()
    data object TurnStarted : ThreadEvent()
    data class TurnCompleted(val usage: JSONObject?) : ThreadEvent()
    data class TurnFailed(val message: String?) : ThreadEvent()
    data class ItemStarted(val itemType: String, val raw: JSONObject) : ThreadEvent()
    data class ItemUpdated(val itemType: String, val raw: JSONObject) : ThreadEvent()
    data class ItemCompleted(val itemType: String, val raw: JSONObject) : ThreadEvent()
    data class ThreadErrorEvent(val message: String) : ThreadEvent()
    data class Unknown(val raw: String, val rawType: String?) : ThreadEvent()

    companion object {
        fun parse(line: String): ThreadEvent? {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null
            return try {
                val json = JSONObject(trimmed)
                when (val eventType = json.optString("type", "")) {
                    "thread.started" -> {
                        val threadId = json.optString("thread_id", "")
                        if (threadId.isBlank()) Unknown(trimmed, eventType) else ThreadStarted(threadId)
                    }
                    "turn.started" -> TurnStarted
                    "turn.completed" -> TurnCompleted(json.optJSONObject("usage"))
                    "turn.failed" -> {
                        val errorObj = json.optJSONObject("error")
                        TurnFailed(errorObj?.optString("message"))
                    }
                    "item.started" -> {
                        val item = json.optJSONObject("item") ?: JSONObject()
                        ItemStarted(item.optString("type", "unknown"), item)
                    }
                    "item.updated" -> {
                        val item = json.optJSONObject("item") ?: JSONObject()
                        ItemUpdated(item.optString("type", "unknown"), item)
                    }
                    "item.completed" -> {
                        val item = json.optJSONObject("item") ?: JSONObject()
                        ItemCompleted(item.optString("type", "unknown"), item)
                    }
                    "error" -> ThreadErrorEvent(json.optString("message", "unknown error"))
                    else -> Unknown(trimmed, eventType.ifBlank { null })
                }
            } catch (e: Exception) {
                Unknown(trimmed, null)
            }
        }

        /**
         * Extracts the assistant-visible text from an agent_message item.
         * Per AgentMessageItem's real shape, this is always the `text`
         * field — never `content`.
         */
        fun extractAgentMessageText(item: JSONObject): String = item.optString("text", "")

        /** Extracts reasoning text (ReasoningItem also only has `text`). */
        fun extractReasoningText(item: JSONObject): String = item.optString("text", "")

        /** CommandExecutionItem: command, aggregated_output, exit_code, status. */
        fun extractCommandExitCode(item: JSONObject): Int? =
            if (item.has("exit_code") && !item.isNull("exit_code")) item.optInt("exit_code") else null

        fun extractCommandStatus(item: JSONObject): String = item.optString("status", "in_progress")

        /**
         * Extracts a real visited URL from a web_search item's `action`
         * field, per the actual WebSearchAction enum
         * (codex-rs/protocol/src/models.rs): only the OpenPage and
         * FindInPage variants carry a `url`; the Search variant only
         * carries a query string with no URL at all. Returns null rather
         * than a placeholder when no real URL is present in the event —
         * a source card is only ever shown for events that actually
         * contained one.
         */
        fun extractWebSearchUrl(item: JSONObject): String? {
            val action = item.optJSONObject("action") ?: return null
            return when (action.optString("type")) {
                "open_page", "find_in_page" -> action.optString("url", null).takeUnless { it.isNullOrBlank() }
                else -> null
            }
        }

        fun extractWebSearchQuery(item: JSONObject): String? {
            val action = item.optJSONObject("action") ?: return null
            if (action.optString("type") != "search") return null
            return action.optString("query", null)
        }
    }
}

/**
 * Turkish "samurai step" labels shown as an animated strip beneath
 * assistant replies. Every label here is only ever shown because a real
 * Codex event justified it (see SamuraiStepMapper below) — none of these
 * are decorative/random; WORKING_GENERIC exists specifically so an
 * unrecognized event type still shows *something* honest ("Codex is
 * working") rather than either freezing on a stale specific label or
 * inventing a fake specific one.
 */
enum class SamuraiStep(val label: String) {
    THINKING("Codex düşünüyor"),
    PLANNING("Codex planlıyor"),
    WRITING_CODE("Codex kod yazıyor"),
    EDITING_FILE("Codex dosyayı düzenliyor"),
    CREATING_FILE("Codex dosya oluşturuyor"),
    DELETING_FILE("Codex dosya siliyor"),
    RUNNING_COMMAND("Codex komut çalıştırıyor"),
    INSTALLING_PACKAGE("Codex paket kuruyor"),
    BUILDING("Codex projeyi derliyor"),
    TESTING("Codex testleri çalıştırıyor"),
    GIT_OPERATION("Codex git işlemi gerçekleştiriyor"),
    INSPECTING_ARCHIVE("Codex arşivi inceliyor"),
    SCANNING_PROJECT("Codex proje dosyalarını tarıyor"),
    SEARCHING_WEB("Codex web'de arıyor"),
    PREPARING_SUMMARY("Codex özet hazırlıyor"),
    FINISHING("Codex işlemi tamamlıyor"),
    WORKING_GENERIC("Codex çalışıyor"),
    IDLE("")
}

/**
 * A single completed/failed samurai step with whatever real detail the
 * originating Codex event actually carried — file path, command text, or
 * source count. Fields are null when the event didn't contain that
 * information; the UI never fabricates a value for a null field.
 */
data class SamuraiStepEntry(
    val step: SamuraiStep,
    val filePath: String? = null,
    val command: String? = null,
    val sourceCount: Int? = null,
    val timestampMillis: Long = System.currentTimeMillis()
)


/**
 * display. Unknown/unmapped item types intentionally fall through to null
 * (caller keeps showing the previous step) rather than silently freezing
 * on a stale label with no indication anything changed.
 */
object SamuraiStepMapper {
    fun mapEvent(event: ThreadEvent): SamuraiStep? = when (event) {
        is ThreadEvent.TurnStarted -> SamuraiStep.THINKING
        is ThreadEvent.ItemStarted -> mapItem(event.itemType, event.raw)
        is ThreadEvent.ItemUpdated -> mapItem(event.itemType, event.raw, isUpdate = true)
        is ThreadEvent.TurnCompleted -> SamuraiStep.FINISHING
        else -> null
    }

    /**
     * Inspects the actual command text of a command_execution item (the
     * real CommandExecutionItem.command field — see exec_events.rs) to
     * pick a more specific, honest label than a generic "running a
     * command" when the command itself makes the intent unambiguous
     * (installing a package, running tests, building, git, unzipping).
     * If the command doesn't match any recognized pattern, it falls back
     * to the generic RUNNING_COMMAND label rather than guessing.
     */
    private fun mapItem(itemType: String, raw: org.json.JSONObject, isUpdate: Boolean = false): SamuraiStep? = when (itemType) {
        "reasoning" -> SamuraiStep.THINKING
        "command_execution" -> classifyCommand(raw.optString("command", ""))
        "file_change" -> classifyFileChange(raw, isUpdate)
        "web_search" -> SamuraiStep.SEARCHING_WEB
        "todo_list" -> SamuraiStep.PLANNING
        "agent_message" -> SamuraiStep.PREPARING_SUMMARY
        "mcp_tool_call", "collab_tool_call" -> SamuraiStep.WORKING_GENERIC
        else -> null
    }

    private fun classifyCommand(command: String): SamuraiStep {
        val c = command.lowercase()
        return when {
            c.contains("unzip") || c.contains("tar -x") || c.contains("tar -z") -> SamuraiStep.INSPECTING_ARCHIVE
            c.contains("find ") || c.contains("ls -r") || c.contains("tree ") -> SamuraiStep.SCANNING_PROJECT
            c.startsWith("npm install") || c.startsWith("pip install") || c.startsWith("apt") ||
                c.startsWith("yarn add") || c.startsWith("pnpm add") || c.contains("gradle") && c.contains("dependencies") -> SamuraiStep.INSTALLING_PACKAGE
            c.contains("gradlew") && (c.contains("assemble") || c.contains("build")) -> SamuraiStep.BUILDING
            c.startsWith("make") || c.contains(" build") || c.startsWith("go build") || c.startsWith("cargo build") -> SamuraiStep.BUILDING
            c.contains("test") || c.contains("pytest") || c.contains("jest") || c.contains("junit") -> SamuraiStep.TESTING
            c.startsWith("git ") -> SamuraiStep.GIT_OPERATION
            c.startsWith("rm ") || c.startsWith("rm -") -> SamuraiStep.DELETING_FILE
            else -> SamuraiStep.RUNNING_COMMAND
        }
    }

    private fun classifyFileChange(raw: org.json.JSONObject, isUpdate: Boolean): SamuraiStep {
        // FileChangeItem carries a `changes` list with per-file kind info
        // in the real schema; when present we can distinguish create vs
        // edit vs delete rather than always saying "creating a file".
        val changes = raw.optJSONArray("changes")
        if (changes != null && changes.length() > 0) {
            val firstKind = changes.optJSONObject(0)?.optString("kind", "")
            return when (firstKind) {
                "add" -> SamuraiStep.CREATING_FILE
                "delete" -> SamuraiStep.DELETING_FILE
                "update" -> SamuraiStep.EDITING_FILE
                else -> if (isUpdate) SamuraiStep.WRITING_CODE else SamuraiStep.CREATING_FILE
            }
        }
        return if (isUpdate) SamuraiStep.WRITING_CODE else SamuraiStep.CREATING_FILE
    }

    /**
     * Extracts a real file path from a file_change item's `changes[0].path`
     * field (the actual FileChangeItem schema) — returns null, never a
     * placeholder, if the event doesn't carry a path.
     */
    fun extractFilePath(raw: org.json.JSONObject): String? {
        val changes = raw.optJSONArray("changes") ?: return null
        if (changes.length() == 0) return null
        val path = changes.optJSONObject(0)?.optString("path", null)
        return path?.takeUnless { it.isBlank() }
    }

    /** Real command text from a command_execution item, or null if absent. */
    fun extractCommandText(raw: org.json.JSONObject): String? =
        raw.optString("command", null)?.takeUnless { it.isBlank() }

    /**
     * Builds a SamuraiStepEntry from a completed/failed item event,
     * carrying whatever real detail (path/command) that event's JSON
     * actually contains — never inventing a value for a field the event
     * didn't provide.
     */
    fun toEntry(itemType: String, raw: org.json.JSONObject): SamuraiStepEntry {
        val step = mapItem(itemType, raw) ?: SamuraiStep.WORKING_GENERIC
        return SamuraiStepEntry(
            step = step,
            filePath = if (itemType == "file_change") extractFilePath(raw) else null,
            command = if (itemType == "command_execution") extractCommandText(raw) else null
        )
    }
}
