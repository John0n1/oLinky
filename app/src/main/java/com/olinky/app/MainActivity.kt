package com.olinky.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.olinky.app.ui.OLinkyAppTheme
import com.olinky.app.ui.screens.OnboardingScreen
import com.olinky.app.ui.screens.OverviewScreen
import com.olinky.app.viewmodel.OnboardingViewModel
import com.olinky.app.viewmodel.OverviewViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OLinkyAppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val onboardingViewModel: OnboardingViewModel = viewModel()
                    val onboardingState by onboardingViewModel.state.collectAsState()

                    if (!onboardingState.completed) {
                        OnboardingScreen(
                            state = onboardingState,
                            onRequestRoot = onboardingViewModel::requestRootAccess,
                            onCreateDirectory = onboardingViewModel::createDefaultDirectory,
                            onSelectUsbProfile = onboardingViewModel::selectUsbProfile,
                            onDismissError = onboardingViewModel::clearError,
                            onRequestStoragePermission = onboardingViewModel::requestStoragePermission,
                            onRefreshStoragePermission = onboardingViewModel::refreshStoragePermissionState
                        )
                    } else {
                        val overviewViewModel: OverviewViewModel = viewModel()
                        val overviewState by overviewViewModel.state.collectAsState()

                        OverviewScreen(
                            state = overviewState,
                            onMountImage = { id, writable -> overviewViewModel.mountImage(id, writable) },
                            onUnmountImage = overviewViewModel::unmountImage,
                            onToggleWritable = overviewViewModel::toggleWritable,
                            onStartPxe = overviewViewModel::startPxe,
                            onStopPxe = overviewViewModel::stopPxe,
                            onAddSampleImage = overviewViewModel::addSampleImage,
                            onRefreshStatus = overviewViewModel::refresh,
                            onClearErrors = overviewViewModel::clearErrors
                        )
                    }
                }
            }
        }
    }
}
