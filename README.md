# CNIC Scanner App

A comprehensive Android application for scanning and processing Pakistani Computerized National Identity Cards (CNIC) in both English and Urdu.

## Features

### Enhanced CNIC Detection
- **Bigger Detection Box**: Increased detection area from 80% to 90% of screen width for better CNIC positioning
- **Multi-language Support**: Detects CNICs in both English and Urdu languages
- **Improved Accuracy**: Enhanced detection algorithm with better edge detection, corner recognition, and document feature analysis
- **Auto-capture**: Automatically captures images when CNIC is detected with high confidence (>75%)

### Dual-Side Scanning
- **Front and Back Side Support**: Automatically switches between front and back side scanning
- **Progress Tracking**: Visual indicators show which sides have been captured
- **Side Indicators**: Clear labeling of current side being scanned

### Background Removal
- **Automatic Background Removal**: Removes background from captured CNIC images
- **Edge-based Detection**: Samples edge colors to determine background
- **Transparency Support**: Creates transparent backgrounds for clean CNIC extraction

### Image Enhancement
- **Contrast Enhancement**: Improves text readability
- **Saturation Adjustment**: Enhances color clarity
- **Quality Optimization**: High-quality image capture and processing

### User Interface Improvements
- **Visual Feedback**: Color-coded detection box (Green = detected, Yellow = detected with lower confidence, White = not detected)
- **Real-time Instructions**: Dynamic text showing current status and instructions
- **Progress Indicators**: Shows completion status for front and back sides
- **Manual Fallback**: Manual capture button for cases where auto-capture doesn't trigger

## Technical Improvements

### Detection Algorithm
- **Enhanced Edge Detection**: Improved gradient-based edge detection
- **Corner Recognition**: Detects strong edges at corners for better rectangular shape validation
- **Document Feature Analysis**: Identifies structured content typical in official documents
- **Text Density Analysis**: Detects text-like patterns for document validation

### Image Processing
- **Background Removal Algorithm**: 
  - Samples edge colors to determine background
  - Calculates color distance for pixel classification
  - Creates transparent backgrounds for clean extraction
- **Enhanced Cropping**: Improved CNIC boundary detection with safety bounds
- **Quality Enhancement**: Multiple color matrix operations for optimal image quality

### Error Handling
- **Robust Error Handling**: Comprehensive try-catch blocks for all operations
- **Fallback Mechanisms**: Multiple detection methods ensure reliability
- **Safe Bounds Checking**: Prevents array out-of-bounds errors

## Usage

1. **Launch the App**: Open the CNIC Scanner app
2. **Position CNIC**: Place the CNIC within the detection frame
3. **Auto-capture**: The app will automatically capture when CNIC is detected
4. **Switch Sides**: After capturing front side, position the back side
5. **Manual Capture**: Use manual capture button if needed
6. **View Results**: Processed images are saved to gallery with background removed

## Requirements

- Android 6.0 (API level 24) or higher
- Camera permission
- Storage permission for saving images

## Dependencies

- AndroidX Camera libraries
- Compose UI framework
- Kotlin standard library
- AndroidX ExifInterface for image metadata

## Installation

1. Clone the repository
2. Open in Android Studio
3. Build and run on device or emulator
4. Grant necessary permissions when prompted

## Technical Details

### Detection Confidence Levels
- **High Confidence (>75%)**: Auto-capture enabled, green detection box
- **Medium Confidence (50-75%)**: Yellow detection box, manual capture recommended
- **Low Confidence (<50%)**: White detection box, manual positioning needed

### Supported CNIC Features
- **English Text**: "CNIC", "NIC", "Identity Card", "Pakistan", "NADRA", etc.
- **Urdu Text**: "قومی شناختی کارڈ", "شناختی کارڈ", "پاکستان", etc.
- **CNIC Numbers**: 13-digit format with dashes (XXXXX-XXXXXXX-X)
- **Document Structure**: Rectangular shape with proper aspect ratio (1.6:1)

### Image Processing Pipeline
1. **Capture**: High-quality image capture
2. **Detect**: Enhanced CNIC detection
3. **Crop**: Intelligent boundary detection
4. **Remove Background**: Edge-based background removal
5. **Enhance**: Contrast and saturation optimization
6. **Save**: High-quality output to gallery

## Future Enhancements

- ML Kit integration for advanced text recognition
- OCR for extracting CNIC data
- Cloud storage integration
- Multiple document type support
- Advanced image filters and effects 