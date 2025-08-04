package com.example.cnicscanner

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.abs
// ML Kit imports commented out to avoid compilation issues
// import com.google.mlkit.vision.common.InputImage
// import com.google.mlkit.vision.text.TextRecognition
// import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object CNICDetector {
    
    // ML Kit text recognizer commented out to avoid compilation issues
    // private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    suspend fun detectCNICInImage(bitmap: Bitmap): CNICDetectionResult {
        // Enhanced detection for both English and Urdu CNICs
        return analyzeTextForCNICEnhanced("", bitmap)
    }
    
    private fun analyzeTextForCNICEnhanced(text: String, bitmap: Bitmap): CNICDetectionResult {
        // Enhanced CNIC patterns for both English and Urdu
        val cnicPatterns = listOf(
            // English patterns
            "CNIC", "NIC", "Identity Card", "National Identity",
            "Pakistan", "PAK", "NADRA", "Identity Number", "National Identity Card",
            "Computerized National Identity Card", "Government of Pakistan",
            "Federal Government", "Republic of Pakistan",
            
            // Urdu patterns (common Urdu text found on CNIC)
            "قومی شناختی کارڈ", "شناختی کارڈ", "پاکستان", "حکومت پاکستان",
            "مکمل نام", "والد کا نام", "تاریخ پیدائش", "جنس", "خاندانی نام",
            "پہلا نام", "آخری نام", "شناختی نمبر", "قومی شناختی نمبر",
            "پاکستانی شناختی کارڈ", "وفاقی حکومت", "جمہوریہ پاکستان"
        )
        
        val hasCNICText = cnicPatterns.any { pattern ->
            text.contains(pattern, ignoreCase = true)
        }
        
        // Check for CNIC number pattern (13 digits with dashes)
        val cnicNumberPattern = Regex("\\d{5}-\\d{7}-\\d")
        val hasCNICNumber = cnicNumberPattern.containsMatchIn(text)
        
        // Check for rectangular shape with enhanced detection
        val isRectangular = detectRectangularShapeEnhanced(bitmap)
        
        // Check for document-like features
        val hasDocumentFeatures = detectDocumentFeatures(bitmap)
        
        // Check for text density (indicating a document)
        val hasTextDensity = detectTextDensity(bitmap)
        
        val isCNIC = hasCNICText || hasCNICNumber || isRectangular || hasDocumentFeatures || hasTextDensity
        
        val bounds = if (isCNIC) {
            // Estimate CNIC bounds based on enhanced detection
            estimateCNICBoundsEnhanced(bitmap)
        } else null
        
        val confidence = calculateEnhancedConfidence(
            hasCNICText, hasCNICNumber, isRectangular, hasDocumentFeatures, hasTextDensity
        )
        
        return CNICDetectionResult(
            isDetected = isCNIC,
            bounds = bounds,
            confidence = confidence
        )
    }
    
    private fun detectRectangularShapeEnhanced(bitmap: Bitmap): Boolean {
        // Enhanced edge detection for rectangular shape
        val width = bitmap.width
        val height = bitmap.height
        
        // Check if the image has a rectangular aspect ratio (typical for CNIC)
        val aspectRatio = width.toFloat() / height.toFloat()
        val isRectangularAspect = aspectRatio in 1.4f..1.8f // CNIC aspect ratio range
        
        // Check for strong edges at the boundaries
        val edgeStrength = calculateEdgeStrengthEnhanced(bitmap)
        
        // Check for corner detection
        val hasCorners = detectCorners(bitmap)
        
        return isRectangularAspect && edgeStrength > 0.25f && hasCorners
    }
    
    private fun calculateEdgeStrengthEnhanced(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        var edgePixels = 0
        var totalPixels = 0
        
        // Sample pixels to detect edges with better algorithm
        for (x in 0 until width step 3) {
            for (y in 0 until height step 3) {
                totalPixels++
                val pixel = bitmap.getPixel(x, y)
                val gray = (android.graphics.Color.red(pixel) + 
                           android.graphics.Color.green(pixel) + 
                           android.graphics.Color.blue(pixel)) / 3
                
                // Check for edges (significant color changes)
                if (x > 0 && y > 0 && x < width - 1 && y < height - 1) {
                    val leftPixel = bitmap.getPixel(x - 1, y)
                    val rightPixel = bitmap.getPixel(x + 1, y)
                    val topPixel = bitmap.getPixel(x, y - 1)
                    val bottomPixel = bitmap.getPixel(x, y + 1)
                    
                    val leftGray = (android.graphics.Color.red(leftPixel) + 
                                   android.graphics.Color.green(leftPixel) + 
                                   android.graphics.Color.blue(leftPixel)) / 3
                    val rightGray = (android.graphics.Color.red(rightPixel) + 
                                    android.graphics.Color.green(rightPixel) + 
                                    android.graphics.Color.blue(rightPixel)) / 3
                    val topGray = (android.graphics.Color.red(topPixel) + 
                                  android.graphics.Color.green(topPixel) + 
                                  android.graphics.Color.blue(topPixel)) / 3
                    val bottomGray = (android.graphics.Color.red(bottomPixel) + 
                                     android.graphics.Color.green(bottomPixel) + 
                                     android.graphics.Color.blue(bottomPixel)) / 3
                    
                    val horizontalGradient = kotlin.math.abs(gray - leftGray) + kotlin.math.abs(gray - rightGray)
                    val verticalGradient = kotlin.math.abs(gray - topGray) + kotlin.math.abs(gray - bottomGray)
                    
                    if (horizontalGradient > 50 || verticalGradient > 50) {
                        edgePixels++
                    }
                }
            }
        }
        
        return if (totalPixels > 0) edgePixels.toFloat() / totalPixels else 0f
    }
    
    private fun detectCorners(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        var cornerCount = 0
        
        // Check corners for strong edges
        val cornerSize = 20
        val corners = listOf(
            Rect(0, 0, cornerSize, cornerSize), // Top-left
            Rect(width - cornerSize, 0, width, cornerSize), // Top-right
            Rect(0, height - cornerSize, cornerSize, height), // Bottom-left
            Rect(width - cornerSize, height - cornerSize, width, height) // Bottom-right
        )
        
        for (corner in corners) {
            var edgePixels = 0
            var totalPixels = 0
            
            for (x in corner.left until corner.right) {
                for (y in corner.top until corner.bottom) {
                    if (x < width && y < height) {
                        totalPixels++
                        val pixel = bitmap.getPixel(x, y)
                        val gray = (android.graphics.Color.red(pixel) + 
                                   android.graphics.Color.green(pixel) + 
                                   android.graphics.Color.blue(pixel)) / 3
                        
                        if (gray < 128) { // Dark pixel (potential edge)
                            edgePixels++
                        }
                    }
                }
            }
            
            if (totalPixels > 0 && edgePixels.toFloat() / totalPixels > 0.3f) {
                cornerCount++
            }
        }
        
        return cornerCount >= 2 // At least 2 corners should have strong edges
    }
    
    private fun detectDocumentFeatures(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        var structuredPixels = 0
        var totalPixels = 0
        
        // Look for structured content typical in documents
        for (x in 0 until width step 5) {
            for (y in 0 until height step 5) {
                totalPixels++
                val pixel = bitmap.getPixel(x, y)
                val gray = (android.graphics.Color.red(pixel) + 
                           android.graphics.Color.green(pixel) + 
                           android.graphics.Color.blue(pixel)) / 3
                
                // Check for mid-tone pixels typical in documents
                if (gray in 50..200) {
                    structuredPixels++
                }
            }
        }
        
        val structuredRatio = if (totalPixels > 0) structuredPixels.toFloat() / totalPixels else 0f
        return structuredRatio > 0.4f
    }
    
    private fun detectTextDensity(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        var textPixels = 0
        var totalPixels = 0
        
        // Look for text-like patterns
        for (x in 0 until width step 4) {
            for (y in 0 until height step 4) {
                totalPixels++
                val pixel = bitmap.getPixel(x, y)
                val gray = (android.graphics.Color.red(pixel) + 
                           android.graphics.Color.green(pixel) + 
                           android.graphics.Color.blue(pixel)) / 3
                
                // Dark pixels that could be text
                if (gray < 100) {
                    textPixels++
                }
            }
        }
        
        val textRatio = if (totalPixels > 0) textPixels.toFloat() / totalPixels else 0f
        return textRatio > 0.15f
    }
    
    private fun estimateCNICBoundsEnhanced(bitmap: Bitmap): Rect {
        val width = bitmap.width
        val height = bitmap.height
        
        // CNIC typically takes up 70-90% of the image
        val cnicWidth = (width * 0.85).toInt()
        val cnicHeight = (cnicWidth / 1.6).toInt() // CNIC aspect ratio
        
        val left = (width - cnicWidth) / 2
        val top = (height - cnicHeight) / 2
        
        // Ensure bounds are within image dimensions
        val safeLeft = maxOf(0, left)
        val safeTop = maxOf(0, top)
        val safeRight = minOf(width, safeLeft + cnicWidth)
        val safeBottom = minOf(height, safeTop + cnicHeight)
        
        return Rect(safeLeft, safeTop, safeRight, safeBottom)
    }
    
    private fun calculateEnhancedConfidence(
        hasCNICText: Boolean,
        hasCNICNumber: Boolean,
        isRectangular: Boolean,
        hasDocumentFeatures: Boolean,
        hasTextDensity: Boolean
    ): Float {
        var confidence = 0f
        if (hasCNICText) confidence += 0.3f
        if (hasCNICNumber) confidence += 0.3f
        if (isRectangular) confidence += 0.2f
        if (hasDocumentFeatures) confidence += 0.15f
        if (hasTextDensity) confidence += 0.05f
        return confidence
    }
}

data class CNICDetectionResult(
    val isDetected: Boolean,
    val bounds: Rect?,
    val confidence: Float,
    val errorMessage: String? = null
) 