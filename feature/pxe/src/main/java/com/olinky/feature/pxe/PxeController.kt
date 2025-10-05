package com.olinky.feature.pxe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Placeholder for PXE orchestration (DHCP/TFTP/HTTP) atop USB Ethernet gadgets.
 */
class PxeController {
    suspend fun startServers(): Result<Unit> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("PXE stack not implemented yet"))
    }

    suspend fun stopServers(): Result<Unit> = withContext(Dispatchers.IO) {
        Result.success(Unit)
    }
}
