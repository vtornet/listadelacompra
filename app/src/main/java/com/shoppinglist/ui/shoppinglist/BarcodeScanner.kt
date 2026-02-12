@file:OptIn(androidx.camera.core.ExperimentalGetImage::class)

package com.shoppinglist.ui.shoppinglist

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun BarcodeScannerDialog(
    onDismiss: () -> Unit,
    onBarcode: (String) -> Unit
) {
    FullScreenCamera(
        onClose = onDismiss,
        onDetected = { value ->
            if (value.isNotBlank()) onBarcode(value)
        }
    )
}

@Composable
private fun FullScreenCamera(
    onClose: () -> Unit,
    onDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    // Executor para el analizador
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    // Scanner ML Kit: EAN/UPC/1D + QR opcional
    val scannerOptions = remember {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_QR_CODE // opcional; quítalo si no lo quieres
            )
            .build()
    }
    val scanner = remember { BarcodeScanning.getClient(scannerOptions) }

    // Flag para emitir una sola vez
    var emitted by remember { mutableStateOf(false) }

    // Limpiar recursos (executor y scanner) cuando se dispose el composable
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            runCatching { scanner.close() }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // Vista de la cámara
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                    bindCameraUseCases(
                        ctx = ctx,
                        lifecycle = lifecycleOwner,
                        previewView = previewView,
                        onCameraReady = { camera = it },
                        analyzer = { imageProxy -> analyzeImage(imageProxy, scanner) { code ->
                            if (!emitted) {
                                emitted = true
                                onDetected(code)
                                // Cerrar al detectar
                                onClose()
                            }
                        } },
                        executor = cameraExecutor
                    )
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Barra superior con cerrar / flash
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        camera?.let { cam ->
                            val hasTorch = cam.cameraInfo.hasFlashUnit()
                            if (hasTorch) {
                                torchOn = !torchOn
                                runCatching { cam.cameraControl.enableTorch(torchOn) }
                            }
                        }
                    }) {
                        Icon(
                            if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                            contentDescription = "Linterna",
                            tint = Color.White
                        )
                    }
                }
            }

            // Guía/instrucción
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(12.dp)
            ) {
                Text(
                    "Apunta al código de barras para escanear",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

private fun bindCameraUseCases(
    ctx: Context,
    lifecycle: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    onCameraReady: (Camera) -> Unit,
    analyzer: (ImageProxy) -> Unit,
    executor: ExecutorService
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(executor) { image -> analyzer(image) }
            }

        val selector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                lifecycle, selector, preview, analysis
            )
            onCameraReady(camera)
        } catch (_: Exception) {
            // Puedes loguear si quieres
        }
    }, ContextCompat.getMainExecutor(ctx))
}

@SuppressLint("UnsafeOptInUsageError")
private fun analyzeImage(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onFirstCode: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(input)
        .addOnSuccessListener { barcodes ->
            val value = barcodes.firstOrNull { it.rawValue != null }?.rawValue
            if (value != null) onFirstCode(value)
        }
        .addOnFailureListener {
            // Ignora, seguimos analizando frames
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
