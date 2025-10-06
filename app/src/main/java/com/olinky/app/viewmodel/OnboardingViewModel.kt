package com.olinky.app.viewmodel

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.olinky.app.model.OnboardingStep
import com.olinky.app.model.OnboardingUiState
import com.olinky.app.model.UsbProfile
import com.olinky.app.model.UsbProfiles
import com.olinky.core.root.RootShell
import com.olinky.data.OnboardingPreferences
import com.olinky.data.OnboardingRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = OnboardingRepository(application.applicationContext)
    private val defaultDirectory = File(Environment.getExternalStorageDirectory(), "oLinky/images")
    private val usbProfiles = UsbProfiles.defaults
    private val detectionResult = detectUsbProfiles(usbProfiles)
    private var autoProfileApplied = false

    private val _state = MutableStateFlow(
        OnboardingUiState(
            availableProfiles = usbProfiles,
            defaultDirectorySuggestion = defaultDirectory.absolutePath,
            needsStoragePermission = !hasManageStoragePermission(),
            enabledProfileIds = detectionResult.enabledIds.ifEmpty { usbProfiles.map { it.id }.toSet() },
            recommendedProfileId = detectionResult.recommendedProfileId,
            usbProfileId = detectionResult.recommendedProfileId
        )
    )
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.preferencesFlow.collect { prefs ->
                val detection = detectionResult
                if (!autoProfileApplied && prefs.usbProfileId.isNullOrBlank() && detection.recommendedProfileId != null) {
                    autoProfileApplied = true
                    repository.setUsbProfile(detection.recommendedProfileId)
                }
                val nextStep = determineStep(prefs)
                if (nextStep == OnboardingStep.COMPLETE && !prefs.completed) {
                    repository.setCompleted(true)
                } else if (nextStep != OnboardingStep.COMPLETE && prefs.completed) {
                    repository.setCompleted(false)
                }
                _state.update { current ->
                    val enabledIds = detection.enabledIds.ifEmpty { usbProfiles.map { it.id }.toSet() }
                    val resolvedProfileId = prefs.usbProfileId ?: detection.recommendedProfileId
                    current.copy(
                        availableProfiles = usbProfiles,
                        step = nextStep,
                        rootGranted = prefs.rootGranted,
                        imageDirectory = prefs.imageDirectory,
                        usbProfileId = resolvedProfileId,
                        enabledProfileIds = enabledIds,
                        recommendedProfileId = detection.recommendedProfileId,
                        needsStoragePermission = if (nextStep == OnboardingStep.STORAGE) !hasManageStoragePermission() else false,
                        completed = nextStep == OnboardingStep.COMPLETE,
                        isProcessing = false,
                        errorMessage = if (nextStep != current.step) null else current.errorMessage
                    )
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun requestRootAccess() {
        if (_state.value.isProcessing) return
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, errorMessage = null) }
            val result = RootShell.runScript("id -u")
            val granted = result.exitCode == 0 && result.stdout.firstOrNull()?.trim() == "0"
            if (granted) {
                repository.setRootGranted(true)
            } else {
                repository.setRootGranted(false)
                repository.setCompleted(false)
                val message = when {
                    result.exitCode == -1 -> result.stderr.firstOrNull() ?: "Root binary not found. Install Magisk or SuperSU then retry."
                    result.stderr.isNotEmpty() -> result.stderr.joinToString(separator = "\n")
                    else -> "Root access denied. Approve oLinky in Magisk/SuperSU and try again."
                }
                _state.update { it.copy(isProcessing = false, errorMessage = message) }
            }
        }
    }

    fun createDefaultDirectory() {
        if (_state.value.isProcessing) return
        viewModelScope.launch {
            if (!hasManageStoragePermission()) {
                _state.update {
                    it.copy(
                        isProcessing = false,
                        needsStoragePermission = true,
                        errorMessage = "Storage permission required. Tap \"Grant storage access\", approve, then retry."
                    )
                }
                return@launch
            }

            _state.update { it.copy(isProcessing = true, errorMessage = null, needsStoragePermission = false) }
            val path = withContext(Dispatchers.IO) {
                ensureDirectory(defaultDirectory) ?: ensureDirectoryWithRoot(defaultDirectory)
            }
            if (path != null) {
                repository.setImageDirectory(path)
            } else {
                _state.update {
                    it.copy(
                        isProcessing = false,
                        needsStoragePermission = !hasManageStoragePermission(),
                        errorMessage = "Failed to create ${defaultDirectory.absolutePath}. Ensure storage access is granted."
                    )
                }
            }
        }
    }

    fun setCustomDirectory(path: String) {
        viewModelScope.launch {
            repository.setImageDirectory(path)
        }
    }

    fun selectUsbProfile(profileId: String) {
        if (_state.value.isProcessing) return
        val profileExists = _state.value.availableProfiles.any { it.id == profileId }
        val isEnabled = _state.value.enabledProfileIds.contains(profileId)
        if (!profileExists || !isEnabled) return
        viewModelScope.launch {
            repository.setUsbProfile(profileId)
        }
    }

    fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val app = getApplication<Application>()
            val packageName = app.packageName
            val appIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val allFilesIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                app.startActivity(appIntent)
            } catch (e: ActivityNotFoundException) {
                try {
                    app.startActivity(allFilesIntent)
                } catch (_: ActivityNotFoundException) {
                    _state.update {
                        it.copy(
                            errorMessage = "Unable to open storage settings. Grant All Files Access manually in system settings.",
                            needsStoragePermission = true
                        )
                    }
                }
            }
        }
    }

    fun refreshStoragePermissionState() {
        _state.update { it.copy(needsStoragePermission = !hasManageStoragePermission()) }
    }

    private fun detectUsbProfiles(profiles: List<UsbProfile>): UsbProfileDetection {
        val manufacturer = Build.MANUFACTURER?.lowercase()?.trim().orEmpty()
        val brand = Build.BRAND?.lowercase()?.trim().orEmpty()
        val device = Build.DEVICE?.lowercase()?.trim().orEmpty()
        val model = Build.MODEL?.lowercase()?.trim().orEmpty()
        val hardware = Build.HARDWARE?.lowercase()?.trim().orEmpty()
        val board = Build.BOARD?.lowercase()?.trim().orEmpty()
        val product = Build.PRODUCT?.lowercase()?.trim().orEmpty()

        val udcMatch = profiles.firstOrNull { profile ->
            profile.udcCandidates.any(::udcPathExists)
        }

        val detectors = listOf(
            UsbProfileDetector(
                profileId = "pixel_gki",
                priority = 120,
                manufacturers = listOf("google"),
                devicePrefixes = listOf("oriole", "raven", "bluejay", "cheetah", "panther", "husky", "lynx"),
                hardwarePrefixes = listOf("gs", "qcom"),
                modelPrefixes = listOf("pixel"),
                productPrefixes = listOf("oriole", "raven", "cheetah", "panther", "husky", "lynx")
            ),
            UsbProfileDetector(
                profileId = "samsung_exynos",
                priority = 110,
                manufacturers = listOf("samsung"),
                hardwarePrefixes = listOf("exynos"),
                modelPrefixes = listOf("sm-"),
                devicePrefixes = listOf("starlte", "star2lte", "crownlte", "beyond", "d2s", "d1x"),
                productPrefixes = listOf("starlte", "star2lte", "crownlte", "beyond", "d2s", "d1x")
            ),
            UsbProfileDetector(
                profileId = "qualcomm_qti",
                priority = 100,
                manufacturers = listOf("oneplus", "xiaomi", "sony", "nothing", "fairphone"),
                brands = listOf("oneplus", "xiaomi", "sony", "nothing", "fairphone", "asus", "motorola"),
                hardwarePrefixes = listOf("qcom"),
                modelPrefixes = listOf("le", "in", "gm", "a20", "iv", "xq"),
                boardPrefixes = listOf("lito", "kona", "lahaina", "taroko", "kalama"),
                productPrefixes = listOf("le", "in", "gm", "xq", "ne")
            ),
            UsbProfileDetector(
                profileId = "mediatek_generic",
                priority = 90,
                manufacturers = listOf("mediatek", "oppo", "realme", "vivo", "tecno", "infinix", "xiaomi"),
                brands = listOf("oppo", "realme", "vivo", "tecno", "infinix", "poco", "redmi"),
                hardwarePrefixes = listOf("mt", "mediatek"),
                boardPrefixes = listOf("mt"),
                devicePrefixes = listOf("rmx", "v", "cp", "mp", "x"),
                productPrefixes = listOf("rmx", "v", "cp", "mp", "x")
            ),
            UsbProfileDetector(
                profileId = "android_system",
                priority = 80,
                manufacturers = listOf("google", "samsung", "oneplus", "xiaomi", "sony", "motorola", "asus", "hmd global"),
                brands = listOf("google", "samsung", "oneplus", "xiaomi", "sony", "motorola", "asus", "nokia"),
                hardwarePrefixes = listOf("gs", "qcom", "exynos", "mt"),
                modelPrefixes = listOf("pixel", "sm-", "le", "xq", "xt"),
                boardPrefixes = listOf("gs", "kona", "lahaina", "mt"),
                productPrefixes = listOf("pixel", "sm-", "le", "xq", "xt")
            ),
            UsbProfileDetector(
                profileId = "generic",
                priority = 10
            )
        )

        val heuristicMatch = detectors
            .sortedByDescending { it.priority }
            .firstOrNull { detector ->
                val profile = profiles.firstOrNull { it.id == detector.profileId }
                val udcCandidateHit = profile?.udcCandidates?.any(::udcPathExists) ?: false
                detector.matches(
                    manufacturer = manufacturer,
                    brand = brand,
                    device = device,
                    model = model,
                    hardware = hardware,
                    board = board,
                    product = product
                ) || udcCandidateHit
            }

        val recommendedId = udcMatch?.id ?: heuristicMatch?.profileId
        val enabledIds = if (recommendedId != null) setOf(recommendedId) else profiles.map { it.id }.toSet()

        return UsbProfileDetection(enabledIds = enabledIds, recommendedProfileId = recommendedId)
    }

    private fun udcPathExists(candidate: String): Boolean {
        if (candidate.isBlank()) return false
        val searchPaths = listOf(
            "/sys/class/udc/$candidate",
            "/sys/devices/platform/$candidate",
            "/sys/devices/platform/$candidate/udc",
            "/sys/devices/$candidate"
        )
        return searchPaths.any { path -> runCatching { File(path).exists() }.getOrDefault(false) }
    }

    private data class UsbProfileDetection(
        val enabledIds: Set<String>,
        val recommendedProfileId: String?
    )

    private data class UsbProfileDetector(
        val profileId: String,
        val priority: Int,
        val manufacturers: List<String> = emptyList(),
        val brands: List<String> = emptyList(),
        val devicePrefixes: List<String> = emptyList(),
        val modelPrefixes: List<String> = emptyList(),
        val hardwarePrefixes: List<String> = emptyList(),
        val boardPrefixes: List<String> = emptyList(),
        val productPrefixes: List<String> = emptyList()
    ) {
        fun matches(
            manufacturer: String,
            brand: String,
            device: String,
            model: String,
            hardware: String,
            board: String,
            product: String
        ): Boolean {
            return manufacturers.any { manufacturer.contains(it) } ||
                brands.any { brand.contains(it) } ||
                devicePrefixes.any { device.startsWith(it) } ||
                modelPrefixes.any { model.startsWith(it) } ||
                hardwarePrefixes.any { hardware.startsWith(it) } ||
                boardPrefixes.any { board.startsWith(it) } ||
                productPrefixes.any { product.startsWith(it) }
        }
    }

    private suspend fun ensureDirectory(target: File): String? {
        return runCatching {
            if (!target.exists()) {
                target.mkdirs()
            }
            if (target.exists() && target.isDirectory) {
                // Drop a .nomedia file to keep galleries clean
                val nomedia = File(target, ".nomedia")
                if (!nomedia.exists()) {
                    nomedia.createNewFile()
                }
                // Touch a temp file to verify write access
                val probe = File(target, "._olinky_probe")
                probe.writeText("ok")
                probe.delete()
                target.absolutePath
            } else {
                null
            }
        }.getOrNull()
    }

    private suspend fun ensureDirectoryWithRoot(target: File): String? {
        val absolutePath = target.absolutePath
        val escapedPath = absolutePath.replace("\"", "\\\"")
                val script = """
                        set -e
                        TARGET=\"$escapedPath\"
                        PARENT=\"\${'$'}(dirname \"$escapedPath\")\"
                        if [ ! -d \"$escapedPath\" ]; then
                            mkdir -p \"$escapedPath\"
                        fi
                        if [ -d \"$escapedPath\" ]; then
                            OWNER=\${'$'}(stat -c '%u:%g' \"$escapedPath\" 2>/dev/null || stat -c '%u:%g' \"\${'$'}PARENT\")
                            chown \${'$'}OWNER \"$escapedPath\"
                            chmod 775 \"$escapedPath\"
                        fi
                """.trimIndent()
        val result = RootShell.runScript(script)
        return if (result.exitCode == 0) {
            ensureDirectory(target)
        } else {
            null
        }
    }

    private fun determineStep(prefs: OnboardingPreferences): OnboardingStep = when {
        !prefs.rootGranted -> OnboardingStep.ROOT
        prefs.imageDirectory.isNullOrBlank() -> OnboardingStep.STORAGE
        prefs.usbProfileId.isNullOrBlank() -> OnboardingStep.USB
        else -> OnboardingStep.COMPLETE
    }

    private fun hasManageStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }
}
