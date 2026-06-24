package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: PriceTrackerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanMode by viewModel.scanMode.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (scanMode == ScanMode.RECEIPT) "Scan Receipt" else "Scan Barcode") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(AppScreen.DASHBOARD) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                // Real CameraX preview container
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }

                            // Silently bind analysis so the system builds and is ready for real inputs
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (exc: Exception) {
                                exc.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Camera permission is required for real scanning. Use the Simulator Controls below to test full receipt parse operations on this emulator.",
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Mode Selector Toggle (HUD overlay)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top controls (Toggles)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.width(300.dp)
                    ) {
                        SegmentedButton(
                            selected = scanMode == ScanMode.RECEIPT,
                            onClick = { viewModel.setScanMode(ScanMode.RECEIPT) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            modifier = Modifier.testTag("mode_receipt_toggle")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Receipt OCR", fontSize = 12.sp)
                            }
                        }
                        SegmentedButton(
                            selected = scanMode == ScanMode.BARCODE,
                            onClick = { viewModel.setScanMode(ScanMode.BARCODE) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            modifier = Modifier.testTag("mode_barcode_toggle")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Barcode Scanner", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Middle scanning viewfinder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(horizontal = 32.dp)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(Color.Red.copy(alpha = 0.6f))
                    )
                    Text(
                        if (scanMode == ScanMode.RECEIPT) "Align receipt items inside box" else "Place barcode inside scanner frame",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                    )
                }

                // Bottom: Beautiful Simulator Dashboard Panel
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Simulator Control Console",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Select a pre-configured template below to simulate perfect ML Kit receipt scanning and barcode OCR parser results.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (scanMode == ScanMode.RECEIPT) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.processOcrResult(
                                            merchantName = "Costco - North Seattle",
                                            lines = listOf(
                                                "COKE 1.5L @ 2.49",
                                                "ORGANIC MILK 1L @ 3.99",
                                                "PREMIUM COFFEE BEANS 500G @ 12.99"
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("simulate_costco_button"),
                                    contentPadding = PaddingValues(vertical = 10.dp)
                                ) {
                                    Text("Costco OCR", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        viewModel.processOcrResult(
                                            merchantName = "Trader Joe's - Capitol Hill",
                                            lines = listOf(
                                                "GREEK YOGURT 907G @ 6.49",
                                                "SPARKLING WATER 12PCS @ 5.99",
                                                "ORGANIC HONEY @ 8.99" // Honey has null units to test correction form!
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("simulate_tj_button"),
                                    contentPadding = PaddingValues(vertical = 10.dp)
                                ) {
                                    Text("Trader Joe's", fontSize = 11.sp)
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.processBarcodeResult("049000028913") }, // Coke
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("simulate_coke_barcode"),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                    ) {
                                        Text("Coke (049000028913)", fontSize = 11.sp)
                                    }
                                    
                                    Button(
                                        onClick = { viewModel.processBarcodeResult("074238321041") }, // Whole Milk
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("simulate_milk_barcode"),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                    ) {
                                        Text("Milk (074238321041)", fontSize = 11.sp)
                                    }
                                }

                                Button(
                                    onClick = { viewModel.processBarcodeResult("999128374921") }, // New Barcode
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("simulate_new_barcode"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                ) {
                                    Text("Scan Brand New Barcode: 999128374921", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
