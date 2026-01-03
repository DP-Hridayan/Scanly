package com.skeler.scanely.ui.screens

import android.Manifest
import android.content.ClipData
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.skeler.scanely.core.actions.ScanAction
import com.skeler.scanely.core.actions.ScanActionDetector
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.navigation.Routes
import com.skeler.scanely.ocr.OcrResult
import com.skeler.scanely.scanner.BarcodeResult
import com.skeler.scanely.scanner.BarcodeScanner
import com.skeler.scanely.ui.ScanViewModel
import com.skeler.scanely.ui.components.ScanActionsRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

private const val TAG = "BarcodeScannerScreen"

// Cooldown between scans to prevent duplicates (ms)
private const val SCAN_COOLDOWN_MS = 1500L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun BarcodeScannerScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val scanViewModel: ScanViewModel = hiltViewModel(activity)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    var scannedResults by remember { mutableStateOf<List<BarcodeResult>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }
    
    // Track last scan time for cooldown
    var lastScanTime by remember { mutableLongStateOf(0L) }

    // De-duplication set to avoid saving the same barcode continuously
    val previouslyScanned = remember { mutableSetOf<String>() }

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Barcode Scanner") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (cameraPermission.status.isGranted) {
                // Camera Preview with Scanner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    CameraPreviewWithScanner(
                        onBarcodeDetected = { results ->
                            val currentTime = System.currentTimeMillis()
                            
                            // Apply cooldown to prevent rapid duplicate scans
                            if (results.isNotEmpty() && isScanning && 
                                (currentTime - lastScanTime) > SCAN_COOLDOWN_MS) {
                                
                                scannedResults = results
                                lastScanTime = currentTime

                                // Check for new barcodes to save
                                results.forEach { result ->
                                    val key = "${result.formatName}|${result.rawValue}"
                                    if (!previouslyScanned.contains(key)) {
                                        previouslyScanned.add(key)
                                        // Save to history
                                        val ocrResult = OcrResult(
                                            text = result.displayValue,
                                            confidence = 100,
                                            languages = listOf("Barcode: ${result.formatName}"),
                                            processingTimeMs = 0
                                        )
                                        scanViewModel.onBarcodeScanned(ocrResult)
                                        navController.navigate(Routes.RESULTS)
                                    }
                                }
                            }
                        }
                    )

                    // Scan Frame Overlay
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(250.dp)
                                .border(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(16.dp)
                                )
                        )
                    }

                    // Hint text
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 24.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = "Point camera at a barcode or QR code",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier
                                .background(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                // Results Section with Smart Actions
                AnimatedVisibility(
                    visible = scannedResults.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(scannedResults) { result ->
                            BarcodeResultCard(result = result)
                        }
                    }
                }
            } else {
                // Permission not granted
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Camera permission required",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewWithScanner(
    onBarcodeDetected: (List<BarcodeResult>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { previewView ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            // Optimized image analysis for barcode scanning
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(android.util.Size(1280, 720)) // 720p for balance
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(executor) { imageProxy ->
                        scope.launch {
                            processImageProxy(imageProxy, onBarcodeDetected)
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
}

private suspend fun processImageProxy(
    imageProxy: ImageProxy,
    onBarcodeDetected: (List<BarcodeResult>) -> Unit
) {
    try {
        val bitmap = imageProxy.toBitmap()
        val results = BarcodeScanner.scanBitmap(bitmap)

        withContext(Dispatchers.Main) {
            onBarcodeDetected(results)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error processing image", e)
    } finally {
        imageProxy.close()
    }
}

/**
 * Enhanced BarcodeResultCard with smart action detection.
 * Displays barcode content and provides actionable buttons based on content type.
 */
@Composable
private fun BarcodeResultCard(
    result: BarcodeResult
) {
    // Detect actions from barcode content using ML Kit typed data
    val actions = remember(result) {
        ScanActionDetector.detectActions(
            text = result.rawValue,
            valueType = result.valueType,
            urlData = result.urlData,
            wifiData = result.wifiData,
            emailData = result.emailData,
            phoneData = result.phoneData,
            smsData = result.smsData,
            contactInfo = result.contactInfo
        )
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Format label
            Text(
                text = result.formatName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Content preview
            Text(
                text = result.displayValue,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 3
            )
            
            // Smart Actions Row
            if (actions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                ScanActionsRow(actions = actions)
            }
        }
    }
}
