package com.olinky.app.model

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
    val sizeBytes: Long,
    val filesystem: String,
    val bootable: Boolean,
    val mounted: Boolean,
    val writable: Boolean,
    val notes: String? = null
)

data class OverviewUiState(
    val images: List<DiskImageUiModel> = emptyList(),
    val selectedImageId: String? = null,
    val gadgetStatus: GadgetStatusUiModel = GadgetStatusUiModel(
        mode = GadgetMode.Idle,
        connectionLabel = "USB disconnected",
        hostConnected = false,
        lastOperation = null
    ),
    val isBusy: Boolean = false,
    val errors: List<String> = emptyList()
)
