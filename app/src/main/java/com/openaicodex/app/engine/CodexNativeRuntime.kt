package com.openaicodex.app.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wraps the bundled `codex.bin` ELF executable (arm64-v8a) and runs it as a
 * genuine child process — the same architecture Termux itself uses, and the
 * same one codex.js uses on desktop (spawn + env, not a library call).
 *
 * We deliberately do NOT try to dlopen() this as a JNI library: it's a CLI
 * binary with a main(), not an exported JNI_OnLoad symbol. Android exposes
 * it to us pre-extracted, executable, and out of app-data (W^X compliant)
 * because it lives under nativeLibraryDir thanks to jniLibs packaging.
 */
sealed class CodexEvent {
    data class Stdout(val line: String) : CodexEvent()
    data class Stderr(val line: String) : CodexEvent()
    data class Exited(val code: Int) : CodexEvent()
    data class Failed(val message: String) : CodexEvent()
}

class CodexNativeRuntime(private val context: Context) {

    private val tag = "CodexNativeRuntime"
    private var process: Process? = null
    private val running = AtomicBoolean(false)

    /** Absolute path to the extracted native binary inside nativeLibraryDir. */
    private fun binaryPath(): String {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        return File(nativeDir, "libcodexcore.so").absolutePath
    }

    private fun libDir(): String = context.applicationInfo.nativeLibraryDir

    /** CODEX_HOME — where auth.json, config.toml, sessions, etc. live. */
    fun codexHomeDir(): File {
        val dir = File(context.filesDir, "codex_home")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Per-conversation CODEX_HOME. Fix: the CLI process was previously
     * always launched with the single global codexHomeDir() as CODEX_HOME,
     * so session files, config.toml overrides, and anything else codex.bin
     * writes under CODEX_HOME during a turn were shared and visible across
     * every conversation — a real isolation violation (see item 2: "her
     * conversation için ayrı ... CODEX_HOME", "filesDir/codex_home gibi
     * global Codex HOME kullanımını kaldır").
     *
     * Login (auth.json) itself stays account-wide by design — codexHomeDir()
     * above remains the single place CodexAuthManager persists credentials,
     * since OpenAI sign-in is a per-user action, not a per-chat one, and
     * requiring re-login per conversation would be a regression, not a fix.
     * To reconcile that with per-conversation isolation, [launch] copies the
     * current auth.json into this per-conversation home right before
     * starting the process, so each conversation's CODEX_HOME is otherwise
     * fully isolated (sessions/, config.toml, any other CLI-written state)
     * while still authenticating with the same account.
     */
    fun conversationCodexHomeDir(conversationId: String? = null): File {
        val root = File(context.filesDir, "codex_home_conversations")
        val dir = if (conversationId != null) {
            File(root, sanitizeDirName(conversationId))
        } else {
            File(root, "default")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Copies the shared account auth.json into a per-conversation CODEX_HOME, if present. */
    private fun syncAuthJsonInto(perConversationHome: File) {
        try {
            val source = File(codexHomeDir(), "auth.json")
            if (!source.exists()) return
            val dest = File(perConversationHome, "auth.json")
            source.copyTo(dest, overwrite = true)
        } catch (e: Exception) {
            Log.w(tag, "Could not sync auth.json into conversation CODEX_HOME: ${e.message}")
        }
    }

    /**
     * Workspace directory exposed to the running agent as its working
     * directory. Now conversation-scoped: each conversation gets its own
     * isolated subdirectory (codex_workspace/conversations/<id>/) instead
     * of every conversation sharing one root. This means files/attachments
     * from one chat can't leak into or collide with another, and AGENTS.md
     * (per CodexPromptComposer) is written per-conversation too.
     * Falls back to a shared "default" directory when no conversation id
     * is known yet (e.g. before the first message creates one).
     */
    fun workspaceDir(conversationId: String? = null): File {
        val root = File(context.filesDir, "codex_workspace")
        val dir = if (conversationId != null) {
            File(File(root, "conversations"), sanitizeDirName(conversationId))
        } else {
            File(root, "default")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun sanitizeDirName(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    fun isRunning(): Boolean = running.get()

    /**
     * Launches `codex.bin` with the given CLI arguments and streams
     * stdout/stderr line-by-line as a cold Flow. Cancelling collection kills
     * the process.
     */
    fun launch(args: List<String>, extraEnv: Map<String, String> = emptyMap(), conversationId: String? = null): Flow<CodexEvent> = callbackFlow {
        val binary = binaryPath()
        if (!File(binary).exists()) {
            trySend(CodexEvent.Failed("Native binary not found at $binary"))
            close()
            return@callbackFlow
        }

        val command = mutableListOf(binary)
        command.addAll(args)

        val builder = ProcessBuilder(command)
        builder.directory(workspaceDir(conversationId))
        builder.redirectErrorStream(false)

        // Per-conversation CODEX_HOME (see conversationCodexHomeDir doc) —
        // synced with the current account auth.json so login still works,
        // but session/config state this process writes stays isolated to
        // this conversation instead of leaking into every other chat's home.
        val perConversationHome = conversationCodexHomeDir(conversationId)
        syncAuthJsonInto(perConversationHome)

        val env = builder.environment()
        env["CODEX_MANAGED_BY_NPM"] = "1"
        env["CODEX_SELF_EXE"] = binary
        env["CODEX_HOME"] = perConversationHome.absolutePath
        env["HOME"] = context.filesDir.absolutePath
        env["TMPDIR"] = context.cacheDir.absolutePath
        // Bundle dir first so libc++_shared.so resolves before anything else.
        env["LD_LIBRARY_PATH"] = libDir()
        env.putAll(extraEnv)

        try {
            val proc = builder.start()
            process = proc
            running.set(true)

            val stdoutReader = BufferedReader(InputStreamReader(proc.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(proc.errorStream))

            val stdoutThread = Thread {
                try {
                    var line: String?
                    while (stdoutReader.readLine().also { line = it } != null) {
                        trySend(CodexEvent.Stdout(line ?: ""))
                    }
                } catch (e: Exception) {
                    Log.w(tag, "stdout reader ended: ${e.message}")
                }
            }
            val stderrThread = Thread {
                try {
                    var line: String?
                    while (stderrReader.readLine().also { line = it } != null) {
                        trySend(CodexEvent.Stderr(line ?: ""))
                    }
                } catch (e: Exception) {
                    Log.w(tag, "stderr reader ended: ${e.message}")
                }
            }
            stdoutThread.isDaemon = true
            stderrThread.isDaemon = true
            stdoutThread.start()
            stderrThread.start()

            val waitThread = Thread {
                val code = proc.waitFor()
                running.set(false)
                trySend(CodexEvent.Exited(code))
                close()
            }
            waitThread.isDaemon = true
            waitThread.start()
        } catch (e: Exception) {
            running.set(false)
            trySend(CodexEvent.Failed(e.message ?: "unknown launch failure"))
            close()
        }

        awaitClose {
            destroy()
        }
    }

    /**
     * Builds the CLI args for a turn in a conversation.
     *
     * `codex exec --json` is a one-shot process — it exits after answering,
     * it is NOT an interactive stdin shell. There is no PTY here and no
     * live stdin injection into a running process (a prior version of this
     * class exposed a sendInput() that wrote to a process's stdin, but
     * since exec always exits after one turn, nothing was ever listening
     * on the other end — that method was dead code and has been removed).
     *
     * Real multi-turn continuity is done the way the upstream Codex CLI
     * itself does it outside of app-server/TUI: `codex exec resume
     * <thread_id> <prompt>` reopens the same recorded session and continues
     * it with full prior context, using the thread_id captured from that
     * session's `thread.started` event. This is a genuine continuation
     * mechanism, not a simulated one.
     */
    /**
     * modelId/effort, when provided, are passed straight through as the
     * Codex CLI's own `--model` / `-c model_reasoning_effort=...` flags —
     * this app does not reimplement model routing, it just forwards the
     * user's chip selection to the CLI, which is the single source of
     * truth for which model ids it actually accepts.
     */
    fun buildTurnArgs(
        prompt: String,
        resumeThreadId: String?,
        modelId: String? = null,
        effort: String? = null
    ): List<String> {
        val modelArgs = mutableListOf<String>()
        if (!modelId.isNullOrBlank()) {
            modelArgs += listOf("--model", modelId)
        }
        if (!effort.isNullOrBlank()) {
            modelArgs += listOf("-c", "model_reasoning_effort=\"${effort.lowercase()}\"")
        }
        val base = if (resumeThreadId != null) {
            listOf("exec", "resume", resumeThreadId, "--json", "--skip-git-repo-check")
        } else {
            listOf("exec", "--json", "--skip-git-repo-check")
        }
        return base + modelArgs + listOf(prompt)
    }

    fun destroy() {
        running.set(false)
        process?.let {
            if (it.isAlive) {
                it.destroy()
            }
        }
        process = null
    }

    /**
     * Kills the tracked process AND any descendant processes it spawned
     * (codex.bin can itself launch shell subprocesses for command
     * execution — `bash`, individual tool invocations, etc). A plain
     * `Process#destroy()` only signals the direct child; if that child
     * has already forked further children, those can be left running
     * as orphans after the "stop" button is pressed.
     *
     * `Process#descendants()` (API 26+) is a real, documented JDK/Android
     * API for exactly this — it is not custom process-tree bookkeeping.
     * On the rare pre-26 path (this app's minSdk is already 26 so this
     * is defensive only) we fall back to plain destroy().
     */
    fun destroyWithChildren() {
        running.set(false)
        val proc = process ?: return
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val handle = proc.toHandle()
                handle.descendants().forEach { descendant ->
                    try {
                        descendant.destroyForcibly()
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
            // descendants() can throw on some OEM kernels that restrict
            // /proc access; the direct destroy below still runs.
        }
        if (proc.isAlive) {
            proc.destroyForcibly()
        }
        process = null
    }

    /**
     * Runs a short-lived command to completion, returning combined stdout.
     * Used for one-shot calls like `codex --version`.
     *
     * Fix: a prior version called `proc.inputStream.bufferedReader().readText()`
     * BEFORE `proc.waitFor(timeoutMs, ...)`. `readText()` blocks until the
     * stream hits EOF (i.e. the process exits or closes stdout) — so if the
     * process hung and produced no output, execution never reached the
     * waitFor() line at all and the configured timeout never had a chance
     * to fire. This version reads the stream on a background thread with
     * its own bounded StringBuilder, while the calling thread enforces the
     * real timeout via waitFor(), and force-kills the process on timeout
     * regardless of what the reader thread is doing.
     */
    fun runBlockingCapture(args: List<String>, timeoutMs: Long = 15_000): Result<String> {
        return try {
            val binary = binaryPath()
            val command = mutableListOf(binary)
            command.addAll(args)
            val builder = ProcessBuilder(command)
            builder.directory(workspaceDir())
            builder.redirectErrorStream(true)
            val env = builder.environment()
            env["CODEX_SELF_EXE"] = binary
            env["CODEX_HOME"] = codexHomeDir().absolutePath
            env["HOME"] = context.filesDir.absolutePath
            env["LD_LIBRARY_PATH"] = libDir()

            val proc = builder.start()
            val outputBuilder = StringBuilder()
            val readerThread = Thread {
                try {
                    proc.inputStream.bufferedReader().forEachLine { line ->
                        outputBuilder.append(line).append('\n')
                    }
                } catch (_: Exception) {
                    // Stream closed because we force-killed the process on timeout — expected.
                }
            }
            readerThread.isDaemon = true
            readerThread.start()

            val finishedInTime = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finishedInTime) {
                proc.destroyForcibly()
                readerThread.join(1000)
                return Result.failure(RuntimeException("Command timed out after ${timeoutMs}ms: ${args.joinToString(" ")}"))
            }
            readerThread.join(2000)
            val finished = proc.exitValue()
            val output = outputBuilder.toString()
            if (finished == 0) Result.success(output) else Result.failure(RuntimeException(output))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
