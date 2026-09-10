package com.codex.mobile.engine

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

        val env = builder.environment()
        env["CODEX_MANAGED_BY_NPM"] = "1"
        env["CODEX_SELF_EXE"] = binary
        env["CODEX_HOME"] = codexHomeDir().absolutePath
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
    fun buildTurnArgs(prompt: String, resumeThreadId: String?): List<String> {
        return if (resumeThreadId != null) {
            listOf("exec", "resume", resumeThreadId, "--json", "--skip-git-repo-check", prompt)
        } else {
            listOf("exec", "--json", "--skip-git-repo-check", prompt)
        }
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
     * Android's `java.lang.Process` does not expose the desktop JDK
     * `ProcessHandle`/`toHandle()` API. We therefore terminate the direct
     * child using the Android-supported Process APIs; the native runtime is
     * responsible for cleaning up its own descendants.
     */
    fun destroyWithChildren() {
        running.set(false)
        val proc = process ?: return
        try {
            if (proc.isAlive) {
                proc.destroy()
            }
        } catch (_: Exception) {
            // The direct forceful termination below is the fallback.
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
