package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NavyPrimary
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun rememberBarcodeScanRequester(
    onToast: (String) -> Unit,
    onBarcodeScanned: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    var showScanner by remember { mutableStateOf(false) }
    val latestOnScanned by rememberUpdatedState(onBarcodeScanned)
    val latestOnToast by rememberUpdatedState(onToast)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showScanner = true
        } else {
            latestOnToast("يرجى السماح بصلاحية الكاميرا لمسح الباركود")
        }
    }

    if (showScanner) {
        BarcodeScannerDialog(
            onBarcodeScanned = { code ->
                showScanner = false
                latestOnScanned(code)
            },
            onDismiss = { showScanner = false },
            onError = { msg ->
                showScanner = false
                latestOnToast(msg)
            }
        )
    }

    return {
        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCamera) {
            showScanner = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

@Composable
fun BarcodeScannerDialog(
    onBarcodeScanned: (String) -> Unit,
    onDismiss: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnScanned by rememberUpdatedState(onBarcodeScanned)
    val latestOnError by rememberUpdatedState(onError)

    var torchEnabled by remember { mutableStateOf(false) }
    var initError by remember { mutableStateOf<String?>(null) }
    val delivered = remember { AtomicBoolean(false) }

    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        BarcodeScanning.getClient(options)
    }

    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        }
    }

    DisposableEffect(lifecycleOwner, cameraController, barcodeScanner) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        try {
            cameraController.setImageAnalysisAnalyzer(
                mainExecutor,
                MlKitAnalyzer(
                    listOf(barcodeScanner),
                    CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED,
                    mainExecutor
                ) { result ->
                    if (delivered.get()) return@MlKitAnalyzer
                    val barcodes = result?.getValue(barcodeScanner) ?: return@MlKitAnalyzer
                    val raw = barcodes.firstNotNullOfOrNull { it.rawValue?.trim() }
                        ?.takeIf { it.isNotBlank() }
                    if (raw != null && delivered.compareAndSet(false, true)) {
                        latestOnScanned(raw)
                    }
                }
            )
            cameraController.bindToLifecycle(lifecycleOwner)
        } catch (e: Exception) {
            initError = "تعذر تشغيل الكاميرا على هذا الجهاز"
        }

        onDispose {
            try {
                cameraController.clearImageAnalysisAnalyzer()
                cameraController.unbind()
            } catch (_: Exception) {
            }
            try {
                barcodeScanner.close()
            } catch (_: Exception) {
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler(onBack = onDismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (initError == null) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            controller = cameraController
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = initError.orEmpty(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Center scanner reticle frame
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 270.dp, height = 170.dp)
                    .border(2.5.dp, GoldAccent, RoundedCornerShape(20.dp))
            )

            // Top controls (Close + Torch)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = NavyPrimary.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.6f))
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("scanner_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_close),
                            tint = Color.White
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = NavyPrimary.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "مسح الباركود",
                        color = GoldAccent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = NavyPrimary.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.6f))
                ) {
                    IconButton(
                        onClick = {
                            val next = !torchEnabled
                            torchEnabled = next
                            try {
                                cameraController.enableTorch(next)
                            } catch (_: Exception) {
                                latestOnError("الفلاش غير متاح")
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("scanner_flash_button")
                    ) {
                        Icon(
                            imageVector = if (torchEnabled) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                            contentDescription = stringResource(R.string.cd_toggle_flash),
                            tint = if (torchEnabled) GoldAccent else Color.White
                        )
                    }
                }
            }

            // Bottom helper hint
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = NavyPrimary.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, GoldAccent),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = 32.dp, start = 20.dp, end = 20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = GoldAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "وجّه الكاميرا نحو الباركود للقراءة التلقائية",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
