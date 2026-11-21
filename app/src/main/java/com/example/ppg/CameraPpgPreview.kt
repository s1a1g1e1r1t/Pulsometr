package com.example.ppg

import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import java.util.concurrent.Executors

@Composable
fun CameraPpgPreview(
    isScanning: Boolean,
    torchEnabled: Boolean,
    onFrameSample: (red: Double, green: Double, blue: Double, timestampMs: Long) -> Unit,
    onCameraError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrameSample by rememberUpdatedState(onFrameSample)
    val currentOnCameraError by rememberUpdatedState(onCameraError)
    val previewDescription = context.getString(R.string.cd_camera_preview)

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(boundCamera, torchEnabled, isScanning) {
        val cam = boundCamera
        if (cam != null && cam.cameraInfo.hasFlashUnit()) {
            cam.cameraControl.enableTorch(isScanning && torchEnabled)
        }
        onDispose {
            if (cam != null && cam.cameraInfo.hasFlashUnit()) {
                cam.cameraControl.enableTorch(false)
            }
        }
    }

    DisposableEffect(isScanning, lifecycleOwner) {
        if (!isScanning) {
            boundCamera = null
            onDispose {}
        } else {
            val analyzerExecutor = Executors.newSingleThreadExecutor()
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            var activeProvider: ProcessCameraProvider? = null

            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    activeProvider = cameraProvider

                    val preview = Preview.Builder()
                        .build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }

                    val resolutionSelector = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(320, 240),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            )
                        )
                        .build()

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                        try {
                            val rgb = extractCenterRgbFromYuv(imageProxy)
                            currentOnFrameSample(rgb[0], rgb[1], rgb[2], System.currentTimeMillis())
                        } catch (e: Exception) {
                            Log.w("CameraPpg", "Frame analysis skipped: ${e.message}")
                        } finally {
                            imageProxy.close()
                        }
                    }

                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis,
                    )
                    boundCamera = camera
                    if (camera.cameraInfo.hasFlashUnit()) {
                        camera.cameraControl.enableTorch(torchEnabled)
                    }
                } catch (e: Exception) {
                    Log.e("CameraPpg", "Failed to bind CameraX use cases", e)
                    currentOnCameraError(e.localizedMessage ?: "Не вдалося запустити камеру")
                }
            }, ContextCompat.getMainExecutor(context))

            onDispose {
                try {
                    boundCamera?.let {
                        if (it.cameraInfo.hasFlashUnit()) {
                            it.cameraControl.enableTorch(false)
                        }
                    }
                    activeProvider?.unbindAll()
                } catch (e: Exception) {
                    Log.w("CameraPpg", "Error unbinding camera: ${e.message}")
                }
                boundCamera = null
                analyzerExecutor.shutdown()
            }
        }
    }

    Box(modifier = modifier.semantics { contentDescription = previewDescription }) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun extractCenterRgbFromYuv(imageProxy: ImageProxy): DoubleArray {
    val planes = imageProxy.planes
    if (planes.size < 3) return doubleArrayOf(0.0, 0.0, 0.0)

    val yPlane = planes[0]
    val uPlane = planes[1]
    val vPlane = planes[2]

    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer

    val yRowStride = yPlane.rowStride
    val yPixelStride = yPlane.pixelStride
    val uRowStride = uPlane.rowStride
    val uPixelStride = uPlane.pixelStride
    val vRowStride = vPlane.rowStride
    val vPixelStride = vPlane.pixelStride

    val width = imageProxy.width
    val height = imageProxy.height

    val startX = width / 4
    val endX = (width * 3) / 4
    val startY = height / 4
    val endY = (height * 3) / 4

    var sumR = 0.0
    var sumG = 0.0
    var sumB = 0.0
    var count = 0

    var y = startY
    while (y < endY) {
        val uvRow = y / 2
        var x = startX
        while (x < endX) {
            val uvCol = x / 2
            val yIdx = y * yRowStride + x * yPixelStride
            val uIdx = uvRow * uRowStride + uvCol * uPixelStride
            val vIdx = uvRow * vRowStride + uvCol * vPixelStride

            if (yIdx < yBuffer.limit() && uIdx < uBuffer.limit() && vIdx < vBuffer.limit()) {
                val yVal = yBuffer.get(yIdx).toInt() and 0xFF
                val uVal = (uBuffer.get(uIdx).toInt() and 0xFF) - 128
                val vVal = (vBuffer.get(vIdx).toInt() and 0xFF) - 128

                val r = (yVal + 1.402 * vVal).coerceIn(0.0, 255.0)
                val g = (yVal - 0.344136 * uVal - 0.714136 * vVal).coerceIn(0.0, 255.0)
                val b = (yVal + 1.772 * uVal).coerceIn(0.0, 255.0)

                sumR += r
                sumG += g
                sumB += b
                count++
            }
            x += 4
        }
        y += 4
    }

    if (count == 0) return doubleArrayOf(0.0, 0.0, 0.0)
    return doubleArrayOf(sumR / count, sumG / count, sumB / count)
}
