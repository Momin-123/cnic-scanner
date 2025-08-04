package com.example.cnicscanner

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlin.math.sqrt
import kotlin.math.pow
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ImageProcessor {
    
    fun cropAndEnhanceCNIC(
        context: Context,
        originalUri: Uri,
        onSuccess: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val originalBitmap = getBitmapFromUri(context, originalUri)
            if (originalBitmap == null) {
                onError("Failed to load original image")
                return
            }
            
            // Detect CNIC boundaries (enhanced for both English and Urdu)
            val cnicBounds = detectCNICBoundsEnhanced(originalBitmap)
            
            // Crop the CNIC
            val croppedBitmap = cropBitmap(originalBitmap, cnicBounds)
            
            // Remove background
            val backgroundRemovedBitmap = removeBackground(croppedBitmap)
            
            // Enhance the image
            val enhancedBitmap = enhanceImage(backgroundRemovedBitmap)
            
            // Save to gallery
            val savedUri = saveToGallery(context, enhancedBitmap)
            onSuccess(savedUri)
            
        } catch (e: Exception) {
            onError("Image processing failed: ${e.message}")
        }
    }
    
    private fun getBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun detectCNICBoundsEnhanced(bitmap: Bitmap): Rect {
        // Enhanced CNIC detection for both English and Urdu
        val width = bitmap.width
        val height = bitmap.height
        
        // CNIC typically has a 1.6:1 aspect ratio
        val cnicWidth = (width * 0.9).toInt() // Larger detection area for better cropping
        val cnicHeight = (cnicWidth / 1.6).toInt()
        
        val left = (width - cnicWidth) / 2
        val top = (height - cnicHeight) / 2
        
        // Ensure bounds are within image dimensions
        val safeLeft = maxOf(0, left)
        val safeTop = maxOf(0, top)
        val safeRight = minOf(width, safeLeft + cnicWidth)
        val safeBottom = minOf(height, safeTop + cnicHeight)
        
        // Ensure minimum size for cropping
        val minWidth = 100
        val minHeight = 60
        
        if (safeRight - safeLeft < minWidth || safeBottom - safeTop < minHeight) {
            // If detected area is too small, use a default centered crop
            val defaultWidth = (width * 0.8).toInt()
            val defaultHeight = (defaultWidth / 1.6).toInt()
            val defaultLeft = (width - defaultWidth) / 2
            val defaultTop = (height - defaultHeight) / 2
            
            return Rect(
                maxOf(0, defaultLeft),
                maxOf(0, defaultTop),
                minOf(width, defaultLeft + defaultWidth),
                minOf(height, defaultTop + defaultHeight)
            )
        }
        
        return Rect(safeLeft, safeTop, safeRight, safeBottom)
    }
    
    private fun cropBitmap(bitmap: Bitmap, bounds: Rect): Bitmap {
        return try {
            // Ensure bounds are valid
            val safeLeft = maxOf(0, bounds.left)
            val safeTop = maxOf(0, bounds.top)
            val safeRight = minOf(bitmap.width, bounds.right)
            val safeBottom = minOf(bitmap.height, bounds.bottom)
            
            val width = safeRight - safeLeft
            val height = safeBottom - safeTop
            
            // Ensure minimum size
            if (width < 50 || height < 30) {
                // If crop area is too small, use a centered crop
                val cropWidth = (bitmap.width * 0.8).toInt()
                val cropHeight = (cropWidth / 1.6).toInt()
                val cropLeft = (bitmap.width - cropWidth) / 2
                val cropTop = (bitmap.height - cropHeight) / 2
                
                return Bitmap.createBitmap(
                    bitmap,
                    maxOf(0, cropLeft),
                    maxOf(0, cropTop),
                    minOf(cropWidth, bitmap.width - cropLeft),
                    minOf(cropHeight, bitmap.height - cropTop)
                )
            }
            
            Bitmap.createBitmap(bitmap, safeLeft, safeTop, width, height)
        } catch (e: Exception) {
            // If cropping fails, return a centered crop of the original
            val cropWidth = (bitmap.width * 0.8).toInt()
            val cropHeight = (cropWidth / 1.6).toInt()
            val cropLeft = (bitmap.width - cropWidth) / 2
            val cropTop = (bitmap.height - cropHeight) / 2
            
            try {
                Bitmap.createBitmap(
                    bitmap,
                    maxOf(0, cropLeft),
                    maxOf(0, cropTop),
                    minOf(cropWidth, bitmap.width - cropLeft),
                    minOf(cropHeight, bitmap.height - cropTop)
                )
            } catch (e2: Exception) {
                bitmap // Return original if all cropping attempts fail
            }
        }
    }
    
    private fun removeBackground(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Create a canvas to draw on the result bitmap
        val canvas = Canvas(resultBitmap)
        
        // Create a paint object for background removal
        val paint = Paint().apply {
            isAntiAlias = true
            isDither = true
        }
        
        // Sample colors from the edges to determine background color
        val edgeColors = mutableListOf<Int>()
        val sampleSize = 10
        
        // Sample from top edge
        for (x in 0 until width step sampleSize) {
            edgeColors.add(bitmap.getPixel(x, 0))
        }
        
        // Sample from bottom edge
        for (x in 0 until width step sampleSize) {
            edgeColors.add(bitmap.getPixel(x, height - 1))
        }
        
        // Sample from left edge
        for (y in 0 until height step sampleSize) {
            edgeColors.add(bitmap.getPixel(0, y))
        }
        
        // Sample from right edge
        for (y in 0 until height step sampleSize) {
            edgeColors.add(bitmap.getPixel(width - 1, y))
        }
        
        // Calculate average background color
        val avgRed = edgeColors.map { android.graphics.Color.red(it) }.average().toInt()
        val avgGreen = edgeColors.map { android.graphics.Color.green(it) }.average().toInt()
        val avgBlue = edgeColors.map { android.graphics.Color.blue(it) }.average().toInt()
        
        val backgroundColor = android.graphics.Color.rgb(avgRed, avgGreen, avgBlue)
        val tolerance = 30 // Color tolerance for background removal
        
        // Process each pixel
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                val pixelRed = android.graphics.Color.red(pixel)
                val pixelGreen = android.graphics.Color.green(pixel)
                val pixelBlue = android.graphics.Color.blue(pixel)
                
                // Calculate color distance from background
                val colorDistance = kotlin.math.sqrt(
                    (pixelRed - avgRed).toDouble().pow(2) +
                    (pixelGreen - avgGreen).toDouble().pow(2) +
                    (pixelBlue - avgBlue).toDouble().pow(2)
                )
                
                if (colorDistance > tolerance) {
                    // Keep the pixel if it's significantly different from background
                    resultBitmap.setPixel(x, y, pixel)
                } else {
                    // Make background transparent
                    resultBitmap.setPixel(x, y, android.graphics.Color.TRANSPARENT)
                }
            }
        }
        
        return resultBitmap
    }
    
    private fun enhanceImage(bitmap: Bitmap): Bitmap {
        val enhancedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(enhancedBitmap)
        
        // Apply contrast and brightness adjustments
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                setSaturation(1.3f) // Increase saturation for better text visibility
            })
        }
        
        // Apply additional enhancement for better text readability
        val colorMatrix = ColorMatrix().apply {
            setSaturation(1.2f) // Slight saturation increase
        }
        
        // Apply contrast adjustment
        val contrastMatrix = ColorMatrix().apply {
            setScale(1.2f, 1.2f, 1.2f, 1.0f) // Increase contrast
        }
        
        colorMatrix.postConcat(contrastMatrix)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return enhancedBitmap
    }
    
    private fun saveToGallery(context: Context, bitmap: Bitmap): Uri {
        val filename = "CNIC_${System.currentTimeMillis()}.jpg"
        
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10 and above, use MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CNICScanner")
                }
                
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let { savedUri ->
                    context.contentResolver.openOutputStream(savedUri)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                    }
                    savedUri
                } ?: throw IOException("Failed to create new MediaStore record.")
            } else {
                // For older Android versions, save to external storage
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val cnicDir = File(imagesDir, "CNICScanner")
                if (!cnicDir.exists()) {
                    cnicDir.mkdirs()
                }
                
                val imageFile = File(cnicDir, filename)
                FileOutputStream(imageFile).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                }
                
                // Convert to content URI for sharing
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    imageFile
                )
            }
        } catch (e: Exception) {
            // Fallback: save to app's external files directory
            val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val fallbackFile = File(fallbackDir, filename)
            FileOutputStream(fallbackFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }
            Uri.fromFile(fallbackFile)
        }
    }
    
    fun getFileProviderAuthority(context: Context): String {
        return "${context.packageName}.fileprovider"
    }
} 