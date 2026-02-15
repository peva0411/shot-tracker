# OpenCV Integration Guide

## Overview
This document describes how to integrate OpenCV for Android into the Shot Tracker app for enhanced motion detection capabilities.

## Current State (MVP)
The MVP uses a basic motion detection algorithm that doesn't require OpenCV. This provides:
- Simple frame differencing
- Luminance-based motion detection
- Good enough accuracy for initial testing (~70%)

## Why OpenCV?
For improved detection accuracy in Phase 2+, OpenCV provides:
- Gaussian blur for noise reduction
- Advanced motion vector analysis
- Directional motion detection
- Better handling of lighting conditions
- Background subtraction algorithms

## Installation Steps

### 1. Download OpenCV Android SDK
Download from: https://opencv.org/releases/
- Recommended version: OpenCV 4.9.0 (or latest 4.x)
- Download the Android package (opencv-4.x.x-android-sdk.zip)

### 2. Extract and Add to Project
```bash
# Extract the downloaded file
unzip opencv-4.9.0-android-sdk.zip

# Copy the AAR file to your project
cp opencv-4.9.0-android-sdk/sdk/native/libs/opencv-*.aar \
   app/libs/opencv-4.9.0.aar
```

### 3. Update build.gradle.kts
Uncomment the OpenCV dependency in `app/build.gradle.kts`:
```kotlin
dependencies {
    // ... other dependencies
    
    // OpenCV
    implementation(files("libs/opencv-4.9.0.aar"))
}
```

### 4. Update ProGuard Rules
Add to `app/proguard-rules.pro`:
```proguard
# OpenCV
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**
```

### 5. Initialize OpenCV in Application
Update `ShotTrackerApplication.kt`:
```kotlin
import org.opencv.android.OpenCVLoader

class ShotTrackerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize OpenCV
        if (OpenCVLoader.initLocal()) {
            Log.d("OpenCV", "OpenCV loaded successfully")
        } else {
            Log.e("OpenCV", "OpenCV initialization failed")
        }
    }
}
```

## Enhanced Shot Detection with OpenCV

Once OpenCV is integrated, you can enhance `ShotDetector.kt` with:

```kotlin
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

private fun detectMotionWithOpenCV(
    prevMat: Mat,
    currentMat: Mat
): ShotDetectionResult {
    // Convert to grayscale
    val gray1 = Mat()
    val gray2 = Mat()
    Imgproc.cvtColor(prevMat, gray1, Imgproc.COLOR_RGBA2GRAY)
    Imgproc.cvtColor(currentMat, gray2, Imgproc.COLOR_RGBA2GRAY)
    
    // Apply Gaussian blur to reduce noise
    Imgproc.GaussianBlur(gray1, gray1, Size(5.0, 5.0), 0.0)
    Imgproc.GaussianBlur(gray2, gray2, Size(5.0, 5.0), 0.0)
    
    // Calculate absolute difference
    val diff = Mat()
    Core.absdiff(gray1, gray2, diff)
    
    // Apply threshold
    val thresh = Mat()
    Imgproc.threshold(diff, thresh, 30.0, 255.0, Imgproc.THRESH_BINARY)
    
    // Count non-zero pixels in ROI
    val roi = thresh.submat(roiRect)
    val motionPixels = Core.countNonZero(roi)
    
    // Calculate confidence
    val totalPixels = roi.rows() * roi.cols()
    val confidence = motionPixels.toFloat() / totalPixels
    
    // Detect upward motion using optical flow (future enhancement)
    // val flow = Mat()
    // Video.calcOpticalFlowFarneback(gray1, gray2, flow, ...)
    
    // Cleanup
    gray1.release()
    gray2.release()
    diff.release()
    thresh.release()
    roi.release()
    
    return ShotDetectionResult(
        isShot = confidence > MOTION_THRESHOLD,
        confidence = confidence
    )
}
```

## Performance Considerations

- Release Mat objects immediately after use to prevent memory leaks
- Process frames at reduced resolution (720p sufficient)
- Consider processing every 2nd or 3rd frame
- Use native OpenCV methods (faster than Java/Kotlin)

## Testing

After integration:
1. Test with different lighting conditions
2. Verify memory usage doesn't increase over time
3. Check battery impact
4. Measure detection accuracy improvement

## Troubleshooting

**Issue**: OpenCV not loading
- **Solution**: Check ABI compatibility (arm64-v8a, armeabi-v7a)
- Verify AAR file is in `app/libs/` directory

**Issue**: Memory leaks
- **Solution**: Ensure all Mat.release() calls are made
- Use try-finally blocks

**Issue**: Performance degradation
- **Solution**: Reduce processing frequency
- Lower frame resolution
- Profile with Android Studio profiler

## References

- [OpenCV Android Tutorial](https://opencv.org/android/)
- [OpenCV Documentation](https://docs.opencv.org/4.x/)
- [Motion Detection Algorithms](https://docs.opencv.org/4.x/d7/df3/group__imgproc__motion.html)
