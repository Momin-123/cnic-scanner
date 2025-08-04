package com.example.cnicscanner

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest

@Composable
fun PreviewScreen(
    imageUri: Uri,
    onSaveToGallery: () -> Unit,
    onShare: () -> Unit,
    onRetake: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isProcessing by remember { mutableStateOf(false) }
    var isSaved by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            
            Text(
                text = "CNIC Preview",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall
            )
            
            // Placeholder for symmetry
            Box(modifier = Modifier.size(48.dp))
        }
        
        // Image Preview
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Image(
                painter = rememberAsyncImagePainter(
                    ImageRequest.Builder(context)
                        .data(imageUri)
                        .build()
                ),
                contentDescription = "Captured CNIC",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        
        // Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Retake Button
            Button(
                onClick = onRetake,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Gray
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Retake")
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Save Button
            Button(
                onClick = {
                    isProcessing = true
                    ImageProcessor.cropAndEnhanceCNIC(
                        context = context,
                        originalUri = imageUri,
                        onSuccess = { savedUri ->
                            isProcessing = false
                            isSaved = true
                            onSaveToGallery()
                        },
                        onError = { error ->
                            isProcessing = false
                            // Show error message
                        }
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSaved) Color.Green else MaterialTheme.colorScheme.primary
                )
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Check else Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(if (isSaved) "Saved!" else "Save")
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Share Button
            Button(
                onClick = onShare,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Blue
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Share")
            }
        }
        
        // Status Text
        if (isProcessing) {
            Text(
                text = "Processing and saving CNIC...",
                color = Color.White,
                modifier = Modifier.padding(top = 16.dp)
            )
        } else if (isSaved) {
            Text(
                text = "CNIC saved to gallery successfully!",
                color = Color.Green,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
} 