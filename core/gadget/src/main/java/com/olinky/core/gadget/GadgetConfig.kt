package com.olinky.core.gadget

import com.olinky.core.root.RootShell

private const val DEFAULT_CONFIGFS_ROOT = "/config/usb_gadget"
private const val DEFAULT_MANUFACTURER = "oLinky"
private const val DEFAULT_PRODUCT = "oLinky USB Drive"

/**
 * Provides utilities for configuring ConfigFS-based USB gadgets backed by disk images.
 */
class GadgetConfig(
    private val rootShell: RootShell = RootShell,
    private val configRoot: String = DEFAULT_CONFIGFS_ROOT
) {

    /**
     * Check if a gadget with the given name exists and is currently bound.
     */
    suspend fun isGadgetBound(gadgetName: String): Result<Boolean> {
        val script = """
            if [ -f "$configRoot/$gadgetName/UDC" ]; then
                UDC_CONTENT=$(cat "$configRoot/$gadgetName/UDC" 2>/dev/null || echo "")
                if [ -n "${'$'}UDC_CONTENT" ]; then
                    echo "1"
                else
                    echo "0"
                fi
            else
                echo "0"
            fi
        """.trimIndent()
        val result = rootShell.runScript(script)
        return if (result.exitCode == 0) {
            Result.success(result.stdout.firstOrNull()?.trim() == "1")
        } else {
            Result.failure(IllegalStateException("Failed to check gadget status"))
        }
    }

    /**
     * Get the currently mounted image path from a gadget, if any.
     */
    suspend fun getMountedImagePath(gadgetName: String, functionName: String = "usb0"): Result<String?> {
        val script = """
            FILE_PATH="$configRoot/$gadgetName/functions/mass_storage.$functionName/lun.0/file"
            if [ -f "${'$'}FILE_PATH" ]; then
                cat "${'$'}FILE_PATH" 2>/dev/null || echo ""
            else
                echo ""
            fi
        """.trimIndent()
        val result = rootShell.runScript(script)
        return if (result.exitCode == 0) {
            val path = result.stdout.firstOrNull()?.trim()
            Result.success(if (path.isNullOrBlank()) null else path)
        } else {
            Result.failure(IllegalStateException("Failed to read mounted image path"))
        }
    }

    suspend fun applyMassStorageConfig(request: MassStorageConfigRequest): Result<Unit> {
        if (!rootShell.isRootAvailable()) {
            return Result.failure(IllegalStateException("Root access is required to manage USB gadgets"))
        }

        // Check if gadget already exists and tear it down first
        val existingGadget = rootShell.fileExists("$configRoot/${request.gadgetName}")
        if (existingGadget) {
            val tearDownResult = tearDownMassStorage(request.gadgetName)
            if (tearDownResult.isFailure) {
                return Result.failure(IllegalStateException("Failed to clean up existing gadget: ${tearDownResult.exceptionOrNull()?.message}"))
            }
        }

        val script = request.buildSetupScript(configRoot)
        val result = rootShell.runScript(script)
        
        if (result.exitCode != 0) {
            val stderrMsg = result.stderr.joinToString("\n")
            val stdoutMsg = result.stdout.joinToString("\n")
            val combinedOutput = buildString {
                append("Exit code: ${result.exitCode}")
                if (stderrMsg.isNotBlank()) {
                    append("\nError output: $stderrMsg")
                }
                if (stdoutMsg.isNotBlank()) {
                    append("\nStandard output: $stdoutMsg")
                }
            }
            return Result.failure(IllegalStateException("ConfigFS apply failed: $combinedOutput"))
        }
        
        // Verify gadget was actually bound by checking UDC file
        if (request.autoBind) {
            val verifyScript = """
                if [ -f "$configRoot/${request.gadgetName}/UDC" ]; then
                    cat "$configRoot/${request.gadgetName}/UDC"
                else
                    echo "UDC_FILE_MISSING" >&2
                    exit 1
                fi
            """.trimIndent()
            val verifyResult = rootShell.runScript(verifyScript)
            if (verifyResult.exitCode != 0 || verifyResult.stdout.firstOrNull().isNullOrBlank()) {
                return Result.failure(IllegalStateException("Gadget binding verification failed: UDC not bound"))
            }
        }
        
        return Result.success(Unit)
    }

    suspend fun tearDownMassStorage(gadgetName: String): Result<Unit> {
        val script = buildString {
            appendLine("set -e")
            appendLine("CONFIGFS_ROOT=\"$configRoot\"")
            appendLine("GADGET=\"$configRoot/$gadgetName\"")
            appendLine("# Exit successfully if configfs or gadget doesn't exist")
            appendLine("if [ ! -d \"$configRoot\" ]; then exit 0; fi")
            appendLine("if [ ! -d \"$configRoot/$gadgetName\" ]; then exit 0; fi")
            appendLine("# Unbind UDC if bound")
            appendLine("if [ -f \"$configRoot/$gadgetName/UDC\" ]; then")
            appendLine("  CURRENT_UDC=\$(cat \"$configRoot/$gadgetName/UDC\" 2>/dev/null || echo \"\")")
            appendLine("  if [ -n \"\$CURRENT_UDC\" ]; then")
            appendLine("    printf '' > \"$configRoot/$gadgetName/UDC\" 2>/dev/null || true")
            appendLine("    sleep 0.2")
            appendLine("  fi")
            appendLine("fi")
            appendLine("# Remove function symlinks")
            appendLine("if [ -d \"$configRoot/$gadgetName/configs/c.1\" ]; then")
            appendLine("  find \"$configRoot/$gadgetName/configs/c.1\" -maxdepth 1 -type l -exec rm -f {} + 2>/dev/null || true")
            appendLine("fi")
            appendLine("# Remove function directories")
            appendLine("if [ -d \"$configRoot/$gadgetName/functions\" ]; then")
            appendLine("  rm -rf \"$configRoot/$gadgetName/functions\"/* 2>/dev/null || true")
            appendLine("fi")
            appendLine("# Remove configs")
            appendLine("if [ -d \"$configRoot/$gadgetName/configs\" ]; then")
            appendLine("  rm -rf \"$configRoot/$gadgetName/configs\"/* 2>/dev/null || true")
            appendLine("fi")
            appendLine("# Remove strings")
            appendLine("if [ -d \"$configRoot/$gadgetName/strings\" ]; then")
            appendLine("  rm -rf \"$configRoot/$gadgetName/strings\"/* 2>/dev/null || true")
            appendLine("fi")
            appendLine("# Remove gadget directory")
            appendLine("rmdir \"$configRoot/$gadgetName\" 2>/dev/null || true")
            appendLine("# Verify removal")
            appendLine("if [ -d \"$configRoot/$gadgetName\" ]; then")
            appendLine("  echo 'WARNING: Gadget directory still exists after cleanup' >&2")
            appendLine("fi")
        }

        val result = rootShell.runScript(script)
        return if (result.exitCode == 0) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Failed to tear down gadget: ${result.stderr.joinToString("; ")}"))
        }
    }
}

data class MassStorageConfigRequest(
    val gadgetName: String,
    val imagePath: String,
    val vendorId: String = "0x1d6b",
    val productId: String = "0x0104",
    val manufacturer: String = DEFAULT_MANUFACTURER,
    val product: String = DEFAULT_PRODUCT,
    val serialNumber: String = "OLINKY-0001",
    val configurationLabel: String = "Mass Storage",
    val functionName: String = "usb0",
    val readOnly: Boolean = true,
    val autoBind: Boolean = true,
    val udcOverride: String? = null
)

private fun MassStorageConfigRequest.buildSetupScript(configRoot: String): String {
    val escapedImage = imagePath.replace("\"", "\\\"")
    val roFlag = if (readOnly) "1" else "0"
    val autoBindFlag = if (autoBind) "true" else "false"
    val udcSelection = buildString {
        appendLine("UDC=\"${udcOverride.orEmpty()}\"")
        appendLine("if [ -z \"\$UDC\" ]; then")
        appendLine("  if [ -d /sys/class/udc ]; then")
        appendLine("    UDC=\$(ls /sys/class/udc 2>/dev/null | head -n 1)")
        appendLine("  fi")
        appendLine("fi")
        appendLine("if [ -z \"\$UDC\" ]; then")
        appendLine("  echo 'ERROR: No USB Device Controller (UDC) found in /sys/class/udc' >&2")
        appendLine("  exit 4")
        appendLine("fi")
        appendLine("echo \"Using UDC: \$UDC\"")
    }

    return buildString {
        appendLine("set -e")
        appendLine("CONFIGFS_ROOT=\"$configRoot\"")
        appendLine("GADGET=\"$configRoot/$gadgetName\"")
        appendLine("IMAGE_FILE=\"$escapedImage\"")
        appendLine("")
        appendLine("# Verify image file exists and is readable")
        appendLine("if [ ! -f \"\$IMAGE_FILE\" ]; then")
        appendLine("  echo \"ERROR: Image file not found: \$IMAGE_FILE\" >&2")
        appendLine("  exit 2")
        appendLine("fi")
        appendLine("if [ ! -r \"\$IMAGE_FILE\" ]; then")
        appendLine("  echo \"ERROR: Image file not readable: \$IMAGE_FILE\" >&2")
        appendLine("  exit 3")
        appendLine("fi")
        appendLine("")
        appendLine("# Ensure configfs is mounted")
        appendLine("ensure_configfs() {")
        appendLine("  if [ ! -d \"$configRoot\" ]; then")
        appendLine("    mkdir -p \"$configRoot\" 2>/dev/null || true")
        appendLine("    mount -t configfs none \"$configRoot\" 2>/dev/null || true")
        appendLine("  fi")
        appendLine("  if [ ! -d \"$configRoot\" ]; then")
        appendLine("    echo \"ERROR: ConfigFS not available at $configRoot\" >&2")
        appendLine("    exit 1")
        appendLine("  fi")
        appendLine("}")
        appendLine("ensure_configfs")
        appendLine("setprop sys.usb.configfs 1 2>/dev/null || true")
    appendLine("for EXISTING in \"$configRoot\"/*; do")
    appendLine("  if [ -d \"${'$'}EXISTING\" ] && [ \"${'$'}EXISTING\" != \"$configRoot/$gadgetName\" ]; then")
    appendLine("    UDC_FILE=\"${'$'}EXISTING/UDC\"")
    appendLine("    if [ -f \"${'$'}UDC_FILE\" ]; then")
    appendLine("      CURRENT=$(cat \"${'$'}UDC_FILE\" 2>/dev/null || echo \"\")")
    appendLine("      if [ -n \"${'$'}CURRENT\" ]; then")
    appendLine("        printf '' > \"${'$'}UDC_FILE\" 2>/dev/null || true")
    appendLine("        sleep 0.2")
    appendLine("      fi")
    appendLine("    fi")
    appendLine("  fi")
    appendLine("done")
        appendLine("")
        appendLine("# Create gadget directory")
        appendLine("mkdir -p \"\$GADGET\"")
        appendLine("")
        appendLine("# Set USB descriptors")
        appendLine("echo ${vendorId.lowercase()} > \"\$GADGET/idVendor\"")
        appendLine("echo ${productId.lowercase()} > \"\$GADGET/idProduct\"")
        appendLine("")
        appendLine("# Create and set strings")
        appendLine("mkdir -p \"\$GADGET/strings/0x409\"")
        appendLine("echo \"$serialNumber\" > \"\$GADGET/strings/0x409/serialnumber\"")
        appendLine("echo \"$manufacturer\" > \"\$GADGET/strings/0x409/manufacturer\"")
        appendLine("echo \"$product\" > \"\$GADGET/strings/0x409/product\"")
        appendLine("")
        appendLine("# Create configuration")
        appendLine("mkdir -p \"\$GADGET/configs/c.1/strings/0x409\"")
        appendLine("echo \"$configurationLabel\" > \"\$GADGET/configs/c.1/strings/0x409/configuration\"")
        appendLine("")
        appendLine("# Create mass storage function (with device-specific fallbacks)")
        appendLine("FUNCTION_ID=\"\"")
        appendLine("for CANDIDATE in \"mass_storage.$functionName\" \"mass_storage.usb0\" \"mass_storage.0\"; do")
        appendLine("  if [ -n \"\$FUNCTION_ID\" ]; then")
        appendLine("    break")
        appendLine("  fi")
        appendLine("  if mkdir -p \"\$GADGET/functions/\$CANDIDATE\" 2>/dev/null; then")
        appendLine("    FUNCTION_ID=\"\$CANDIDATE\"")
        appendLine("  fi")
        appendLine("done")
        appendLine("if [ -z \"\$FUNCTION_ID\" ]; then")
        appendLine("  echo \"ERROR: Unable to create mass storage function\" >&2")
        appendLine("  exit 5")
        appendLine("fi")
        appendLine("FUNCTION_DIR=\"\$GADGET/functions/\$FUNCTION_ID\"")
        appendLine("mkdir -p \"\$FUNCTION_DIR/lun.0\"")
        appendLine("echo \"\$IMAGE_FILE\" > \"\$FUNCTION_DIR/lun.0/file\"")
        appendLine("echo $roFlag > \"\$FUNCTION_DIR/lun.0/ro\"")
        appendLine("echo 1 > \"\$FUNCTION_DIR/lun.0/removable\"")
        appendLine("")
        appendLine("# Link function to configuration")
        appendLine("mkdir -p \"\$GADGET/configs/c.1\"")
        appendLine("ln -sf \"\$FUNCTION_DIR\" \"\$GADGET/configs/c.1/\$FUNCTION_ID\"")
        appendLine("")
        append(udcSelection)
        appendLine("AUTO_BIND=\"$autoBindFlag\"")
        appendLine("if [ \"\$AUTO_BIND\" = \"true\" ]; then")
        appendLine("  echo \"\$UDC\" > \"\$GADGET/UDC\"")
        appendLine("  echo \"Gadget bound to UDC: \$UDC\"")
        appendLine("fi")
    }
}
