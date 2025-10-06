package com.olinky.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.olinky.app.model.DiskImageUiModel
import com.olinky.app.model.GadgetMode
import com.olinky.app.model.GadgetStatusUiModel
import com.olinky.app.model.OverviewUiState
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OverviewViewModel : ViewModel() {

    private val _state = MutableStateFlow(createInitialState())
    val state: StateFlow<OverviewUiState> = _state.asStateFlow()

    fun refresh() {
        _state.update { current ->
            current.copy(errors = emptyList())
        }
    }

    fun mountImage(imageId: String, writable: Boolean) {
        operateWithSpinner {
            val image = _state.value.images.firstOrNull { it.id == imageId }
                ?: return@operateWithSpinner registerError("Image not found")

            _state.update { current ->
                val updatedImages = current.images.map {
                    if (it.id == imageId) it.copy(mounted = true, writable = writable)
                    else it.copy(mounted = false)
                }
                current.copy(
                    images = updatedImages,
                    selectedImageId = imageId,
                    gadgetStatus = GadgetStatusUiModel(
                        mode = GadgetMode.MassStorage,
                        connectionLabel = "Mass storage gadget active",
                        hostConnected = current.gadgetStatus.hostConnected,
                        lastOperation = "Mounted ${image.displayName} (${if (writable) "RW" else "RO"})"
                    ),
                    errors = emptyList()
                )
            }
        }
    }

    fun unmountImage(imageId: String) {
        operateWithSpinner {
            _state.update { current ->
                if (current.selectedImageId != imageId) current
                else {
                    val updated = current.images.map { it.copy(mounted = false) }
                    current.copy(
                        images = updated,
                        selectedImageId = null,
                        gadgetStatus = GadgetStatusUiModel(
                            mode = GadgetMode.Idle,
                            connectionLabel = "USB gadget idle",
                            hostConnected = false,
                            lastOperation = "Unmounted image"
                        ),
                        errors = emptyList()
                    )
                }
            }
        }
    }

    fun startPxe() {
        operateWithSpinner {
            _state.update { current ->
                current.copy(
                    gadgetStatus = GadgetStatusUiModel(
                        mode = GadgetMode.Pxe,
                        connectionLabel = "PXE services listening",
                        hostConnected = true,
                        lastOperation = "PXE stack started"
                    ),
                    errors = emptyList()
                )
            }
        }
    }

    fun stopPxe() {
        operateWithSpinner {
            _state.update { current ->
                current.copy(
                    gadgetStatus = GadgetStatusUiModel(
                        mode = GadgetMode.Idle,
                        connectionLabel = "Services stopped",
                        hostConnected = false,
                        lastOperation = "PXE stack stopped"
                    )
                )
            }
        }
    }

    fun toggleWritable(imageId: String) {
        _state.update { current ->
            val image = current.images.firstOrNull { it.id == imageId } ?: return@update current
            val updated = current.images.map {
                if (it.id == imageId) it.copy(writable = !image.writable) else it
            }
            current.copy(images = updated)
        }
    }

    fun addSampleImage() {
        _state.update { current ->
            val sample = DiskImageUiModel(
                id = UUID.randomUUID().toString(),
                displayName = "Custom ISO ${current.images.size + 1}",
                sizeBytes = 2_048_000_000L,
                filesystem = "ISO9660",
                bootable = true,
                mounted = false,
                writable = false,
                notes = "Imported locally"
            )
            current.copy(images = current.images + sample)
        }
    }

    fun setHostConnection(connected: Boolean) {
        _state.update { current ->
            current.copy(
                gadgetStatus = current.gadgetStatus.copy(
                    hostConnected = connected,
                    connectionLabel = if (connected) "Host detected" else "Waiting for host",
                    lastOperation = current.gadgetStatus.lastOperation
                )
            )
        }
    }

    fun clearErrors() {
        _state.update { it.copy(errors = emptyList()) }
    }

    private fun operateWithSpinner(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            try {
                block()
            } finally {
                delay(150)
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    private fun registerError(message: String) {
        _state.update { current ->
            current.copy(
                errors = current.errors + message,
                isBusy = false
            )
        }
    }

    private fun createInitialState(): OverviewUiState {
        val images = listOf(
            DiskImageUiModel(
                id = UUID.randomUUID().toString(),
                displayName = "Debian 12 NetInstall",
                sizeBytes = 400_000_000L,
                filesystem = "ISO9660",
                bootable = true,
                mounted = false,
                writable = false,
                notes = "UEFI & legacy"
            ),
            DiskImageUiModel(
                id = UUID.randomUUID().toString(),
                displayName = "Ventoy Rescue Kit",
                sizeBytes = 1_500_000_000L,
                filesystem = "exFAT",
                bootable = true,
                mounted = false,
                writable = true,
                notes = "Diagnostics bundle"
            ),
            DiskImageUiModel(
                id = UUID.randomUUID().toString(),
                displayName = "FreeDOS Utility",
                sizeBytes = 64_000_000L,
                filesystem = "FAT32",
                bootable = true,
                mounted = false,
                writable = true,
                notes = "Flash BIOS scripts"
            )
        )
        return OverviewUiState(
            images = images,
            gadgetStatus = GadgetStatusUiModel(
                mode = GadgetMode.Idle,
                connectionLabel = "USB gadget idle",
                hostConnected = false,
                lastOperation = "Ready to mount"
            ),
            errors = emptyList()
        )
    }
}
