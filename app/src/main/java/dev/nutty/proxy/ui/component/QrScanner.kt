package dev.nutty.proxy.ui.component

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** CameraX + ML Kit scanner. Pairing payloads are parsed by the screen, never trusted here. */
@Composable
fun QrScanner(
    modifier: Modifier = Modifier,
    onPayload: (String) -> Unit,
    onUnavailable: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        if (!it) onUnavailable()
    }
    if (!granted) {
        // Permission requests and screen state changes must happen after this
        // composition pass. Calling either directly here can recompose forever.
        LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.CAMERA) }
        return
    }

    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    val active = remember { AtomicBoolean(true) }
    val delivered = remember { AtomicBoolean(false) }
    var provider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    var analysis: ImageAnalysis? by remember { mutableStateOf(null) }
    DisposableEffect(Unit) {
        onDispose {
            // Navigation happens as soon as a QR is recognized. CameraX can
            // still have one image queued at that point, so stop analysis
            // before closing ML Kit and ignore every late callback.
            active.set(false)
            analysis?.clearAnalyzer()
            provider?.unbindAll()
            scanner.close()
            executor.shutdownNow()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).also { previewView ->
                ProcessCameraProvider.getInstance(viewContext).addListener({
                    if (!active.get()) return@addListener
                    val cameraProvider = ProcessCameraProvider.getInstance(viewContext).get()
                    provider = cameraProvider
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis = imageAnalysis
                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                        if (!active.get()) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        val image = imageProxy.image
                        if (image == null) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        runCatching {
                            scanner.process(InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees))
                                .addOnSuccessListener { codes ->
                                    val payload = codes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue
                                    if (payload != null && active.get() && delivered.compareAndSet(false, true)) onPayload(payload)
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        }.onFailure { imageProxy.close() }
                    }
                    runCatching {
                        cameraProvider.unbindAll()
                        if (active.get()) cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                    }.onFailure { onUnavailable() }
                }, ContextCompat.getMainExecutor(viewContext))
            }
        },
    )
}
