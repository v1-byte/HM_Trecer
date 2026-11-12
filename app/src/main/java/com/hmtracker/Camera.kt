package com.hmtracker

import android.content.Context
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Status kamera yang dipakai bersama antara preview dan tombol di layar. */
class LiveCameraState {
    var imageCapture by mutableStateOf<ImageCapture?>(null)
    var camera by mutableStateOf<Camera?>(null)
    var zoomMin by mutableStateOf(1f)
    var zoomMax by mutableStateOf(1f)
}

private suspend fun Context.cameraProvider(): ProcessCameraProvider = suspendCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(this))
}

/**
 * Preview kamera belakang langsung di layar.
 * hd = resolusi tertinggi, jika tidak resolusi 720p. torch = senter. zoom = rasio zoom.
 */
@Composable
fun LiveCameraPreview(
    state: LiveCameraState,
    hd: Boolean,
    torch: Boolean,
    zoom: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    // Pasang ulang kamera setiap HD berubah
    LaunchedEffect(hd) {
        val provider = context.cameraProvider()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val strategy = if (hd) {
            ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY
        } else {
            ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
        }
        val capture = ImageCapture.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(strategy).build())
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        provider.unbindAll()
        val cam = provider.bindToLifecycle(
            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture
        )
        state.imageCapture = capture
        state.camera = cam
        cam.cameraInfo.zoomState.observe(lifecycleOwner) { z ->
            state.zoomMin = z.minZoomRatio
            state.zoomMax = z.maxZoomRatio
        }
    }

    LaunchedEffect(state.camera, torch) {
        state.camera?.cameraControl?.enableTorch(torch)
    }

    LaunchedEffect(state.camera, zoom, state.zoomMin, state.zoomMax) {
        state.camera?.cameraControl?.setZoomRatio(zoom.coerceIn(state.zoomMin, state.zoomMax))
    }

    AndroidView(factory = { previewView }, modifier = modifier.fillMaxSize())
}

/** Mengambil foto dari preview kamera dan menyimpannya ke file. */
fun captureLivePhoto(
    context: Context,
    state: LiveCameraState,
    file: File,
    onSaved: () -> Unit,
    onFailed: (String) -> Unit
) {
    val capture = state.imageCapture
    if (capture == null) {
        onFailed("Kamera belum siap. Tunggu sebentar lalu coba lagi.")
        return
    }
    file.parentFile?.mkdirs()
    file.delete()
    val options = ImageCapture.OutputFileOptions.Builder(file).build()
    capture.takePicture(
        options,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                if (file.exists() && file.length() > 0) onSaved()
                else onFailed("Foto kosong, coba lagi.")
            }

            override fun onError(exception: ImageCaptureException) {
                onFailed("Gagal mengambil foto: ${exception.message}")
            }
        }
    )
}
