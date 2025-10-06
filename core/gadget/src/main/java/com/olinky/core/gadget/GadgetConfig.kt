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

    suspend fun applyMassStorageConfig(request: MassStorageConfigRequest): Result<Unit> {
        if (!rootShell.isRootAvailable()) {
            return Result.failure(IllegalStateException("Root access is required to manage USB gadgets"))
        }

        val script = request.buildSetupScript(configRoot)
        val result = rootShell.runScript(script)
        return if (result.exitCode == 0) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("ConfigFS apply failed: ${result.stderr.joinToString("; ")}"))
        }
    }

    suspend fun tearDownMassStorage(gadgetName: String): Result<Unit> {
        val script = buildString {
            appendLine("set -e")
            appendLine("CONFIGFS_ROOT=\"$configRoot\"")
            appendLine("GADGET=\"$configRoot/$gadgetName\"")
            appendLine("if [ ! -d \"$configRoot\" ]; then exit 0; fi")
            appendLine("if [ ! -d \"$configRoot/$gadgetName\" ]; then exit 0; fi")
        appendLine("if [ -f \"$configRoot/$gadgetName/UDC\" ]; then")
        appendLine("  CURRENT_UDC=\$(cat \"$configRoot/$gadgetName/UDC\")")
        appendLine("  if [ -n \"\$CURRENT_UDC\" ]; then printf '' > \"$configRoot/$gadgetName/UDC\"; fi")
            appendLine("fi")
            appendLine("find \"$configRoot/$gadgetName/configs\" -maxdepth 1 -type l -exec rm -f {} + || true")
            appendLine("rm -rf \"$configRoot/$gadgetName\"")
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
    val functionId = "mass_storage.$functionName"
    val autoBindFlag = if (autoBind) "true" else "false"
    val udcSelection = buildString {
        appendLine("UDC=\"${udcOverride.orEmpty()}\"")
        appendLine("if [ -z \"\$UDC\" ]; then")
        appendLine("  UDC=\$(ls /sys/class/udc | head -n 1)")
        appendLine("fi")
        appendLine("if [ -z \"\$UDC\" ]; then")
        appendLine("  echo 'UDC_NOT_FOUND' >&2")
        appendLine("  exit 4")
        appendLine("fi")
    }

    return buildString {
        appendLine("set -e")
        appendLine("CONFIGFS_ROOT=\"$configRoot\"")
        appendLine("GADGET=\"$configRoot/$gadgetName\"")
        appendLine("ensure_configfs() {")
        appendLine("  if [ ! -d \"$configRoot\" ]; then")
        appendLine("    mount -t configfs none \"$configRoot\"")
        appendLine("  fi")
        appendLine("}")
        appendLine("ensure_configfs")
        appendLine("mkdir -p \"$configRoot/$gadgetName\"")
        appendLine("echo ${vendorId.lowercase()} > \"$configRoot/$gadgetName/idVendor\"")
        appendLine("echo ${productId.lowercase()} > \"$configRoot/$gadgetName/idProduct\"")
        appendLine("mkdir -p \"$configRoot/$gadgetName/strings/0x409\"")
        appendLine("echo \"$serialNumber\" > \"$configRoot/$gadgetName/strings/0x409/serialnumber\"")
        appendLine("echo \"$manufacturer\" > \"$configRoot/$gadgetName/strings/0x409/manufacturer\"")
        appendLine("echo \"$product\" > \"$configRoot/$gadgetName/strings/0x409/product\"")
        appendLine("mkdir -p \"$configRoot/$gadgetName/configs/c.1/strings/0x409\"")
        appendLine("echo \"$configurationLabel\" > \"$configRoot/$gadgetName/configs/c.1/strings/0x409/configuration\"")
        appendLine("mkdir -p \"$configRoot/$gadgetName/functions/$functionId\"")
        appendLine("echo \"$escapedImage\" > \"$configRoot/$gadgetName/functions/$functionId/lun.0/file\"")
        appendLine("echo $roFlag > \"$configRoot/$gadgetName/functions/$functionId/lun.0/ro\"")
        appendLine("echo 1 > \"$configRoot/$gadgetName/functions/$functionId/lun.0/removable\"")
        appendLine("mkdir -p \"$configRoot/$gadgetName/configs/c.1\"")
        appendLine("ln -sf \"$configRoot/$gadgetName/functions/$functionId\" \"$configRoot/$gadgetName/configs/c.1/$functionId\"")
        append(udcSelection)
        appendLine("AUTO_BIND=\"$autoBindFlag\"")
        appendLine("if [ \"\$AUTO_BIND\" = \"true\" ]; then")
        appendLine("  echo \$UDC > \"$configRoot/$gadgetName/UDC\"")
        appendLine("fi")
    }
}
