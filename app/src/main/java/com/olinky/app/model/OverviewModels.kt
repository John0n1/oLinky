package com.olinky.app.model

import android.net.Uri
import com.olinky.app.module.ModuleStatus

enum class GadgetMode {
    Idle,
    MassStorage,
    Pxe
}

data class GadgetStatusUiModel(
    val mode: GadgetMode,
    val connectionLabel: String,
    val hostConnected: Boolean,
    val lastOperation: String? = null
)

data class DiskImageUiModel(
    val id: String,
    val displayName: String,
    val path: String,
    val sizeBytes: Long,
    val bootable: Boolean,
    val mounted: Boolean,
    val writable: Boolean,
    val lastModifiedMillis: Long,
    val notes: String? = null
)

data class OverviewUiState(
    val images: List<DiskImageUiModel> = emptyList(),
    val selectedImageId: String? = null,
    val selectedImageUri: Uri? = null,
    val selectedImageName: String = "No image selected",
    val selectedImageSize: Long = 0,
    val gadgetStatus: GadgetStatusUiModel = GadgetStatusUiModel(
        mode = GadgetMode.Idle,
        connectionLabel = "USB disconnected",
        hostConnected = false,
        lastOperation = null
    ),
    val libraryPath: String? = null,
    val usbProfileLabel: String? = null,
    val rootGranted: Boolean = false,
    val mountState: MountState = MountState.Idle,
    val mountedImageId: String? = null,
    val mountedImagePath: String? = null,
    val isMounted: Boolean = false,
    val isPxeRunning: Boolean = false,
    val statusMessage: String = "Ready",
    val pxeStatusMessage: String = "PXE Disabled",
    val isBusy: Boolean = false,
    val autoMountEnabled: Boolean = false,
    val darkModeEnabled: Boolean = false,
    val moduleStatus: ModuleStatus = ModuleStatus.OK,
    val errors: List<String> = emptyList()
)

enum class MountState {
    Idle,
    Mounting,
    Mounted,
    Unmounting,
    Error
}
