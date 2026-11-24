package com.regiaobs.app.ui.connect

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.regiaobs.app.obs.ObsServerProfile
import com.regiaobs.app.obs.ObsUriParser
import com.regiaobs.app.ui.theme.StudioColors
import java.util.concurrent.Executors

@Composable
fun QrScannerDialog(
    onProfileScanned: (ObsServerProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var scanHint by remember { mutableStateOf("Point at the QR Code in OBS → Tools → WebSocket Server Settings") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = StudioColors.Panel,
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioColors.Border)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            tint = StudioColors.ReplayCyan
                        )
                        Text(
                            text = "Scan OBS QR",
                            style = MaterialTheme.typography.titleMedium,
                            color = StudioColors.TextPrimary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = StudioColors.TextSecondary
                        )
                    }
                }

                if (hasCameraPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .border(1.dp, StudioColors.Border, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CameraQrPreview(
                            onRawCode = { raw ->
                                val parsed = ObsUriParser.parse(raw)
                                if (parsed != null) {
                                    onProfileScanned(parsed)
                                } else {
                                    scanHint = "QR detected, but it is not a valid OBS WebSocket URI."
                                }
                            }
                        )
                        // Reticle overlay
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val boxSize = size.minDimension * 0.62f
                            val left = (size.width - boxSize) / 2f
                            val top = (size.height - boxSize) / 2f
                            val right = left + boxSize
                            val bottom = top + boxSize
                            val corner = 28.dp.toPx()
                            val stroke = 3.dp.toPx()
                            val c = StudioColors.ReplayCyan

                            // Top-left
                            drawLine(c, Offset(left, top), Offset(left + corner, top), stroke)
                            drawLine(c, Offset(left, top), Offset(left, top + corner), stroke)
                            // Top-right
                            drawLine(c, Offset(right - corner, top), Offset(right, top), stroke)
                            drawLine(c, Offset(right, top), Offset(right, top + corner), stroke)
                            // Bottom-left
                            drawLine(c, Offset(left, bottom), Offset(left + corner, bottom), stroke)
                            drawLine(c, Offset(left, bottom - corner), Offset(left, bottom), stroke)
                            // Bottom-right
                            drawLine(c, Offset(right - corner, bottom), Offset(right, bottom), stroke)
                            drawLine(c, Offset(right, bottom - corner), Offset(right, bottom), stroke)
                        }
                    }

                    Text(
                        text = scanHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioColors.TextSecondary
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            tint = StudioColors.TextMuted,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "Camera permission is required to scan the QR Code shown in OBS Studio.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StudioColors.TextSecondary
                        )
                        Button(
                            onClick = { launcher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioColors.ReplayCyan)
                        ) {
                            Text("Allow Camera")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun CameraQrPreview(
    onRawCode: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var delivered by remember { mutableStateOf(false) }

    val reader = remember {
        MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true
                )
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull() ?: return@addListener
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(executor) { imageProxy ->
                    try {
                        if (delivered) return@setAnalyzer
                        val plane = imageProxy.planes.firstOrNull() ?: return@setAnalyzer
                        val buffer = plane.buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)

                        val source = PlanarYUVLuminanceSource(
                            bytes,
                            plane.rowStride,
                            imageProxy.height,
                            0,
                            0,
                            imageProxy.width,
                            imageProxy.height,
                            false
                        )
                        val bitmap = BinaryBitmap(HybridBinarizer(source))
                        val result = reader.decodeWithState(bitmap)
                        val text = result?.text?.trim()
                        if (!text.isNullOrEmpty() && !delivered) {
                            delivered = true
                            previewView.post { onRawCode(text) }
                        }
                    } catch (_: NotFoundException) {
                        // Normal frame without QR
                    } catch (_: Exception) {
                    } finally {
                        reader.reset()
                        imageProxy.close()
                    }
                }

                runCatching {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}
