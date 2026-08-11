package com.diprish.utilitymeter.ui.reading

import android.Manifest
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.diprish.utilitymeter.ui.formatNumber
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AddReadingScreen(
    onDone: () -> Unit,
    viewModel: AddReadingViewModel = viewModel(factory = AddReadingViewModel.Factory),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    // When true we hide the camera and let the user type the reading directly.
    var manualEntry by remember { mutableStateOf(false) }
    val photoDir = remember { File(context.filesDir, "meter_photos") }

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(if (state.meterName.isBlank()) "New reading" else "Reading · ${state.meterName}") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- Capture area ----
            when {
                state.photoPath != null -> {
                    CapturedPhoto(
                        path = state.photoPath!!,
                        onRetake = viewModel::retakePhoto,
                    )
                }
                manualEntry -> {
                    OutlinedButton(
                        onClick = { manualEntry = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Use camera instead") }
                }
                cameraPermission.status.isGranted -> {
                    CameraCapture(
                        outputDirectory = photoDir,
                        onImageCaptured = { file -> viewModel.onPhotoCaptured(context, file) },
                        onError = { manualEntry = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f),
                    )
                    TextButton(
                        onClick = { manualEntry = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) { Text("Enter reading manually") }
                }
                else -> {
                    CameraPermissionPrompt(
                        onGrant = { cameraPermission.launchPermissionRequest() },
                        onManual = { manualEntry = true },
                    )
                }
            }

            // ---- OCR status ----
            if (state.ocrRunning) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reading the meter…", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            state.ocrMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // ---- Value entry ----
            OutlinedTextField(
                value = state.valueText,
                onValueChange = viewModel::onValueChange,
                label = { Text(if (state.unit.isBlank()) "Reading" else "Reading (${state.unit})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            state.previousValue?.let { previous ->
                val delta = state.parsedValue?.let { it - previous }
                val hint = buildString {
                    append("Previous: ${formatNumber(previous)} ${state.unit}")
                    if (delta != null) {
                        append(if (delta >= 0) " · +${formatNumber(delta)} used" else " · check value (lower than last)")
                    }
                }
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (delta != null && delta < 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text("Save reading")
            }
        }
    }
}

@Composable
private fun CapturedPhoto(path: String, onRetake: () -> Unit) {
    val bitmap = remember(path) { android.graphics.BitmapFactory.decodeFile(path) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Captured meter photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        IconButton(
            onClick = onRetake,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Retake photo",
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun CameraPermissionPrompt(
    onGrant: () -> Unit,
    onManual: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Allow camera access to snap a photo of the meter and read it automatically.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
            Text("Allow camera")
        }
        TextButton(onClick = onManual) {
            Text("Enter reading manually")
        }
    }
}
