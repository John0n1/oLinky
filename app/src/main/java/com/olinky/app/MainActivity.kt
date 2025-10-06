package com.olinky.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.olinky.app.model.UsbProfiles
import com.olinky.app.ui.OLinkyAppTheme
import com.olinky.app.ui.screens.OnboardingScreen
import com.olinky.app.ui.screens.OverviewScreen
import com.olinky.app.ui.screens.SettingsScreen
import com.olinky.app.viewmodel.OnboardingViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OLinkyAppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
                    val onboardingState by onboardingViewModel.state.collectAsState()
                    var showSettings by remember { mutableStateOf(false) }

                    when {
                        !onboardingState.completed -> {
                            OnboardingScreen(
                                state = onboardingState,
                                onRequestRoot = onboardingViewModel::requestRootAccess,
                                onCreateDirectory = onboardingViewModel::createDefaultDirectory,
                                onSelectUsbProfile = onboardingViewModel::selectUsbProfile,
                                onDismissError = onboardingViewModel::clearError,
                                onRequestStoragePermission = onboardingViewModel::requestStoragePermission,
                                onRefreshStoragePermission = onboardingViewModel::refreshStoragePermissionState
                            )
                        }
                        showSettings -> {
                            SettingsScreen(
                                libraryPath = onboardingState.imageDirectory,
                                usbProfile = onboardingState.usbProfileId,
                                rootGranted = onboardingState.rootGranted,
                                autoMountEnabled = onboardingState.autoMountEnabled,
                                darkModeEnabled = onboardingState.darkModeEnabled,
                                availableProfiles = UsbProfiles.defaults,
                                onNavigateBack = { showSettings = false },
                                onRetestRoot = onboardingViewModel::requestRootAccess,
                                onChangeDirectory = onboardingViewModel::setCustomDirectory,
                                onSelectUsbProfile = onboardingViewModel::selectUsbProfile,
                                onRerunOnboarding = {
                                    onboardingViewModel.resetOnboarding()
                                    showSettings = false
                                },
                                onAutoMountChanged = onboardingViewModel::setAutoMountEnabled,
                                onDarkModeChanged = onboardingViewModel::setDarkModeEnabled
                            )
                        }
                        else -> {
                            OverviewScreen(onNavigateToSettings = { showSettings = true })
                        }
                    }
                }
            }
        }
    }
}
