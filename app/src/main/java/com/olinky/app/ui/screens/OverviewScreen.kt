package com.olinky.app.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.olinky.app.model.DiskImageUiModel
import com.olinky.app.module.ModuleStatus
import com.olinky.app.viewmodel.OverviewViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("oLinky") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.moduleStatus != ModuleStatus.OK) {
                ModuleInstallCard(
                    status = uiState.moduleStatus,
                    onRebootClick = { viewModel.onRebootAction() }
                )
            }

            DiskLibraryCard(
                images = uiState.images,
                selectedImageId = uiState.selectedImageId,
                libraryPath = uiState.libraryPath,
                onSelect = viewModel::onImageEntrySelected,
                onRefresh = viewModel::refreshLibrary,
                onCreateImage = viewModel::createBlankImage,
                isBusy = uiState.isBusy
            )

            MountControlCard(
                imageName = uiState.selectedImageName,
                imageSize = uiState.selectedImageSize,
                isMounted = uiState.isMounted,
                isBusy = uiState.isBusy,
                onMountToggle = { mount -> viewModel.onMountToggle(mount) }
            )

            PxeBootCard(
                isPxeRunning = uiState.isPxeRunning,
                isBusy = uiState.isBusy,
                statusMessage = uiState.pxeStatusMessage,
                onPxeToggle = { enable -> viewModel.onPxeToggle(enable) }
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = uiState.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ModuleInstallCard(status: ModuleStatus, onRebootClick: () -> Unit) {
    val message = when (status) {
        ModuleStatus.NEEDS_REBOOT -> "SELinux module has been installed. Please reboot for it to take effect."
        ModuleStatus.INSTALL_FAILED -> "Failed to install the required SELinux module. Please install it manually via Magisk."
        ModuleStatus.ROOT_UNAVAILABLE -> "Root access is not available. This app requires root."
        else -> return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Action Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            if (status == ModuleStatus.NEEDS_REBOOT) {
                ElevatedButton(onClick = onRebootClick) {
                    Text("Reboot Now")
                }
            }
        }
    }
}


@Composable
fun MountControlCard(
    imageName: String,
    imageSize: Long,
    isMounted: Boolean,
    isBusy: Boolean,
    onMountToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("USB Mass Storage", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = imageName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (imageSize > 0) {
                Text(
                    text = "Size: ${Formatter.formatShortFileSize(LocalContext.current, imageSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Select an image from your library below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Mount as USB Drive", style = MaterialTheme.typography.bodyLarge)
                if (isBusy && !isMounted) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Switch(
                        checked = isMounted,
                        onCheckedChange = onMountToggle,
                        enabled = !isBusy && imageSize > 0
                    )
                }
            }
        }
    }
}

@Composable
fun PxeBootCard(
    isPxeRunning: Boolean,
    isBusy: Boolean,
    statusMessage: String,
    onPxeToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("PXE Boot Server", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enable PXE Boot", style = MaterialTheme.typography.bodyLarge)
                if (isBusy && isPxeRunning) { // Show progress only when toggling PXE
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Switch(
                        checked = isPxeRunning,
                        onCheckedChange = onPxeToggle,
                        enabled = !isBusy
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DiskLibraryCard(
    images: List<DiskImageUiModel>,
    selectedImageId: String?,
    libraryPath: String?,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    onCreateImage: (String, Long) -> Unit,
    isBusy: Boolean
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Disk Image Library", style = MaterialTheme.typography.titleLarge)
            libraryPath?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } ?: run {
                Text(
                    text = "No library folder configured. Complete onboarding in Settings to choose a directory.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (images.isEmpty()) {
                Text(
                    text = "Drop ISO/IMG files into your oLinky library directory to see them here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onRefresh, enabled = !isBusy) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Refresh library")
                    }
                    ElevatedButton(
                        onClick = { showCreateDialog = true },
                        enabled = !isBusy && libraryPath != null
                    ) {
                        Text("Create blank image")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onRefresh, enabled = !isBusy) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh library")
                }
                    ElevatedButton(
                        onClick = { showCreateDialog = true },
                        enabled = !isBusy && libraryPath != null
                    ) {
                        Text("Create blank image")
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (images.size > 4) 240.dp else (images.size * 60).coerceAtLeast(120).dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(images, key = { it.id }) { image ->
                        DiskImageRow(
                            image = image,
                            selected = image.id == selectedImageId,
                            onSelect = { onSelect(image.id) }
                        )
                    }
                }
            }
        }

        if (showCreateDialog) {
            CreateImageDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name, sizeBytes ->
                    onCreateImage(name, sizeBytes)
                    showCreateDialog = false
                }
            )
        }
    }
}

@Composable
private fun DiskImageRow(
    image: DiskImageUiModel,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val context = LocalContext.current
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable(onClick = onSelect)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = image.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = Formatter.formatShortFileSize(context, image.sizeBytes),
            style = MaterialTheme.typography.bodySmall,
            color = contentColor
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (image.bootable) {
                Text(
                    text = "Bootable",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor
                )
            }
            if (image.mounted) {
                Text(
                    text = "Mounted",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun CreateImageDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Long) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("new-disk.iso") }
    var sizeInput by rememberSaveable { mutableStateOf("1") }
    var unit by rememberSaveable { mutableStateOf(ImageSizeUnit.GIGABYTE) }

    val sizeValue = sizeInput.toLongOrNull()
    val sizeBytes = sizeValue?.let { value ->
        try {
            Math.multiplyExact(value, unit.multiplier)
        } catch (e: ArithmeticException) {
            null
        }
    }
    val canCreate = name.trim().isNotEmpty() && sizeBytes != null && sizeBytes > 0
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create blank image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("File name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = sizeInput,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) {
                            sizeInput = input
                        }
                    },
                    label = { Text("Size") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    trailingIcon = {
                        Text(unit.label, style = MaterialTheme.typography.labelMedium)
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageSizeUnit.values().forEach { candidate ->
                        FilterChip(
                            selected = unit == candidate,
                            onClick = { unit = candidate },
                            label = { Text(candidate.label) }
                        )
                    }
                }
                Text(
                    text = "Resulting size: ${sizeBytes?.let { Formatter.formatShortFileSize(context, it) } ?: "--"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { sizeBytes?.let { onCreate(name.trim(), it) } }, enabled = canCreate) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private enum class ImageSizeUnit(val label: String, val multiplier: Long) {
    MEGABYTE("MB", 1024L * 1024L),
    GIGABYTE("GB", 1024L * 1024L * 1024L)
}
