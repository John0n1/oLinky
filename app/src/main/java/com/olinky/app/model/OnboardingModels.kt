package com.olinky.app.model

enum class OnboardingStep(val order: Int) {
    ROOT(0),
    STORAGE(1),
    USB(2),
    COMPLETE(3)
}

data class UsbProfile(
    val id: String,
    val title: String,
    val description: String,
    val udcCandidates: List<String>,
    val supportedFunctions: List<String>
)

object UsbProfiles {
    val defaults: List<UsbProfile> = listOf(
        UsbProfile(
            id = "generic",
            title = "Generic ConfigFS",
            description = "Works on most AOSP/Lineage kernels with ConfigFS support.",
            udcCandidates = listOf("musb-hdrc", "dummy_udc", "android_usb"),
            supportedFunctions = listOf("Mass Storage", "RNDIS", "CDC-ECM")
        ),
        UsbProfile(
            id = "android_system",
            title = "Android System",
            description = "Targets stock Android builds using the legacy android_usb controller exposed by init.",
            udcCandidates = listOf("android_usb", "xhci-hcd"),
            supportedFunctions = listOf("Mass Storage", "ADB", "MTP")
        ),
        UsbProfile(
            id = "pixel_gki",
            title = "Pixel GKI",
            description = "Optimized for Pixel 4+ devices running GKI kernels (dwc3 controller).",
            udcCandidates = listOf("a600000.dwc3"),
            supportedFunctions = listOf("Mass Storage", "RNDIS")
        ),
        UsbProfile(
            id = "samsung_exynos",
            title = "Samsung Exynos",
            description = "Targets Samsung devices that expose UsbMenuSel via sysfs for mode switching.",
            udcCandidates = listOf("exynos-udc", "s3c-hsotg"),
            supportedFunctions = listOf("Mass Storage", "RNDIS", "MTP")
        ),
        UsbProfile(
            id = "qualcomm_qti",
            title = "Qualcomm QTI",
            description = "Best for Snapdragon-based devices that mount a Qualcomm DWC3 controller and the qti USB stack.",
            udcCandidates = listOf("70000000.dwc3", "a800000.dwc3", "xhci-hcd"),
            supportedFunctions = listOf("Mass Storage", "ADB", "RNDIS", "Diag")
        ),
        UsbProfile(
            id = "mediatek_generic",
            title = "MediaTek",
            description = "Covers MediaTek SoCs exposing musb-hdrc with gadget hal integration (common on Realme/Vivo/Oppo).",
            udcCandidates = listOf("11200000.usb", "musb-hdrc", "mtu3"),
            supportedFunctions = listOf("Mass Storage", "RNDIS", "MTP")
        )
    )
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.ROOT,
    val rootGranted: Boolean = false,
    val imageDirectory: String? = null,
    val usbProfileId: String? = null,
    val availableProfiles: List<UsbProfile> = UsbProfiles.defaults,
    val defaultDirectorySuggestion: String? = null,
    val needsStoragePermission: Boolean = false,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val completed: Boolean = false,
    val enabledProfileIds: Set<String> = availableProfiles.map { it.id }.toSet(),
    val recommendedProfileId: String? = null
) {
    val activeProfile: UsbProfile?
        get() = availableProfiles.firstOrNull { it.id == usbProfileId }
}
