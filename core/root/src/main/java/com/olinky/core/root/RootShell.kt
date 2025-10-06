package com.olinky.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Lightweight root execution helper that wraps `su -c` calls and captures stdout/stderr output.
 * Designed to be expanded later with persistent sessions and richer error propagation.
 */
object RootShell {

    private const val SU_BINARY = "su"
    private const val DEFAULT_TIMEOUT = 15_000L

    suspend fun isRootAvailable(timeoutMillis: Long = DEFAULT_TIMEOUT): Boolean {
        val result = runScript("id -u", timeoutMillis)
        return result.exitCode == 0 && result.stdout.firstOrNull()?.trim() == "0"
    }

    suspend fun runCommand(vararg command: String, timeoutMillis: Long = DEFAULT_TIMEOUT): ShellResult {
        require(command.isNotEmpty()) { "Command must not be empty" }
        val commandString = command.joinToString(" ") { it.escapeForShell() }
        return runScript(commandString, timeoutMillis)
    }

    suspend fun runScript(script: String, timeoutMillis: Long = DEFAULT_TIMEOUT): ShellResult =
        withContext(Dispatchers.IO) {
            try {
                withTimeout(timeoutMillis) {
                    coroutineScope {
                        val process = ProcessBuilder(SU_BINARY, "-c", script)
                            .redirectErrorStream(false)
                            .start()

                        val stdoutDeferred = async {
                            process.inputStream.bufferedReader().useLines { it.toList() }
                        }
                        val stderrDeferred = async {
                            process.errorStream.bufferedReader().useLines { it.toList() }
                        }

                        val exitCode = process.waitFor()
                        val result = ShellResult(exitCode, stdoutDeferred.await(), stderrDeferred.await())
                        process.destroy()
                        result
                    }
                }
            } catch (throwable: Throwable) {
                ShellResult(
                    exitCode = -1,
                    stdout = emptyList(),
                    stderr = listOf(throwable.message ?: throwable::class.java.simpleName)
                )
            }
        }

    private fun String.escapeForShell(): String {
        if (isEmpty()) return "''"
        return if (all { it.isLetterOrDigit() || it in "@%_+=:,./-" }) {
            this
        } else {
            "'" + replace("'", "'\\''") + "'"
        }
    }
}

data class ShellResult(
    val exitCode: Int,
    val stdout: List<String>,
    val stderr: List<String>
)
