package com.olinky.app.module

import android.content.Context
import com.olinky.core.root.RootShell
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles checking for and installing the required Magisk module for SELinux permissions.
 */
@Singleton
class MagiskModuleInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val rootShell = RootShell

    companion object {
        private const val MODULE_ID = "olinky-selinux"
        private const val MODULE_ASSET_NAME = "olinky-selinux-helper.zip"
        private const val CANARY_DIR = "/config/usb_gadget/olinky_canary"
        private val MAGISK_BINARIES = listOf(
            "magisk",
            "/sbin/magisk",
            "/system/bin/magisk",
            "/system/xbin/magisk",
            "/data/adb/magisk/magisk"
        )
    }

    private suspend fun findMagiskBinary(): String? {
        MAGISK_BINARIES.forEach { candidate ->
            val result = rootShell.runScript("if command -v $candidate >/dev/null 2>&1; then echo $candidate; fi")
            if (result.exitCode == 0) {
                val path = result.stdout.firstOrNull()?.trim()
                if (!path.isNullOrEmpty()) {
                    return path
                }
            }
        }
        return null
    }

    /**
     * Checks if the required SELinux permissions are available.
     * @return `true` if permissions are sufficient, `false` otherwise.
     */
    private suspend fun hasPermissions(): Boolean {
        val script = """
            mkdir -p $CANARY_DIR 2>/dev/null && rmdir $CANARY_DIR 2>/dev/null
        """.trimIndent()
        val result = rootShell.runScript(script)
        // If the command succeeds (exit code 0), we have permissions.
        return result.exitCode == 0
    }

    /**
     * Checks if the oLinky helper module is already installed and enabled in Magisk.
     */
    private suspend fun isModuleInstalled(): Boolean {
        val magiskBinary = findMagiskBinary() ?: return false
        val result = rootShell.runScript("$magiskBinary --list-modules")
        return result.stdout.any { it.contains(MODULE_ID) }
    }

    /**
     * Checks for necessary permissions and installs the Magisk module if needed.
     *
     * @return [ModuleStatus] indicating the current state.
     */
    suspend fun checkAndInstallIfNeeded(): ModuleStatus {
        if (!rootShell.isRootAvailable()) {
            return ModuleStatus.ROOT_UNAVAILABLE
        }

        if (hasPermissions()) {
            return ModuleStatus.OK
        }

        // Permissions are missing, check if the module is installed but maybe needs a reboot
        if (isModuleInstalled()) {
            return ModuleStatus.NEEDS_REBOOT
        }

        // Module not installed and permissions missing, so let's install it.
        return when (installModule()) {
            true -> ModuleStatus.NEEDS_REBOOT
            false -> ModuleStatus.INSTALL_FAILED
        }
    }

    /**
     * Copies the module from assets and installs it via Magisk.
     */
    private suspend fun installModule(): Boolean {
        val moduleFile = withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, MODULE_ASSET_NAME)
            if (file.exists()) file.delete()

            try {
                context.assets.open(MODULE_ASSET_NAME).use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                file.setReadable(true, false)
                file
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } ?: return false

        val magiskBinary = findMagiskBinary()
        val result = if (magiskBinary != null) {
            rootShell.runScript("$magiskBinary --install-module ${moduleFile.absolutePath}")
        } else {
            rootShell.runScript(
                "mkdir -p /data/adb/modules_update && cp ${moduleFile.absolutePath} /data/adb/modules_update/${MODULE_ID}.zip && chmod 644 /data/adb/modules_update/${MODULE_ID}.zip"
            )
        }
        withContext(Dispatchers.IO) {
            moduleFile.delete()
        }
        return result.exitCode == 0
    }
}

enum class ModuleStatus {
    /** Permissions are sufficient, no action needed. */
    OK,
    /** The helper module was just installed or was already installed, and a reboot is required. */
    NEEDS_REBOOT,
    /** Failed to install the helper module. */
    INSTALL_FAILED,
    /** Root access is not available. */
    ROOT_UNAVAILABLE
}
