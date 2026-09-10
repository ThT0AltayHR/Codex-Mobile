package com.openaicodex.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Persists conversation history to a single JSON file under app filesDir.
 * This is intentionally simple (no Room/SQLite dependency) since expected
 * scale is "one person's chat history", not a multi-user database. The
 * shape is still swappable for Room later without touching call sites,
 * since everything is expressed through this repository interface.
 *
 * Two correctness fixes over a prior version:
 *  1. Writes are now atomic: write-to-temp-then-rename instead of a direct
 *     writeText() on the live file. A previous version could leave
 *     conversations.json truncated/corrupt if the process died mid-write,
 *     which would then throw on the next JSONArray(text) parse and could
 *     break the whole history screen.
 *  2. An in-memory cache avoids re-reading and re-parsing the entire file
 *     on every single appendMessage() call (which happens multiple times
 *     per assistant reply once code-block segmentation is in play) —
 *     previously every append was a full read-modify-write-entire-file
 *     cycle on the calling thread.
 */
class ConversationRepository(context: Context) {

    private val storeFile = File(context.filesDir, "conversations.json")
    private val threadIdFile = File(context.filesDir, "thread_ids.json")
    private val lock = ReentrantLock()

    @Volatile
    private var cache: MutableList<Conversation>? = null

    private fun loadFromDisk(): MutableList<Conversation> {
        if (!storeFile.exists()) return mutableListOf()
        val text = storeFile.readText().ifBlank { return mutableListOf() }
        return try {
            val arr = JSONArray(text)
            val result = mutableListOf<Conversation>()
            for (i in 0 until arr.length()) {
                result.add(conversationFromJson(arr.getJSONObject(i)))
            }
            result
        } catch (e: Exception) {
            // Corrupt file: back it up rather than silently discarding the
            // user's chat history — a prior version just returned an
            // empty list here, which looks to the user exactly like "all
            // my conversations vanished" with zero recovery path.
            try {
                val backupFile = File(storeFile.parentFile, "conversations.corrupt.${System.currentTimeMillis()}.json")
                storeFile.copyTo(backupFile, overwrite = true)
            } catch (_: Exception) {
            }
            mutableListOf()
        }
    }

    private fun ensureCache(): MutableList<Conversation> = lock.withLock {
        cache ?: loadFromDisk().also { cache = it }
    }

    fun listConversations(): List<Conversation> = lock.withLock {
        ensureCache().sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.updatedAt })
    }

    fun getConversation(id: String): Conversation? = listConversations().find { it.id == id }

    fun createConversation(initialTitle: String = "Yeni sohbet"): Conversation {
        val now = System.currentTimeMillis()
        val convo = Conversation(
            id = UUID.randomUUID().toString(),
            title = initialTitle,
            createdAt = now,
            updatedAt = now,
            pinned = false,
            messages = emptyList()
        )
        lock.withLock {
            ensureCache().add(convo)
            persist()
        }
        return convo
    }

    fun appendMessage(conversationId: String, message: ChatMessage) {
        lock.withLock {
            val all = ensureCache()
            val idx = all.indexOfFirst { it.id == conversationId }
            if (idx == -1) return
            val existing = all[idx]
            val updatedMessages = existing.messages + message
            val autoTitle = if (existing.messages.isEmpty() && message.role == ChatMessage.Role.USER) {
                message.content.take(40).ifBlank { existing.title }
            } else existing.title
            all[idx] = existing.copy(
                messages = updatedMessages,
                updatedAt = System.currentTimeMillis(),
                title = autoTitle
            )
            persist()
        }
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        lock.withLock {
            val all = ensureCache()
            val idx = all.indexOfFirst { it.id == conversationId }
            if (idx == -1) return
            all[idx] = all[idx].copy(title = newTitle)
            persist()
        }
    }

    fun togglePinned(conversationId: String) {
        lock.withLock {
            val all = ensureCache()
            val idx = all.indexOfFirst { it.id == conversationId }
            if (idx == -1) return
            all[idx] = all[idx].copy(pinned = !all[idx].pinned)
            persist()
        }
    }

    fun deleteConversation(conversationId: String) {
        lock.withLock {
            val all = ensureCache()
            all.removeAll { it.id == conversationId }
            persist()
        }
    }

    /** Must be called while already holding `lock`. */
    private fun persist() {
        val arr = JSONArray()
        cache?.forEach { arr.put(conversationToJson(it)) }
        writeAtomically(storeFile, arr.toString())
    }

    private fun writeAtomically(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            // Fallback for filesystems where atomic rename across the same
            // dir can still occasionally fail — copy+delete as last resort
            // rather than losing the write entirely.
            target.writeText(content)
            tmp.delete()
        }
    }

    // --- Codex thread_id persistence ---
    // Fixes: thread IDs were previously held only in a ChatViewModel-local
    // mutableMapOf<>() and lost whenever the process died, silently
    // breaking `codex exec resume` continuity even though the chat
    // history itself survived via conversations.json.

    fun saveThreadIdMap(map: Map<String, String>) = lock.withLock {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        writeAtomically(threadIdFile, obj.toString())
    }

    fun loadThreadIdMap(): Map<String, String> = lock.withLock {
        if (!threadIdFile.exists()) return emptyMap()
        return try {
            val text = threadIdFile.readText().ifBlank { return emptyMap() }
            val obj = JSONObject(text)
            val result = mutableMapOf<String, String>()
            obj.keys().forEach { key -> result[key] = obj.getString(key) }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun conversationToJson(c: Conversation): JSONObject = JSONObject().apply {
        put("id", c.id)
        put("title", c.title)
        put("createdAt", c.createdAt)
        put("updatedAt", c.updatedAt)
        put("pinned", c.pinned)
        val messagesArr = JSONArray()
        c.messages.forEach { messagesArr.put(messageToJson(it)) }
        put("messages", messagesArr)
    }

    private fun conversationFromJson(o: JSONObject): Conversation {
        val messagesArr = o.optJSONArray("messages") ?: JSONArray()
        val messages = (0 until messagesArr.length()).map { messageFromJson(messagesArr.getJSONObject(it)) }
        return Conversation(
            id = o.getString("id"),
            title = o.getString("title"),
            createdAt = o.getLong("createdAt"),
            updatedAt = o.getLong("updatedAt"),
            pinned = o.optBoolean("pinned", false),
            messages = messages
        )
    }

    private fun messageToJson(m: ChatMessage): JSONObject = JSONObject().apply {
        put("id", m.id)
        put("role", m.role.name)
        put("content", m.content)
        put("timestamp", m.timestamp)
        put("isCodeBlock", m.isCodeBlock)
        put("language", m.language)
        put("attachedFileName", m.attachedFileName)
        put("attachedFilePath", m.attachedFilePath)
        put("generatedImagePath", m.generatedImagePath)
        val sourcesArr = JSONArray()
        m.webSources.forEach { src ->
            sourcesArr.put(JSONObject().apply { put("url", src.url); put("domain", src.domain) })
        }
        put("webSources", sourcesArr)
    }

    private fun messageFromJson(o: JSONObject): ChatMessage {
        val sourcesArr = o.optJSONArray("webSources")
        val sources = if (sourcesArr != null) {
            (0 until sourcesArr.length()).map {
                val s = sourcesArr.getJSONObject(it)
                com.openaicodex.app.data.WebSource(s.getString("url"), s.getString("domain"))
            }
        } else emptyList()
        return ChatMessage(
            id = o.getString("id"),
            role = ChatMessage.Role.valueOf(o.getString("role")),
            content = o.getString("content"),
            timestamp = o.getLong("timestamp"),
            isCodeBlock = o.optBoolean("isCodeBlock", false),
            language = o.optString("language", "text"),
            attachedFileName = o.optString("attachedFileName", null).takeIf { o.has("attachedFileName") && !o.isNull("attachedFileName") },
            attachedFilePath = o.optString("attachedFilePath", null).takeIf { o.has("attachedFilePath") && !o.isNull("attachedFilePath") },
            generatedImagePath = o.optString("generatedImagePath", null).takeIf { o.has("generatedImagePath") && !o.isNull("generatedImagePath") },
            webSources = sources
        )
    }
}
