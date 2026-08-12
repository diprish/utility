package com.diprish.utilitymeter.ui.reading

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.diprish.utilitymeter.ocr.CaptureReticle
import java.io.File
import java.util.concurrent.Executor

private const val TAG = "CameraCapture"

/**
 * Full-bleed camera preview with a shutter button. On capture the frame is
 * written to a JPEG in the app's private storage and [onImageCaptured] is
 * called with the file.
 */
@Composable
fun CameraCapture(
    outputDirectory: File,
    onImageCaptured: (File) -> Unit,
    onError: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { ContextCompat.getMainExecutor(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    Box(modifier = modifier.clip(RoundedCornerShape(16.dp))) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                bindCameraUseCases(ctx, previewView, lifecycleOwner, imageCapture, executor, onError)
                previewView
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Alignment reticle: everything outside the box is dimmed, and only
        // this framed region is read by OCR.
        ReticleOverlay(modifier = Modifier.fillMaxSize())

        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            contentColor = Color.White,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp),
        ) {
            Text(
                "Line up the meter's digits inside the box",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        FloatingActionButton(
            onClick = {
                capturePhoto(context, outputDirectory, imageCapture, executor, onImageCaptured, onError)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = "Capture reading")
        }
    }
}

@Composable
private fun ReticleOverlay(modifier: Modifier = Modifier) {
    val scrim = Color.Black.copy(alpha = 0.5f)
    Canvas(modifier = modifier) {
        val left = size.width * CaptureReticle.LEFT
        val top = size.height * CaptureReticle.TOP
        val right = size.width * CaptureReticle.RIGHT
        val bottom = size.height * CaptureReticle.BOTTOM
        val boxWidth = right - left
        val boxHeight = bottom - top

        // Dim the four regions around the target box.
        drawRect(scrim, topLeft = Offset(0f, 0f), size = Size(size.width, top))
        drawRect(scrim, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
        drawRect(scrim, topLeft = Offset(0f, top), size = Size(left, boxHeight))
        drawRect(scrim, topLeft = Offset(right, top), size = Size(size.width - right, boxHeight))

        // Bright border around the target box.
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(boxWidth, boxHeight),
            cornerRadius = CornerRadius(12f, 12f),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

private fun bindCameraUseCases(
    context: Context,
    previewView: PreviewView,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    imageCapture: ImageCapture,
    executor: Executor,
    onError: (Throwable) -> Unit,
) {
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({
        try {
            val cameraProvider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to bind camera use cases", t)
            onError(t)
        }
    }, executor)
}

private fun capturePhoto(
    context: Context,
    outputDirectory: File,
    imageCapture: ImageCapture,
    executor: Executor,
    onImageCaptured: (File) -> Unit,
    onError: (Throwable) -> Unit,
) {
    if (!outputDirectory.exists()) outputDirectory.mkdirs()
    val photoFile = File(outputDirectory, "reading_${System.currentTimeMillis()}.jpg")
    val options = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        options,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onImageCaptured(photoFile)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed", exception)
                onError(exception)
            }
        },
    )
}
