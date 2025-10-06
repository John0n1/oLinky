package com.olinky.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.olinky.app.model.DiskImageUiModel
import com.olinky.app.model.OverviewUiState
import com.olinky.app.module.MagiskModuleInstaller
import com.olinky.app.module.ModuleStatus
import com.olinky.core.gadget.GadgetConfig
import com.olinky.core.gadget.MassStorageConfigRequest
import com.olinky.core.root.RootShell
import com.olinky.data.ImageRepository
import com.olinky.data.OnboardingRepository
import com.olinky.feature.pxe.PxeController
import dagger.hilt.android.lifecycle.HiltViewModel
import android.text.format.Formatter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class OverviewViewModel @Inject constructor(
    application: Application,
    private val imageRepository: ImageRepository,
    private val onboardingRepository: OnboardingRepository,
    private val gadgetConfig: GadgetConfig,
    private val pxeController: PxeController,
    private val moduleInstaller: MagiskModuleInstaller
) : AndroidViewModel(application) {

    private val appContext = application

    private val _uiState = MutableStateFlow(OverviewUiState())
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    init {
        loadInitialState()
        observeImageLibrary()
        checkPermissionsAndGadgets()
    }

    private fun checkPermissionsAndGadgets() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, statusMessage = "Checking permissions...") }
            val status = moduleInstaller.checkAndInstallIfNeeded()
            _uiState.update { it.copy(moduleStatus = status) }

            if (status == ModuleStatus.OK) {
                _uiState.update { it.copy(statusMessage = "Checking for active gadgets...") }
                // Check for existing mount
                gadgetConfig.getMountedImagePath("olinky").onSuccess { path ->
                    if (path != null) {
                        _uiState.update {
                            it.copy(
                                isMounted = true,
                                mountedImagePath = path,
                                selectedImageUri = Uri.parse(path), // This is not a real URI, but good for display
                                selectedImageName = path.substringAfterLast('/'),
                                statusMessage = "Mounted: ${path.substringAfterLast('/')}"
                            )
                        }
                    }
                }
                // Check for existing PXE
                pxeController.isPxeRunning().onSuccess { running ->
                    if (running) {
                        _uiState.update {
                            it.copy(
                                isPxeRunning = true,
                                pxeStatusMessage = "PXE is Active"
                            )
                        }
                    }
                }
                _uiState.update { it.copy(statusMessage = "Ready") }
            } else {
                _uiState.update { it.copy(statusMessage = "SELinux permissions needed") }
            }
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    fun onRebootAction() {
        viewModelScope.launch(Dispatchers.IO) {
            RootShell.runScript("reboot")
        }
    }

    private fun loadInitialState() {
        viewModelScope.launch {
            onboardingRepository.preferencesFlow.collectLatest { prefs ->
                val libraryDir = prefs.imageDirectory?.let { File(it) }
                if (libraryDir != null) {
                    imageRepository.refreshFromDirectory(libraryDir)
                }
                _uiState.update { current ->
                    current.copy(
                        libraryPath = prefs.imageDirectory,
                        usbProfileLabel = prefs.usbProfileId,
                        rootGranted = prefs.rootGranted,
                        autoMountEnabled = prefs.autoMountEnabled,
                        darkModeEnabled = prefs.darkModeEnabled
                    )
                }
            }
        }
    }

    fun refreshLibrary() {
        val path = _uiState.value.libraryPath ?: return
        viewModelScope.launch {
            imageRepository.refreshFromDirectory(File(path))
        }
    }

    fun createBlankImage(fileName: String, sizeBytes: Long) {
        if (_uiState.value.isBusy) return
        val libraryPath = _uiState.value.libraryPath
        if (libraryPath.isNullOrBlank()) {
            _uiState.update {
                it.copy(statusMessage = "Configure a library directory in Settings before creating images.")
            }
            return
        }
        if (sizeBytes <= 0L) {
            _uiState.update {
                it.copy(statusMessage = "Image size must be greater than zero.")
            }
            return
        }

        val directory = File(libraryPath)
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, statusMessage = "Creating $fileName...") }
            val result = withContext(Dispatchers.IO) {
                imageRepository.createBlankImage(directory, fileName, sizeBytes)
            }

            if (result.isSuccess) {
                val file = result.getOrNull()
                if (file != null) {
                    withContext(Dispatchers.IO) {
                        imageRepository.refreshFromDirectory(directory)
                    }
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = "Created ${file.name}",
                            selectedImageId = file.absolutePath,
                            selectedImageUri = Uri.fromFile(file),
                            selectedImageName = file.name,
                            selectedImageSize = file.length()
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            statusMessage = "Failed to create image: unknown error"
                        )
                    }
                }
            } else {
                val error = result.exceptionOrNull()
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        statusMessage = "Failed to create image: ${error?.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    private fun observeImageLibrary() {
        viewModelScope.launch {
            imageRepository.observeImages().collectLatest { storedImages ->
                val mapped = storedImages.map { stored ->
                    DiskImageUiModel(
                        id = stored.id,
                        displayName = stored.label,
                        path = stored.path,
                        sizeBytes = stored.sizeBytes,
                        bootable = stored.bootable,
                        mounted = uiState.value.mountedImagePath == stored.path,
                        writable = true,
                        lastModifiedMillis = stored.lastModifiedMillis,
                        notes = null
                    )
                }

                val currentState = _uiState.value
                val selectedId = when {
                    currentState.selectedImageId != null && mapped.any { it.id == currentState.selectedImageId } ->
                        currentState.selectedImageId
                    mapped.isNotEmpty() -> mapped.first().id
                    else -> null
                }
                val selected = mapped.firstOrNull { it.id == selectedId }
                _uiState.update {
                    it.copy(
                        images = mapped,
                        selectedImageId = selected?.id,
                        selectedImageUri = selected?.path?.let { path -> Uri.fromFile(File(path)) },
                        selectedImageName = selected?.displayName ?: "No image selected",
                        selectedImageSize = selected?.sizeBytes ?: 0
                    )
                }
            }
        }
    }

    fun onImageEntrySelected(imageId: String) {
        val image = _uiState.value.images.firstOrNull { it.id == imageId } ?: return
        _uiState.update {
            it.copy(
                selectedImageId = image.id,
                selectedImageUri = Uri.fromFile(File(image.path)),
                selectedImageName = image.displayName,
                selectedImageSize = image.sizeBytes,
                statusMessage = "Selected image: ${image.displayName} (${formatFileSize(image.sizeBytes)})"
            )
        }
    }

    fun onMountToggle(mount: Boolean) {
        if (mount) {
            mountImage()
        } else {
            unmountImage()
        }
    }

    private fun mountImage() {
        val imagePath = _uiState.value.images
            .firstOrNull { it.id == _uiState.value.selectedImageId }
            ?.path
        if (imagePath == null) {
            _uiState.update { it.copy(statusMessage = "Select an image from your library before mounting.") }
            return
        }

        viewModelScope.launch {
            if (uiState.value.moduleStatus != ModuleStatus.OK) {
                _uiState.update { it.copy(statusMessage = "SELinux helper required. Install and reboot first.") }
                return@launch
            }
            _uiState.update { it.copy(isBusy = true, statusMessage = "Mounting ${uiState.value.selectedImageName}...") }
            withContext(Dispatchers.IO) {
                val request = MassStorageConfigRequest(
                    gadgetName = "olinky",
                    imagePath = imagePath
                )
                gadgetConfig.applyMassStorageConfig(request).fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                isMounted = true,
                                mountedImagePath = imagePath,
                                mountedImageId = _uiState.value.selectedImageId,
                                isBusy = false,
                                statusMessage = "Mounted: ${it.selectedImageName}"
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isMounted = false,
                                isBusy = false,
                                statusMessage = "Mount failed: ${error.message}"
                            )
                        }
                    }
                )
            }
        }
    }

    private fun unmountImage() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, statusMessage = "Unmounting...") }
            withContext(Dispatchers.IO) {
                gadgetConfig.tearDownMassStorage("olinky").fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                isMounted = false,
                                mountedImagePath = null,
                                mountedImageId = null,
                                isBusy = false,
                                statusMessage = "Unmounted successfully"
                            )
                        }
                    },
                    onFailure = { error ->
                        // Even if it fails, update state to reflect the desired outcome
                        _uiState.update {
                            it.copy(
                                isMounted = false,
                                isBusy = false,
                                statusMessage = "Unmount failed: ${error.message}"
                            )
                        }
                    }
                )
            }
        }
    }

    fun onPxeToggle(enable: Boolean) {
        if (enable) {
            enablePxe()
        } else {
            disablePxe()
        }
    }

    private fun enablePxe() {
        viewModelScope.launch {
            if (uiState.value.moduleStatus != ModuleStatus.OK) {
                _uiState.update { it.copy(pxeStatusMessage = "SELinux helper required. Install and reboot first.") }
                return@launch
            }
            _uiState.update { it.copy(isBusy = true, pxeStatusMessage = "Starting PXE...") }
            withContext(Dispatchers.IO) {
                pxeController.startPxe().fold(
                    onSuccess = {
                        pxeController.startServers().fold(
                            onSuccess = {
                                _uiState.update {
                                    it.copy(
                                        isPxeRunning = true,
                                        isBusy = false,
                                        pxeStatusMessage = "PXE servers started"
                                    )
                                }
                            },
                            onFailure = { serverError ->
                                _uiState.update {
                                    it.copy(
                                        isBusy = false,
                                        pxeStatusMessage = "Error starting servers: ${serverError.message}"
                                    )
                                }
                            }
                        )
                    },
                    onFailure = { gadgetError ->
                        _uiState.update {
                            it.copy(
                                isPxeRunning = false,
                                isBusy = false,
                                pxeStatusMessage = "PXE startup failed: ${gadgetError.message}"
                            )
                        }
                    }
                )
            }
        }
    }

    private fun disablePxe() {
        viewModelScope.launch {
            if (uiState.value.moduleStatus != ModuleStatus.OK) {
                _uiState.update { it.copy(pxeStatusMessage = "SELinux helper required. Install and reboot first.") }
                return@launch
            }
            _uiState.update { it.copy(isBusy = true, pxeStatusMessage = "Stopping PXE...") }
            withContext(Dispatchers.IO) {
                val serverResult = pxeController.stopServers()
                val gadgetResult = pxeController.stopPxe()
                val networkResult = pxeController.teardownNetwork()

                val failure = serverResult.exceptionOrNull()
                    ?: gadgetResult.exceptionOrNull()
                    ?: networkResult.exceptionOrNull()

                if (failure == null) {
                    _uiState.update {
                        it.copy(
                            isPxeRunning = false,
                            isBusy = false,
                            pxeStatusMessage = "PXE disabled"
                        )
                    }
                } else {
                    val gadgetStopped = gadgetResult.isSuccess
                    _uiState.update {
                        it.copy(
                            isPxeRunning = if (gadgetStopped) false else it.isPxeRunning,
                            isBusy = false,
                            pxeStatusMessage = "Failed to stop PXE: ${failure.message}"
                        )
                    }
                }
            }
        }
    }

    private fun formatFileSize(bytes: Long): String = Formatter.formatShortFileSize(appContext, bytes)
}
