package com.olinky.app.ui.screens

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.olinky.app.model.DiskImageUiModel
import com.olinky.app.model.GadgetMode
import com.olinky.app.model.GadgetStatusUiModel
import com.olinky.app.model.OverviewUiState
import com.olinky.app.ui.OLinkyAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    state: OverviewUiState,
    onMountImage: (String, Boolean) -> Unit,
    onUnmountImage: (String) -> Unit,
    onToggleWritable: (String) -> Unit,
    onStartPxe: () -> Unit,
    onStopPxe: () -> Unit,
    onAddSampleImage: () -> Unit,
    onRefreshStatus: () -> Unit,
    onClearErrors: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.errors) {
        if (state.errors.isNotEmpty()) {
            snackbarHostState.showSnackbar(state.errors.joinToString(separator = "\n"))
            onClearErrors()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "oLinky") },
                actions = {
                    IconButton(onClick = onRefreshStatus) {
                        Icon(imageVector = Icons.Outlined.Info, contentDescription = "Refresh status")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddSampleImage,
                icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = "Add image") },
                text = { Text("Import image") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GadgetStatusCard(
                status = state.gadgetStatus,
                isBusy = state.isBusy,
                onStartPxe = onStartPxe,
                onStopPxe = onStopPxe
            )

            AnimatedVisibility(visible = state.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (state.images.isEmpty()) {
                EmptyStateCard(onAddSampleImage)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(state.images, key = { it.id }) { image ->
                        DiskImageCard(
                            image = image,
                            formattedSize = Formatter.formatShortFileSize(context, image.sizeBytes),
                            onMount = { onMountImage(image.id, image.writable) },
                            onUnmount = { onUnmountImage(image.id) },
                            onToggleWritable = { onToggleWritable(image.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GadgetStatusCard(
    status: GadgetStatusUiModel,
    isBusy: Boolean,
    onStartPxe: () -> Unit,
    onStopPxe: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(imageVector = Icons.Filled.Usb, contentDescription = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = status.connectionLabel, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when (status.mode) {
                            GadgetMode.Idle -> "Waiting for action"
                            GadgetMode.MassStorage -> "USB mass storage active"
                            GadgetMode.Pxe -> "PXE services running"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    status.lastOperation?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onStartPxe, enabled = !isBusy && status.mode != GadgetMode.Pxe) {
                    Icon(imageVector = Icons.Filled.Cloud, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start PXE")
                }
                OutlinedButton(onClick = onStopPxe, enabled = !isBusy && status.mode == GadgetMode.Pxe) {
                    Text("Stop PXE")
                }
            }
            AssistChip(
                onClick = {},
                label = { Text(if (status.hostConnected) "Host connected" else "Host disconnected") }
            )
        }
    }
}

@Composable
private fun DiskImageCard(
    image: DiskImageUiModel,
    formattedSize: String,
    onMount: () -> Unit,
    onUnmount: () -> Unit,
    onToggleWritable: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = image.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "$formattedSize • ${image.filesystem} • ${if (image.bootable) "Bootable" else "Non-bootable"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            image.notes?.let {
                AssistChip(
                    onClick = {},
                    leadingIcon = { Icon(imageVector = Icons.Outlined.Layers, contentDescription = null) },
                    label = { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(
                    checked = image.writable,
                    onCheckedChange = { onToggleWritable() },
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                )
                Text(text = if (image.writable) "Writable overlay" else "Read-only")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (image.mounted) {
                    OutlinedButton(onClick = onUnmount) {
                        Text("Unmount")
                    }
                } else {
                    Button(onClick = onMount) {
                        Text("Mount")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(onAddSampleImage: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = Icons.Filled.Usb, contentDescription = null)
            Text(text = "No images yet", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Import or create an image to start using oLinky as your pocket boot drive.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onAddSampleImage) {
                Text("Import image")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OverviewScreenPreview() {
    val previewState = OverviewUiState(
        images = listOf(
            DiskImageUiModel(
                id = "1",
                displayName = "Ubuntu 24.04 LTS",
                sizeBytes = 4_500_000_000L,
                filesystem = "ISO9660",
                bootable = true,
                mounted = true,
                writable = false,
                notes = "Latest desktop"
            ),
            DiskImageUiModel(
                id = "2",
                displayName = "Rescue Toolkit",
                sizeBytes = 1_200_000_000L,
                filesystem = "ext4",
                bootable = true,
                mounted = false,
                writable = true,
                notes = "Diagnostics"
            )
        ),
        selectedImageId = "1",
        gadgetStatus = GadgetStatusUiModel(
            mode = GadgetMode.MassStorage,
            connectionLabel = "Mass storage gadget active",
            hostConnected = true,
            lastOperation = "Mounted Ubuntu 24.04 LTS (RO)"
        )
    )

    OLinkyAppTheme {
        OverviewScreen(
            state = previewState,
            onMountImage = { _, _ -> },
            onUnmountImage = {},
            onToggleWritable = {},
            onStartPxe = {},
            onStopPxe = {},
            onAddSampleImage = {},
            onRefreshStatus = {},
            onClearErrors = {}
        )
    }
}
