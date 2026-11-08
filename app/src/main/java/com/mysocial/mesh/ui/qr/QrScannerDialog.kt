package com.mysocial.mesh.ui.qr

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mysocial.mesh.ui.theme.FacebookBlue
import com.mysocial.mesh.ui.theme.FbBackground
import com.mysocial.mesh.ui.theme.FbBorder
import com.mysocial.mesh.ui.theme.FbSurface
import com.mysocial.mesh.ui.theme.FbTextPrimary
import com.mysocial.mesh.ui.theme.FbTextSecondary
import java.util.concurrent.Executors

@Composable
fun QrScannerDialog(
    onDismiss: () -> Unit,
    onQrScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var showManualInputDialog by remember { mutableStateOf(false) }
    var manualInputText by remember { mutableStateOf("") }
    var torchEnabled by remember { mutableStateOf(false) }
    var cameraControl: androidx.camera.core.CameraControl? by remember { mutableStateOf(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(
                                        cameraExecutor,
                                        QrCodeAnalyzer { resultText ->
                                            onQrScanned(resultText)
                                        }
                                    )
                                }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                val camera = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                                cameraControl = camera.cameraControl
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    }
                )

                DisposableEffect(Unit) {
                    onDispose {
                        cameraExecutor.shutdown()
                    }
                }

                // Overlay with Viewfinder cutout and animated scan line
                val infiniteTransition = rememberInfiniteTransition(label = "scan_laser")
                val scanLineProgress by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2200, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scan_line"
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val boxSize = size.minDimension * 0.70f
                    val left = (size.width - boxSize) / 2f
                    val top = (size.height - boxSize) / 2.2f

                    // Semi-transparent dark overlay around the scanner window
                    drawRect(
                        color = Color.Black.copy(alpha = 0.55f)
                    )

                    // Clear the viewfinder window
                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = Offset(left, top),
                        size = Size(boxSize, boxSize),
                        cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                        blendMode = BlendMode.Clear
                    )

                    // Border around the viewfinder
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.7f),
                        topLeft = Offset(left, top),
                        size = Size(boxSize, boxSize),
                        cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Corner brackets
                    val cornerLength = 32.dp.toPx()
                    val strokeWidth = 4.dp.toPx()
                    val cornerColor = Color(0xFF1877F2)

                    // Top-Left
                    drawLine(cornerColor, Offset(left - 2, top), Offset(left + cornerLength, top), strokeWidth)
                    drawLine(cornerColor, Offset(left, top - 2), Offset(left, top + cornerLength), strokeWidth)

                    // Top-Right
                    drawLine(cornerColor, Offset(left + boxSize - cornerLength, top), Offset(left + boxSize + 2, top), strokeWidth)
                    drawLine(cornerColor, Offset(left + boxSize, top - 2), Offset(left + boxSize, top + cornerLength), strokeWidth)

                    // Bottom-Left
                    drawLine(cornerColor, Offset(left - 2, top + boxSize), Offset(left + cornerLength, top + boxSize), strokeWidth)
                    drawLine(cornerColor, Offset(left, top + boxSize - cornerLength), Offset(left, top + boxSize + 2), strokeWidth)

                    // Bottom-Right
                    drawLine(cornerColor, Offset(left + boxSize - cornerLength, top + boxSize), Offset(left + boxSize + 2, top + boxSize), strokeWidth)
                    drawLine(cornerColor, Offset(left + boxSize, top + boxSize - cornerLength), Offset(left + boxSize, top + boxSize + 2), strokeWidth)

                    // Animated horizontal laser line
                    val lineY = top + (boxSize * scanLineProgress)
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color(0x001877F2),
                                Color(0xFF1877F2),
                                Color(0xFF42B72A),
                                Color(0xFF1877F2),
                                Color(0x001877F2)
                            ),
                            startX = left,
                            endX = left + boxSize
                        ),
                        start = Offset(left + 8.dp.toPx(), lineY),
                        end = Offset(left + boxSize - 8.dp.toPx(), lineY),
                        strokeWidth = 3.dp.toPx()
                    )
                }

            } else {
                // Permission Denied / Pending UI
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Camera Permission Required",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Camera access is needed to scan your friend's QR code without internet.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue)
                    ) {
                        Text("Grant Permission", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Top Header: Close button, title, and torch toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }

                Text(
                    text = "Scan Friend's QR Code",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                IconButton(
                    onClick = {
                        torchEnabled = !torchEnabled
                        cameraControl?.enableTorch(torchEnabled)
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = if (torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = "Flashlight",
                        tint = if (torchEnabled) Color(0xFFF7B125) else Color.White
                    )
                }
            }

            // Bottom Instructions & Manual Friend Code Button
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Align your friend's QR code inside the frame",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { showManualInputDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Filled.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enter Friend Code Manually", fontSize = 13.sp)
                }
            }
        }

        // Manual Input Dialog
        if (showManualInputDialog) {
            AlertDialog(
                onDismissRequest = { showManualInputDialog = false },
                containerColor = FbSurface,
                title = { Text("Enter Friend Code", color = FbTextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            text = "Paste your friend's account ID or invite link:",
                            color = FbTextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = manualInputText,
                            onValueChange = { manualInputText = it },
                            placeholder = { Text("e.g. @ab12cd... or mysocial://follow?authorId=...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = FbTextPrimary,
                                unfocusedTextColor = FbTextPrimary,
                                focusedBorderColor = FacebookBlue,
                                unfocusedBorderColor = FbBorder
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val code = manualInputText.trim()
                            if (code.isNotBlank()) {
                                showManualInputDialog = false
                                onQrScanned(code)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue),
                        enabled = manualInputText.isNotBlank()
                    ) {
                        Text("Connect")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualInputDialog = false }) {
                        Text("Cancel", color = FbTextSecondary)
                    }
                }
            )
        }
    }
}
