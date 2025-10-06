package com.olinky.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.olinky.app.model.OnboardingStep
import com.olinky.app.model.OnboardingUiState
import com.olinky.app.model.UsbProfile
import com.olinky.app.model.UsbProfiles
import com.olinky.app.ui.OLinkyAppTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onRequestRoot: () -> Unit,
    onCreateDirectory: () -> Unit,
    onSelectUsbProfile: (String) -> Unit,
    onDismissError: () -> Unit,
    onRequestStoragePermission: () -> Unit,
    onRefreshStoragePermission: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val showAdvancedDialog: MutableState<Boolean> = remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(state.step) {
        if (state.step == OnboardingStep.STORAGE) {
            onRefreshStoragePermission()
        }
    }

    DisposableEffect(key1 = lifecycleOwner, key2 = state.step) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && state.step == OnboardingStep.STORAGE) {
                onRefreshStoragePermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            onDismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                OnboardingHeader(state.step)
                AnimatedVisibility(visible = state.isProcessing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                when (state.step) {
                    OnboardingStep.ROOT -> RootStepContent(onRequestRoot = onRequestRoot)
                    OnboardingStep.STORAGE -> StorageStepContent(
                        suggestedPath = state.defaultDirectorySuggestion,
                        currentPath = state.imageDirectory,
                        needsPermission = state.needsStoragePermission,
                        onCreateDirectory = onCreateDirectory,
                        onRequestPermission = onRequestStoragePermission,
                        onRefreshPermission = onRefreshStoragePermission,
                        onShowAdvanced = { showAdvancedDialog.value = true }
                    )
                    OnboardingStep.USB -> UsbStepContent(
                        profiles = state.availableProfiles,
                        selectedProfileId = state.usbProfileId,
                        enabledProfileIds = state.enabledProfileIds,
                        recommendedProfileId = state.recommendedProfileId,
                        onSelectUsbProfile = onSelectUsbProfile
                    )
                    OnboardingStep.COMPLETE -> CompletionContent()
                }
            }
            StepFooter(step = state.step)
        }
    }

    if (showAdvancedDialog.value) {
        AlertDialog(
            onDismissRequest = { showAdvancedDialog.value = false },
            title = { Text("Advanced storage options") },
            text = {
                Text(
                    "You can set a custom directory later from Settings ▸ Storage once advanced file browsing is available. For now, we recommend using the default oLinky/images folder so you can drop ISO files from any file manager.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showAdvancedDialog.value = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
private fun OnboardingHeader(step: OnboardingStep) {
    val title = when (step) {
        OnboardingStep.ROOT -> "Grant root access"
        OnboardingStep.STORAGE -> "Choose image directory"
        OnboardingStep.USB -> "Select USB profile"
        OnboardingStep.COMPLETE -> "All set!"
    }
    val description = when (step) {
        OnboardingStep.ROOT -> "oLinky needs superuser permissions to configure USB gadgets and loopback devices."
        OnboardingStep.STORAGE -> "Pick or create a folder where you’ll store ISO/IMG files for mounting."
        OnboardingStep.USB -> "Tell us which USB controller profile matches your device to optimize gadget setup."
        OnboardingStep.COMPLETE -> "Initial setup is complete. You can now mount images and start PXE services."
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RootStepContent(onRequestRoot: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RowWithIcon(icon = Icons.Filled.Security, text = "Tap the button below to trigger your root manager (Magisk, SuperSU, etc.). Grant access when prompted.")
            HorizontalDivider()
            Button(onClick = onRequestRoot, modifier = Modifier.fillMaxWidth()) {
                Text("Request root access")
            }
            Text(
                text = "We run a harmless \"id -u\" check to request permissions. No changes are made until you mount an image.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StorageStepContent(
    suggestedPath: String?,
    currentPath: String?,
    needsPermission: Boolean,
    onCreateDirectory: () -> Unit,
    onRequestPermission: () -> Unit,
    onRefreshPermission: () -> Unit,
    onShowAdvanced: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RowWithIcon(icon = Icons.Filled.Folder, text = "We recommend creating an oLinky/images directory on your shared storage so you can copy ISOs easily.")
            suggestedPath?.let {
                Text(
                    text = "Suggested path: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            currentPath?.let {
                InfoBadge(label = "Active directory", value = it)
            }
            if (needsPermission) {
                Text(
                    text = "Android needs an additional permission so oLinky can manage files in shared storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                    Text("Grant storage access")
                }
                TextButton(onClick = onRefreshPermission, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh status")
                }
            }
            Button(onClick = onCreateDirectory, modifier = Modifier.fillMaxWidth()) {
                Text("Create recommended folder")
            }
            OutlinedButton(onClick = onShowAdvanced, modifier = Modifier.fillMaxWidth()) {
                Text("Need a different path?")
            }
            Text(
                text = "After setup, drop files like ubuntu.iso or windows.img into this folder using your favorite file manager.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UsbStepContent(
    profiles: List<UsbProfile>,
    selectedProfileId: String?,
    enabledProfileIds: Set<String>,
    recommendedProfileId: String?,
    onSelectUsbProfile: (String) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(profiles, key = { it.id }) { profile ->
            val selected = profile.id == selectedProfileId
            val enabled = enabledProfileIds.contains(profile.id)
            val recommended = profile.id == recommendedProfileId
            val cardModifier = Modifier
                .fillMaxWidth()
                .let { base -> if (!enabled) base.alpha(0.45f) else base }
            ElevatedCard(
                modifier = cardModifier,
                colors = if (selected) CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.elevatedCardColors()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = profile.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = profile.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    AssistRow(label = "UDC candidates", value = profile.udcCandidates.joinToString())
                    AssistRow(label = "Functions", value = profile.supportedFunctions.joinToString())
                    Button(
                        onClick = { onSelectUsbProfile(profile.id) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled
                    ) {
                        Text(if (selected) "Selected" else "Use this profile")
                    }
                    if (recommended) {
                        Text(
                            text = "Automatically detected for this device",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (!enabled) {
                        Text(
                            text = "Unavailable for this hardware",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletionContent() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        tonalElevation = 4.dp,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(64.dp))
            Text(text = "Ready to roll", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                text = "You can revisit setup from Settings ▸ Onboarding if you swap kernels or move your library.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StepFooter(step: OnboardingStep) {
    val totalSteps = 3f
    val progress = when (step) {
        OnboardingStep.ROOT -> 1f / totalSteps
        OnboardingStep.STORAGE -> 2f / totalSteps
        OnboardingStep.USB -> 3f / totalSteps
        OnboardingStep.COMPLETE -> 1f
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text(
            text = when (step) {
                OnboardingStep.ROOT -> "Step 1 of 3"
                OnboardingStep.STORAGE -> "Step 2 of 3"
                OnboardingStep.USB -> "Step 3 of 3"
                OnboardingStep.COMPLETE -> "Setup complete"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RowWithIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InfoBadge(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(text = label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AssistRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Text(text = value, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    val previewState = OnboardingUiState(
        step = OnboardingStep.STORAGE,
        rootGranted = true,
        imageDirectory = "/storage/emulated/0/oLinky/images",
        availableProfiles = UsbProfiles.defaults,
        usbProfileId = "generic",
        defaultDirectorySuggestion = "/storage/emulated/0/oLinky/images",
        enabledProfileIds = setOf("generic"),
        recommendedProfileId = "generic"
    )
    OLinkyAppTheme {
        OnboardingScreen(
            state = previewState,
            onRequestRoot = {},
            onCreateDirectory = {},
            onSelectUsbProfile = {},
            onDismissError = {},
            onRequestStoragePermission = {},
            onRefreshStoragePermission = {}
        )
    }
}
