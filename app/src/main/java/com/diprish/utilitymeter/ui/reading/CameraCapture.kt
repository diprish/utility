package com.diprish.utilitymeter.ui.reading

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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
