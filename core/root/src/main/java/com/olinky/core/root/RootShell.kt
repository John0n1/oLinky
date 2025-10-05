package com.olinky.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Provides a minimal abstraction for executing privileged shell commands.
 * Implementation to be expanded with persistent `su` sessions and structured responses.
 */
object RootShell {
    suspend fun runCommand(vararg command: String): ShellResult = withContext(Dispatchers.IO) {
        ShellResult(
            exitCode = -1,
            stdout = emptyList(),
            stderr = listOf("Root execution not yet implemented")
        )
    }
}

data class ShellResult(
    val exitCode: Int,
    val stdout: List<String>,
    val stderr: List<String>
)
