package com.shoppinglist.ui.shoppinglist

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import androidx.compose.material3.ExperimentalMaterial3Api

/**
 * Overlay de escáner con CameraX (controller) + ML Kit.
 * Detecta EAN-13, EAN-8, UPC-A, UPC-E, Code128.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerOverlay(
    onResult: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA)
    }

    // ML Kit: sólo formatos que nos interesan
    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128
            )
            .build()
        BarcodeScanning.getClient(options)
    }

    // Controller de CameraX (no usa ListenableFuture)
    val controller = remember {
        LifecycleCameraController(context).apply {
            // La preview va implícita; habilitamos análisis
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        }
    }

    // Configuramos el analizador de frames
    LaunchedEffect(scanner) {
        controller.setImageAnalysisAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val img = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(img)
                    .addOnSuccessListener { list ->
                        val code = list.firstOrNull()?.rawValue
                        if (!code.isNullOrBlank()) {
                            // Detenemos el análisis para no duplicar eventos
                            controller.clearImageAnalysisAnalyzer()
                            onResult(code)
                            onClose()
                        }
                    }
                    .addOnCompleteListener { imageProxy.close() }
            } else {
                imageProxy.close()
            }
        }
    }

    // Enlazamos al ciclo de vida cuando haya permiso
    LaunchedEffect(hasPermission) {
        if (hasPermission) controller.bindToLifecycle(lifecycleOwner)
    }

    // UI overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
    ) {
        if (!hasPermission) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Se necesita permiso de cámara para escanear.", color = Color.White)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) { Text("Conceder permiso") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onClose) { Text("Cancelar") }
            }
        } else {
            // Vista de la cámara
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        this.scaleType = PreviewView.ScaleType.FILL_CENTER
                        this.controller = controller
                    }
                }
            )

            // Barra superior (simple y estable)
            TopAppBar(
                title = { Text("Escanear código", color = Color.White) },
                navigationIcon = {
                    OutlinedButton(onClick = onClose, modifier = Modifier.padding(start = 8.dp)) {
                        Text("Cerrar")
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.35f))
            )
        }
    }
}
