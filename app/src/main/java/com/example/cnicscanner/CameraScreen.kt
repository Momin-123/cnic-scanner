package com.example.cnicscanner

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

@Composable
fun CameraScreen(
    onImageCaptured: (Uri) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var camera: Camera? by remember { mutableStateOf(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var cnicDetected by remember { mutableStateOf(false) }
    var detectionConfidence by remember { mutableStateOf(0f) }
    var currentSide by remember { mutableStateOf("front") } // "front" or "back"
    var frontCaptured by remember { mutableStateOf(false) }
    var backCaptured by remember { mutableStateOf(false) }
    var autoCaptureEnabled by remember { mutableStateOf(true) }
    var lastMotionTime by remember { mutableStateOf(0L) }
    var lastAnalysisTime by remember { mutableStateOf(0L) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, camera will be initialized
        } else {
            onError("Camera permission is required")
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    this.scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageCaptureInstance = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()

                    imageCapture = imageCaptureInstance

                    val imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(android.util.Size(640, 480)) // Lower resolution for better performance
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor) { imageProxy ->
                                try {
                                    // Rate limiting - only process every 200ms to prevent buffer overflow
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastAnalysisTime < 200) {
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }
                                    lastAnalysisTime = currentTime
                                    
                                    // Enhanced CNIC detection logic for both English and Urdu
                                    detectCNICEnhanced(imageProxy) { detected, confidence ->
                                        cnicDetected = detected
                                        detectionConfidence = confidence

                                        // Debug: Log detection results
                                        android.util.Log.d("CNICDetection", "Detected: $detected, Confidence: $confidence")

                                        // Auto-capture when CNIC is detected with low confidence or after motion
                                        val currentTime = System.currentTimeMillis()
                                        val timeSinceLastMotion = currentTime - lastMotionTime

                                        if ((detected && confidence > 0.2f) ||
                                            (timeSinceLastMotion > 2000 && confidence > 0.1f)) {
                                            if (!isCapturing && autoCaptureEnabled) {
                                                isCapturing = true
                                                captureImage(
                                                    imageCapture = imageCaptureInstance,
                                                    context = context,
                                                    onImageCaptured = { uri ->
                                                        // Process and save the image automatically
                                                        ImageProcessor.cropAndEnhanceCNIC(
                                                            context = context,
                                                            originalUri = uri,
                                                            onSuccess = { savedUri ->
                                                                onImageCaptured(savedUri)
                                                                // Switch sides after capture
                                                                if (currentSide == "front" && !frontCaptured) {
                                                                    frontCaptured = true
                                                                    currentSide = "back"
                                                                } else if (currentSide == "back" && !backCaptured) {
                                                                    backCaptured = true
                                                                    currentSide = "front"
                                                                }
                                                            },
                                                            onError = { error ->
                                                                // If processing fails, still pass the original URI
                                                                onImageCaptured(uri)
                                                                // Switch sides after capture
                                                                if (currentSide == "front" && !frontCaptured) {
                                                                    frontCaptured = true
                                                                    currentSide = "back"
                                                                } else if (currentSide == "back" && !backCaptured) {
                                                                    backCaptured = true
                                                                    currentSide = "front"
                                                                }
                                                            }
                                                        )
                                                    },
                                                    onError = onError
                                                ) {
                                                    isCapturing = false
                                                }
                                            }

                                            // Update motion detection
                                            if (confidence > 0.1f) {
                                                lastMotionTime = currentTime
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Log the exception but don't let it crash the analyzer
                                    android.util.Log.e("CNICDetection", "Error in image analysis: ${e.message}")
                                    // Set default values if detection fails
                                    cnicDetected = false
                                    detectionConfidence = 0f
                                } finally {
                                    // Always close the imageProxy to prevent buffer queue overflow
                                    try {
                                        imageProxy.close()
                                    } catch (e: Exception) {
                                        android.util.Log.e("CNICDetection", "Error closing imageProxy: ${e.message}")
                                    }
                                }
                            }
                        }

                    try {
                        cameraProvider?.unbindAll()
                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(lensFacing)
                            .build()
                        camera = cameraProvider?.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCaptureInstance,
                            imageAnalyzer
                        )
                    } catch (e: Exception) {
                        onError("Camera binding failed: ${e.message}")
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Larger CNIC Boundary Rectangle - ID Card Shape
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp) // Reduced padding for bigger box
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Calculate larger CNIC dimensions (ID card aspect ratio ~1.6:1)
                val cnicWidth = size.width * 0.9f // Increased from 0.8f to 0.9f
                val cnicHeight = cnicWidth / 1.6f
                val cnicX = (size.width - cnicWidth) / 2f
                val cnicY = (size.height - cnicHeight) / 2f

                val cornerLength = 50f // Increased corner length
                val strokeWidth = 5f // Increased stroke width
                val cornerColor = when {
                    cnicDetected && detectionConfidence > 0.2f -> Color.Green
                    cnicDetected -> Color.Yellow
                    else -> Color.White
                }

                // Draw CNIC-shaped rectangle outline using lines
                // Top edge
                drawLine(
                    color = cornerColor,
                    start = androidx.compose.ui.geometry.Offset(cnicX, cnicY),
                    end = androidx.compose.ui.geometry.Offset(cnicX + cnicWidth, cnicY),
                    strokeWidth = strokeWidth
                )
                // Bottom edge
                drawLine(
                    color = cornerColor,
                    start = androidx.compose.ui.geometry.Offset(cnicX, cnicY + cnicHeight),
                    end = androidx.compose.ui.geometry.Offset(cnicX + cnicWidth, cnicY + cnicHeight),
                    strokeWidth = strokeWidth
                )
                // Left edge
                drawLine(
                    color = cornerColor,
                    start = androidx.compose.ui.geometry.Offset(cnicX, cnicY),
                    end = androidx.compose.ui.geometry.Offset(cnicX, cnicY + cnicHeight),
                    strokeWidth = strokeWidth
                )
                // Right edge
                drawLine(
                    color = cornerColor,
                    start = androidx.compose.ui.geometry.Offset(cnicX + cnicWidth, cnicY),
                    end = androidx.compose.ui.geometry.Offset(cnicX + cnicWidth, cnicY + cnicHeight),
                    strokeWidth = strokeWidth
                )

                // Top-left corner
                drawLine(
                    color = cornerColor,
                    start = androidx.compose.ui.geometry.Offset(cnicX, cnicY + cornerLength),
                    end = androidx.compose.ui.geometry.Offset(cnicX, cnicY),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = cornerColor,
                    start = androidx.compose.ui.geometry.Offset(cnicX, cnicY),
                    end = androidx.compose.ui.geometry.Offset(cnicX + cornerLength, cnicY),
                    strokeWidth = strokeWidth
                )

                // Top-right corner
                drawLine(
                    color = cornerColor,
                    start = androidx.compose.ui.geometry.Offset(cnicX + cnicWidth - cornerLength, cnicY),
                    end = androidx.compose.ui.geometry.Offset(cnicX + cnicWidth, cnicY),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = cornerColor,
                    start = androidx.compose.ui.geometry.Offset(cnicX + cnicWidth, cnicY),
                    end = androidx.compose.ui.geometry.Offset(cnicX + cnicWidth, cnicY + cornerLength),
                    strokeWidth = strokeWidth
                )

                // Bottom-left corner
                drawLine(
                    color = cornerColor,
                    start = androidx.compose.ui.geometry.Offset(cnicX, cnicY + cnicHeight - cornerLength),
                    end = androidx.compose.ui.geometry.Offset(cnicX, cnicY + cnicHeight),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = cornerColor,
                    start = androidx.compose.ui.geometry.Offset(cnicX, cnicY + cnicHeight),
                    end = androidx.compose.ui.geometry.Offset(cnicX + cornerLength, cnicY + cnicHeight),
                    strokeWidth = strokeWidth
                )

                // Bottom-right corner
                drawLine(
                    color = cornerColor,
                    start = androidx.compose.ui.geometry.Offset(cnicX + cnicWidth - cornerLength, cnicY + cnicHeight),
                    end = androidx.compose.ui.geometry.Offset(cnicX + cnicWidth, cnicY + cnicHeight),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = cornerColor,
                    start = androidx.compose.ui.geometry.Offset(cnicX + cnicWidth, cnicY + cnicHeight),
                    end = androidx.compose.ui.geometry.Offset(cnicX + cnicWidth, cnicY + cnicHeight - cornerLength),
                    strokeWidth = strokeWidth
                )
                    }
                }

                // Instructions Text
                Text(
                    text = when {
                        isCapturing -> "Capturing & Cropping ${currentSide.capitalize()} side..."
                        cnicDetected && detectionConfidence > 0.2f -> "CNIC Detected! Auto-capturing ${currentSide} side..."
                        cnicDetected -> "CNIC detected (${(detectionConfidence * 100).toInt()}% confidence)"
                        else -> "Position CNIC within the frame (${currentSide.capitalize()} side)"
                    },
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                )

                // Side indicator
                Text(
                    text = "Side: ${currentSide.capitalize()}",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .background(
                            color = Color.Blue.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                )

                // Progress indicator
                if (frontCaptured || backCaptured) {
                    Text(
                        text = "Front: ${if (frontCaptured) "✓" else "✗"} | Back: ${if (backCaptured) "✓" else "✗"}",
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .background(
                                color = Color.Green.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    )
                }

                // Debug info
                Text(
                    text = "Detection: ${if (cnicDetected) "YES" else "NO"} (${(detectionConfidence * 100).toInt()}%)",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                )

                // Manual capture button (fallback)
                Button(
                    onClick = {
                        if (!isCapturing && imageCapture != null) {
                            isCapturing = true
                            captureImage(
                                imageCapture = imageCapture,
                                context = context,
                                onImageCaptured = { uri ->
                                    // Process and save the image automatically
                                    ImageProcessor.cropAndEnhanceCNIC(
                                        context = context,
                                        originalUri = uri,
                                        onSuccess = { savedUri ->
                                            onImageCaptured(savedUri)
                                            if (currentSide == "front" && !frontCaptured) {
                                                frontCaptured = true
                                                currentSide = "back"
                                            } else if (currentSide == "back" && !backCaptured) {
                                                backCaptured = true
                                                currentSide = "front"
                                            }
                                        },
                                        onError = { error ->
                                            // If processing fails, still pass the original URI
                                            onImageCaptured(uri)
                                            if (currentSide == "front" && !frontCaptured) {
                                                frontCaptured = true
                                                currentSide = "back"
                                            } else if (currentSide == "back" && !backCaptured) {
                                                backCaptured = true
                                                currentSide = "front"
                                            }
                                        }
                                    )
                                },
                                onError = onError
                            ) {
                                isCapturing = false
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(32.dp),
                    enabled = !isCapturing && imageCapture != null
                ) {
                    Text("Manual Capture")
                }

                // Test button to force CNIC detection
                Button(
                    onClick = {
                        cnicDetected = true
                        detectionConfidence = 0.8f
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Test Detection")
                }

                // Force capture button
                Button(
                    onClick = {
                        if (!isCapturing && imageCapture != null) {
                            isCapturing = true
                            captureImage(
                                imageCapture = imageCapture,
                                context = context,
                                onImageCaptured = { uri ->
                                    // Process and save the image automatically
                                    ImageProcessor.cropAndEnhanceCNIC(
                                        context = context,
                                        originalUri = uri,
                                        onSuccess = { savedUri ->
                                            onImageCaptured(savedUri)
                                            if (currentSide == "front" && !frontCaptured) {
                                                frontCaptured = true
                                                currentSide = "back"
                                            } else if (currentSide == "back" && !backCaptured) {
                                                backCaptured = true
                                                currentSide = "front"
                                            }
                                        },
                                        onError = { error ->
                                            onImageCaptured(uri)
                                            if (currentSide == "front" && !frontCaptured) {
                                                frontCaptured = true
                                                currentSide = "back"
                                            } else if (currentSide == "back" && !backCaptured) {
                                                backCaptured = true
                                                currentSide = "front"
                                            }
                                        }
                                    )
                                },
                                onError = onError
                            ) {
                                isCapturing = false
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                ) {
                    Text("Force Capture")
                }
            }
    }

    private fun detectCNICEnhanced(
        imageProxy: ImageProxy,
        onDetectionResult: (Boolean, Float) -> Unit
    ) {
        // Add timeout to prevent long processing
        val startTime = System.currentTimeMillis()
        try {
            val buffer = imageProxy.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)

            val width = imageProxy.width
            val height = imageProxy.height

            // Convert YUV to RGB and create bitmap
            val bitmap = yuvToBitmap(data, width, height, imageProxy.imageInfo.rotationDegrees)

            // Enhanced CNIC detection for both English and Urdu
            val enhancedResult = enhancedCNICDetection(data, width, height, bitmap)

            // If enhanced detection fails, try basic detection
            if (!enhancedResult.first) {
                val basicResult = basicCNICDetection(data, width, height)
                if (basicResult.first) {
                    onDetectionResult(basicResult.first, basicResult.second)
                    return
                }
            }

            onDetectionResult(enhancedResult.first, enhancedResult.second)
        } catch (e: Exception) {
            // Check for timeout
            if (System.currentTimeMillis() - startTime > 100) { // 100ms timeout
                onDetectionResult(false, 0f)
                return
            }
            // Fallback to basic detection
            try {
                val buffer = imageProxy.planes[0].buffer
                val data = ByteArray(buffer.remaining())
                buffer.get(data)
                val basicResult = basicCNICDetection(data, imageProxy.width, imageProxy.height)
                onDetectionResult(basicResult.first, basicResult.second)
            } catch (e2: Exception) {
                // Final fallback - assume CNIC is present if we can't process
                onDetectionResult(true, 0.8f)
            }
        }
    }

    private fun enhancedCNICDetection(data: ByteArray, width: Int, height: Int, bitmap: Bitmap?): Pair<Boolean, Float> {
        // Enhanced detection for both English and Urdu CNICs
        val centerX = width / 2
        val centerY = height / 2

        // Define larger CNIC detection area
        val cnicWidth = (width * 0.9).toInt() // Increased from 0.8
        val cnicHeight = (cnicWidth / 1.6).toInt()
        val cnicX = (width - cnicWidth) / 2
        val cnicY = (height - cnicHeight) / 2

        var edgeCount = 0
        var textLikeCount = 0
        var contrastCount = 0
        var horizontalLineCount = 0
        var verticalLineCount = 0
        var documentLikeCount = 0
        var structuredContentCount = 0
        var borderCount = 0

        val threshold = 50  // Lowered threshold for better edge detection
        val textThreshold = 60  // Increased for better text detection
        val lineThreshold = 60  // Lowered for line detection

        // Sample points within the CNIC area with larger step for better performance
        for (x in cnicX..cnicX + cnicWidth step 8) { // Increased step size for performance
            for (y in cnicY..cnicY + cnicHeight step 8) {
                if (x >= 0 && x < width && y >= 0 && y < height) {
                    val index = y * width + x
                    if (index < data.size) {
                        val pixelValue = data[index].toInt() and 0xFF

                        // Edge detection (bright pixels)
                        if (pixelValue > threshold) {
                            edgeCount++
                        }

                        // Text-like pattern detection (dark pixels that could be text)
                        if (pixelValue < textThreshold) {
                            textLikeCount++
                        }

                        // Contrast detection (check for sharp transitions)
                        if (x > 0 && y > 0 && x < width - 1 && y < height - 1) {
                            val leftPixel = data[index - 1].toInt() and 0xFF
                            val rightPixel = data[index + 1].toInt() and 0xFF
                            val topPixel = data[index - width].toInt() and 0xFF
                            val bottomPixel = data[index + width].toInt() and 0xFF

                            val horizontalContrast = abs(pixelValue - leftPixel) + abs(pixelValue - rightPixel)
                            val verticalContrast = abs(pixelValue - topPixel) + abs(pixelValue - bottomPixel)

                            if (horizontalContrast > lineThreshold) {
                                horizontalLineCount++
                            }
                            if (verticalContrast > lineThreshold) {
                                verticalLineCount++
                            }

                            val totalContrast = horizontalContrast + verticalContrast
                            if (totalContrast > 80) { // Lowered threshold
                                contrastCount++
                            }
                        }

                        // Document-like pattern (check for structured content)
                        if (pixelValue in 30..180) {  // Wider range for document detection
                            documentLikeCount++
                        }

                        // Structured content detection (for both English and Urdu)
                        if (pixelValue in 20..140) {
                            structuredContentCount++
                        }

                        // Border detection
                        if (x <= cnicX + 15 || x >= cnicX + cnicWidth - 15 ||
                            y <= cnicY + 15 || y >= cnicY + cnicHeight - 15) {
                            if (pixelValue > 80) { // Lowered threshold
                                borderCount++
                            }
                        }
                    }
                }
            }
        }

        // Calculate various metrics
        val totalPixels = (cnicWidth * cnicHeight) / 16 // Due to step 4
        val edgeDensity = edgeCount.toFloat() / totalPixels
        val textDensity = textLikeCount.toFloat() / totalPixels
        val contrastDensity = contrastCount.toFloat() / totalPixels
        val horizontalLineDensity = horizontalLineCount.toFloat() / totalPixels
        val verticalLineDensity = verticalLineCount.toFloat() / totalPixels
        val documentDensity = documentLikeCount.toFloat() / totalPixels
        val structuredDensity = structuredContentCount.toFloat() / totalPixels
        val borderDensity = borderCount.toFloat() / totalPixels

        // Enhanced confidence calculation with more lenient weights
        val confidence = (
                edgeDensity * 0.15f +
                        textDensity * 0.25f +
                        contrastDensity * 0.2f +
                        horizontalLineDensity * 0.1f +
                        verticalLineDensity * 0.1f +
                        documentDensity * 0.1f +
                        structuredDensity * 0.05f +
                        borderDensity * 0.05f
                )

        // Much more lenient detection criteria for better auto-capture
        val hasGoodEdges = edgeDensity > 0.05f // Much lower threshold
        val hasText = textDensity > 0.03f // Much lower threshold
        val hasContrast = contrastDensity > 0.02f // Much lower threshold
        val hasLines = horizontalLineDensity > 0.01f || verticalLineDensity > 0.01f // Much lower
        val hasDocumentStructure = documentDensity > 0.1f // Much lower threshold
        val hasStructuredContent = structuredDensity > 0.05f // Much lower threshold
        val hasBorders = borderDensity > 0.01f // Much lower threshold
        val hasHighConfidence = confidence > 0.15f // Much lower threshold

        // Only require a few criteria to be met for detection
        val isDetected = (hasGoodEdges && hasText) || (hasContrast && hasLines) ||
                (hasDocumentStructure && hasStructuredContent) ||
                (hasBorders && hasHighConfidence) || confidence > 0.3f

        return Pair(isDetected, minOf(confidence, 1.0f))
    }

    private fun basicCNICDetection(data: ByteArray, width: Int, height: Int): Pair<Boolean, Float> {
        // Much stricter CNIC detection with better criteria
        val centerX = width / 2
        val centerY = height / 2

        // Define CNIC detection area (rectangular like ID card)
        val cnicWidth = (width * 0.8).toInt()
        val cnicHeight = (cnicWidth / 1.6).toInt()
        val cnicX = (width - cnicWidth) / 2
        val cnicY = (height - cnicHeight) / 2

        var edgeCount = 0
        var textLikeCount = 0
        var contrastCount = 0
        var horizontalLineCount = 0
        var verticalLineCount = 0
        var documentLikeCount = 0

        val threshold = 70  // Higher threshold for edges
        val textThreshold = 30  // Lower threshold for text
        val lineThreshold = 100  // Threshold for line detection

        // Sample points within the CNIC area with smaller step for better accuracy
        for (x in cnicX..cnicX + cnicWidth step 4) {
            for (y in cnicY..cnicY + cnicHeight step 4) {
                if (x >= 0 && x < width && y >= 0 && y < height) {
                    val index = y * width + x
                    if (index < data.size) {
                        val pixelValue = data[index].toInt() and 0xFF

                        // Edge detection (bright pixels)
                        if (pixelValue > threshold) {
                            edgeCount++
                        }

                        // Text-like pattern detection (dark pixels that could be text)
                        if (pixelValue < textThreshold) {
                            textLikeCount++
                        }

                        // Contrast detection (check for sharp transitions)
                        if (x > 0 && y > 0 && x < width - 1 && y < height - 1) {
                            val leftPixel = data[index - 1].toInt() and 0xFF
                            val rightPixel = data[index + 1].toInt() and 0xFF
                            val topPixel = data[index - width].toInt() and 0xFF
                            val bottomPixel = data[index + width].toInt() and 0xFF

                            val horizontalContrast = abs(pixelValue - leftPixel) + abs(pixelValue - rightPixel)
                            val verticalContrast = abs(pixelValue - topPixel) + abs(pixelValue - bottomPixel)

                            if (horizontalContrast > lineThreshold) {
                                horizontalLineCount++
                            }
                            if (verticalContrast > lineThreshold) {
                                verticalLineCount++
                            }

                            val totalContrast = horizontalContrast + verticalContrast
                            if (totalContrast > 120) {
                                contrastCount++
                            }
                        }

                        // Document-like pattern (check for structured content)
                        if (pixelValue in 50..150) {  // Mid-tone pixels typical in documents
                            documentLikeCount++
                        }
                    }
                }
            }
        }

        // Calculate various metrics
        val totalPixels = (cnicWidth * cnicHeight) / 16 // Due to step 4
        val edgeDensity = edgeCount.toFloat() / totalPixels
        val textDensity = textLikeCount.toFloat() / totalPixels
        val contrastDensity = contrastCount.toFloat() / totalPixels
        val horizontalLineDensity = horizontalLineCount.toFloat() / totalPixels
        val verticalLineDensity = verticalLineCount.toFloat() / totalPixels
        val documentDensity = documentLikeCount.toFloat() / totalPixels

        // Much stricter combined confidence score
        val confidence = (
                edgeDensity * 0.25f +
                        textDensity * 0.25f +
                        contrastDensity * 0.2f +
                        horizontalLineDensity * 0.15f +
                        verticalLineDensity * 0.1f +
                        documentDensity * 0.05f
                )

        // Much more lenient detection criteria for better detection
        val hasGoodEdges = edgeDensity > 0.05f
        val hasText = textDensity > 0.03f
        val hasContrast = contrastDensity > 0.02f
        val hasLines = horizontalLineDensity > 0.01f || verticalLineDensity > 0.01f
        val hasDocumentStructure = documentDensity > 0.1f
        val hasHighConfidence = confidence > 0.15f

        // Only require a few criteria to be met for detection
        val isDetected = (hasGoodEdges && hasText) || (hasContrast && hasLines) ||
                hasDocumentStructure || hasHighConfidence || confidence > 0.25f

        return Pair(isDetected, minOf(confidence, 1.0f))
    }

    private fun yuvToBitmap(data: ByteArray, width: Int, height: Int, rotation: Int): Bitmap? {
        return try {
            val yuvImage = android.graphics.YuvImage(data, android.graphics.ImageFormat.NV21, width, height, null)
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
            val imageBytes = out.toByteArray()
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            out.close()
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun captureImage(
        imageCapture: ImageCapture?,
        context: Context,
        onImageCaptured: (Uri) -> Unit,
        onError: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        val photoFile = File(
            context.getExternalFilesDir(null),
            "CNIC_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    onImageCaptured(savedUri)
                    onComplete()
                }

                override fun onError(exception: ImageCaptureException) {
                    onError("Image capture failed: ${exception.message}")
                    onComplete()
                }
            }
        )
    }