# Testing Phase 2 on a Real Device

## Prerequisites

1. **Android Device Requirements**
   - Android 8.0 (API 26) or higher
   - Camera (back camera preferred)
   - USB debugging enabled

2. **Enable USB Debugging**
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times to enable Developer Options
   - Go to Settings → Developer Options
   - Enable "USB Debugging"

## Installation Steps

### Option 1: Install via Android Studio (Recommended)
```bash
# 1. Connect your device via USB
# 2. Accept the USB debugging prompt on your device
# 3. Run from Android Studio or command line:
./gradlew installDebug

# 4. The app will be installed as "Shot Tracker (Debug)"
```

### Option 2: Build and Install APK Manually
```bash
# 1. Build the APK
./gradlew assembleDebug

# 2. The APK will be at:
# app/build/outputs/apk/debug/app-debug.apk

# 3. Install via adb:
adb install app/build/outputs/apk/debug/app-debug.apk

# Or transfer the APK to your device and install manually
```

## Testing Checklist

### 1. First Launch - Permission Flow
- [ ] Launch the app
- [ ] Navigate to Session screen
- [ ] Camera permission prompt appears
- [ ] Grant permission
- [ ] Camera preview displays

### 2. Camera Preview
- [ ] Camera shows live preview
- [ ] Preview is full-screen
- [ ] Preview is smooth (no lag)
- [ ] Point camera at basketball hoop

### 3. Shot Detection
- [ ] Shoot a basketball
- [ ] Green flash appears on successful detection
- [ ] Haptic vibration triggers
- [ ] Made count increments
- [ ] Percentage updates correctly

### 4. Manual Controls
- [ ] Tap "+" on Made → count increases
- [ ] Tap "-" on Made → count decreases
- [ ] Tap "+" on Missed → attempts increase
- [ ] Tap "-" on Missed → attempts decrease
- [ ] Percentage recalculates correctly

### 5. Detection States
- [ ] Idle state: no visual feedback
- [ ] Detecting: yellow tint appears
- [ ] Confirmed: green flash appears
- [ ] Cooldown period works (2 seconds between detections)

### 6. Session Management
- [ ] Stats overlay visible over camera
- [ ] End Session button works
- [ ] Returns to home screen (summary not yet implemented)

### 7. Edge Cases
- [ ] Rotate device → camera stays portrait
- [ ] Home button → app pauses
- [ ] Return to app → camera resumes
- [ ] Deny permission → permission screen shows
- [ ] Grant later from settings → camera works

## Expected Behavior

### Detection Accuracy
- **Good lighting (outdoors/gym)**: ~70% accuracy
- **Poor lighting (dim indoor)**: Lower accuracy
- **False positives**: ~15% (acceptable for MVP)

### Performance
- Camera preview latency: < 100ms
- Detection response: < 1 second
- No noticeable lag or stuttering

## Troubleshooting

### Camera doesn't show
**Issue**: Black screen after granting permission
**Solution**: 
- Check logcat: `adb logcat | grep CameraPreview`
- Restart the app
- Ensure camera is not being used by another app

### No detection happening
**Issue**: Motion detected but count doesn't increase
**Solution**:
- Check sensitivity (threshold may need tuning)
- Ensure good lighting conditions
- Try moving object quickly in front of camera
- Check logcat: `adb logcat | grep ShotDetector`

### App crashes
**Issue**: App force closes
**Solution**:
- Check crash logs: `adb logcat | grep AndroidRuntime`
- Report the stack trace
- Try clean install: `./gradlew uninstallDebug installDebug`

### No haptic feedback
**Issue**: No vibration on detection
**Solution**:
- Check device vibration settings
- Some devices may have vibration disabled
- Check logcat: `adb logcat | grep FeedbackManager`

## Viewing Logs

```bash
# View all logs
adb logcat

# Filter Shot Tracker logs only
adb logcat | grep "ShotTracker\|ShotDetector\|CameraPreview\|FeedbackManager"

# Clear logs and start fresh
adb logcat -c && adb logcat

# Save logs to file
adb logcat > logs.txt
```

## Testing Tips

1. **Start Simple**: Test with hand movements first before basketball
2. **Good Lighting**: Test outdoors or in well-lit area
3. **Frame the Hoop**: Keep hoop in upper portion of screen
4. **Manual Override**: Always use manual controls to correct errors
5. **Multiple Shots**: Try 10-20 shots to gauge accuracy

## Known Limitations (MVP)

- Detection is basic (no OpenCV yet)
- No directional motion analysis
- Fixed ROI (no calibration UI)
- Portrait orientation only
- No session persistence (Phase 3)

## Reporting Issues

If you encounter issues, provide:
1. Device model and Android version
2. Lighting conditions
3. What you were doing when it happened
4. Logcat output
5. Expected vs actual behavior

## Next Steps After Testing

Based on your testing results:
- Tune detection threshold if needed
- Adjust ROI if hoop position is problematic
- Note accuracy for different conditions
- Identify any bugs or crashes

Ready to proceed to Phase 3 once testing confirms basic functionality works!
