package com.olinky.feature.pxe

import com.olinky.core.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Controller for PXE boot services over USB Ethernet gadget (RNDIS/ECM).
 * Sets up USB Ethernet gadget and configures network interface for PXE/TFTP services.
 */
class PxeController(private val rootShell: RootShell = RootShell) {

    private val gadgetName = "olinky_pxe"
    private val interfaceName = "usb0"
    private val deviceIpCidr = "192.168.42.1/24"

    suspend fun startPxe(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            // Check if gadget already exists
            val existingGadget = rootShell.fileExists("/config/usb_gadget/$gadgetName")
            if (existingGadget) {
                stopPxe() // Clean up first
            }

            val script = buildSetupScript(gadgetName, interfaceName)
            val result = rootShell.runScript(script)

            if (result.exitCode != 0) {
                val errorOutput = result.stderr.joinToString("\n").ifBlank { result.stdout.joinToString("\n") }
                return@withContext Result.failure(
                    IllegalStateException("Failed to create USB Ethernet gadget: Gadget setup failed: $errorOutput")
                )
            }

            configureNetwork().onFailure { return@withContext Result.failure(it) }

            Result.success(Unit)
        }
    }

    suspend fun stopPxe(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val script = buildTearDownScript(gadgetName)
            val result = rootShell.runScript(script)
            if (result.exitCode == 0) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException("Failed to tear down PXE gadget: ${result.stderr.joinToString("\n")}")
                )
            }
        }
    }

    /**
     * Placeholder for starting DHCP/TFTP servers.
     */
    suspend fun startServers(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            // TODO: Implement actual DHCP and TFTP server logic
            // For now, we just simulate a success.
            Result.success(Unit)
        }
    }

    /**
     * Placeholder for stopping DHCP/TFTP servers.
     */
    suspend fun stopServers(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            // TODO: Implement actual DHCP and TFTP server logic
            Result.success(Unit)
        }
    }

    suspend fun configureNetwork(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val script = """
                ip link set $interfaceName down 2>/dev/null || true
                ip addr flush dev $interfaceName 2>/dev/null || true
                ip addr add $deviceIpCidr dev $interfaceName
                ip link set $interfaceName up
            """.trimIndent()

            val result = rootShell.runScript(script)
            if (result.exitCode == 0) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException("Failed to configure network interface: ${result.stderr.joinToString("\n").ifBlank { result.stdout.joinToString("\n") }}")
                )
            }
        }
    }

    suspend fun teardownNetwork(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val script = """
                ip addr flush dev $interfaceName 2>/dev/null || true
                ip link set $interfaceName down 2>/dev/null || true
            """.trimIndent()

            val result = rootShell.runScript(script)
            if (result.exitCode == 0) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException("Failed to tear down network interface: ${result.stderr.joinToString("\n").ifBlank { result.stdout.joinToString("\n") }}")
                )
            }
        }
    }

    suspend fun isPxeRunning(): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            val script = """
                if [ -f "/config/usb_gadget/$gadgetName/UDC" ]; then
                    CURRENT=${'$'}(cat "/config/usb_gadget/$gadgetName/UDC" 2>/dev/null)
                    if [ -n "${'$'}CURRENT" ]; then
                        echo 1
                    else
                        echo 0
                    fi
                else
                    echo 0
                fi
            """.trimIndent()
            val result = rootShell.runScript(script)
            if (result.exitCode == 0) {
                Result.success(result.stdout.firstOrNull()?.trim() == "1")
            } else {
                Result.failure(IllegalStateException("Failed to query PXE gadget status: ${result.stderr.joinToString("; ")}"))
            }
        }
    }

    private fun buildSetupScript(gadgetName: String, interfaceName: String): String {
        return """
            set -e
            
            GADGET_NAME="$gadgetName"
            INTERFACE_NAME="$interfaceName"
            CONFIGFS_ROOT="/config/usb_gadget"
            GADGET_DIR="${'$'}{CONFIGFS_ROOT}/${'$'}{GADGET_NAME}"

            ensure_configfs() {
                if [ ! -d "${'$'}{CONFIGFS_ROOT}" ]; then
                    mkdir -p "${'$'}{CONFIGFS_ROOT}"
                fi
                if ! grep -qs "${'$'}{CONFIGFS_ROOT}" /proc/mounts; then
                    mount -t configfs none "${'$'}{CONFIGFS_ROOT}"
                fi
                if ! grep -qs "${'$'}{CONFIGFS_ROOT}" /proc/mounts; then
                    echo "ERROR: Failed to mount ConfigFS at ${'$'}{CONFIGFS_ROOT}" >&2
                    exit 1
                fi
            }

            ensure_configfs_property() {
                if command -v setprop >/dev/null 2>&1; then
                    setprop sys.usb.configfs 1 2>/dev/null || true
                fi
            }

            unbind_other_gadgets() {
                if [ ! -d "${'$'}{CONFIGFS_ROOT}" ]; then
                    return 0
                fi
                for GADGET in "${'$'}{CONFIGFS_ROOT}"/*; do
                    if [ ! -d "${'$'}GADGET" ]; then
                        continue
                    fi
                    if [ "${'$'}GADGET" = "${'$'}{GADGET_DIR}" ]; then
                        continue
                    fi
                    UDC_FILE="${'$'}GADGET/UDC"
                    if [ -f "${'$'}UDC_FILE" ]; then
                        CURRENT=$(cat "${'$'}UDC_FILE" 2>/dev/null || echo "")
                        if [ -n "${'$'}CURRENT" ]; then
                            echo "Releasing existing gadget ${'$'}(basename "${'$'}GADGET") from UDC ${'$'}CURRENT"
                            printf '' > "${'$'}UDC_FILE" 2>/dev/null || true
                            sleep 0.2
                        fi
                    fi
                done
            }

            cleanup() {
                echo "--- Cleaning up previous PXE gadget ---"
                if [ ! -d "${'$'}{GADGET_DIR}" ]; then
                    echo "Gadget directory not found, skipping cleanup."
                    return 0
                fi
                
                if [ -f "${'$'}{GADGET_DIR}/UDC" ] && [ -n "$(cat "${'$'}{GADGET_DIR}/UDC" 2>/dev/null)" ]; then
                    echo "Unbinding UDC..."
                    printf '' > "${'$'}{GADGET_DIR}/UDC" || true
                    sleep 0.2
                fi
                
                echo "Removing function symlinks..."
                find "${'$'}{GADGET_DIR}/configs/c.1" -maxdepth 1 -type l -exec rm -f {} + 2>/dev/null || true
                
                echo "Removing functions..."
                rm -rf "${'$'}{GADGET_DIR}/functions" 2>/dev/null || true

                echo "Removing configuration directories..."
                rm -rf "${'$'}{GADGET_DIR}/configs" 2>/dev/null || true

                echo "Removing string descriptors..."
                rm -rf "${'$'}{GADGET_DIR}/strings" 2>/dev/null || true

                rmdir "${'$'}{GADGET_DIR}" 2>/dev/null || true
            }

            # --- Main Script ---
            cleanup
            ensure_configfs
            ensure_configfs_property
            unbind_other_gadgets
            
            echo "Creating gadget directory: ${'$'}{GADGET_DIR}"
            mkdir -p "${'$'}{GADGET_DIR}"

            echo "Setting USB Descriptors (VID: 0x1d6b, PID: 0x0105)"
            echo "0x1d6b" > "${'$'}{GADGET_DIR}/idVendor"
            echo "0x0105" > "${'$'}{GADGET_DIR}/idProduct"
            echo "0x0200" > "${'$'}{GADGET_DIR}/bcdUSB"

            echo "Creating strings"
            mkdir -p "${'$'}{GADGET_DIR}/strings/0x409"
            echo "oLinkyPXE" > "${'$'}{GADGET_DIR}/strings/0x409/serialnumber"
            echo "oLinky" > "${'$'}{GADGET_DIR}/strings/0x409/manufacturer"
            echo "PXE Boot Gadget" > "${'$'}{GADGET_DIR}/strings/0x409/product"

            echo "Creating configuration"
            mkdir -p "${'$'}{GADGET_DIR}/configs/c.1/strings/0x409"
            echo "PXE" > "${'$'}{GADGET_DIR}/configs/c.1/strings/0x409/configuration"
            echo "500" > "${'$'}{GADGET_DIR}/configs/c.1/MaxPower"
            mkdir -p "${'$'}{GADGET_DIR}/configs/c.1"

            FUNCTION_CREATED=0
            # Try to create RNDIS function first, fall back to ECM
            if mkdir -p "${'$'}{GADGET_DIR}/functions/rndis.${'$'}{INTERFACE_NAME}" 2>/dev/null; then
                echo "Using RNDIS function"
                # Configure RNDIS with fixed MAC addresses
                echo "02:00:00:00:00:01" > "${'$'}{GADGET_DIR}/functions/rndis.${'$'}{INTERFACE_NAME}/host_addr"
                echo "02:00:00:00:00:02" > "${'$'}{GADGET_DIR}/functions/rndis.${'$'}{INTERFACE_NAME}/dev_addr"
                ln -sf "${'$'}{GADGET_DIR}/functions/rndis.${'$'}{INTERFACE_NAME}" "${'$'}{GADGET_DIR}/configs/c.1/"
                FUNCTION_CREATED=1
            elif mkdir -p "${'$'}{GADGET_DIR}/functions/ecm.${'$'}{INTERFACE_NAME}" 2>/dev/null; then
                echo "Using ECM function (RNDIS unavailable)"
                # Configure ECM with fixed MAC addresses
                echo "02:00:00:00:00:01" > "${'$'}{GADGET_DIR}/functions/ecm.${'$'}{INTERFACE_NAME}/host_addr"
                echo "02:00:00:00:00:02" > "${'$'}{GADGET_DIR}/functions/ecm.${'$'}{INTERFACE_NAME}/dev_addr"
                ln -sf "${'$'}{GADGET_DIR}/functions/ecm.${'$'}{INTERFACE_NAME}" "${'$'}{GADGET_DIR}/configs/c.1/"
                FUNCTION_CREATED=1
            fi

            if [ "${'$'}FUNCTION_CREATED" -eq 0 ]; then
                echo "ERROR: Failed to create RNDIS or ECM function." >&2
                exit 2
            fi

            echo "Finding and binding UDC..."
            UDC=${'$'}(ls /sys/class/udc 2>/dev/null | head -n 1)
            if [ -z "${'$'}UDC" ]; then
                echo "ERROR: No UDC found in /sys/class/udc" >&2
                exit 3
            fi
            
            echo "Binding to UDC: ${'$'}UDC"
            echo "${'$'}UDC" > "${'$'}{GADGET_DIR}/UDC"

            echo "PXE gadget setup complete."
        """.trimIndent()
    }

    private fun buildTearDownScript(gadgetName: String): String {
        return """
            set -e
            GADGET_NAME="$gadgetName"
            CONFIGFS_ROOT="/config/usb_gadget"
            GADGET_DIR="${'$'}{CONFIGFS_ROOT}/${'$'}{GADGET_NAME}"

            echo "--- Tearing down PXE gadget ---"
            if [ ! -d "${'$'}{GADGET_DIR}" ]; then
                echo "Gadget directory not found, assuming already torn down."
                exit 0
            fi
            
            if [ -f "${'$'}{GADGET_DIR}/UDC" ] && [ -n "$(cat "${'$'}{GADGET_DIR}/UDC" 2>/dev/null)" ]; then
                echo "Unbinding UDC..."
                printf '' > "${'$'}{GADGET_DIR}/UDC" || true
                sleep 0.2
            fi
            
            echo "Removing function symlinks..."
            find "${'$'}{GADGET_DIR}/configs/c.1" -maxdepth 1 -type l -exec rm -f {} + 2>/dev/null || true
            
            echo "Removing gadget directory..."
            rm -rf "${'$'}{GADGET_DIR}" 2>/dev/null || true
            
            echo "Teardown complete."
        """.trimIndent()
    }
}
