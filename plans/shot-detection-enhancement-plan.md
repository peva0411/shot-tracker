# Shot Detection Enhancement Plan

## Problem Analysis

### Current Implementation Issues
The phase 2 implementation uses basic motion detection that is fundamentally flawed for basketball shot tracking:

1. **No Object Detection**: The current algorithm detects ANY motion, not specifically basketball shots
   - Uses simple frame differencing on luminance values
   - Cannot distinguish between:
     - Ball moving toward hoop (shot)
     - Person walking past camera
     - Hand waving
     - General movement
   
2. **No Hoop Detection**: The algorithm doesn't know where the hoop is
   - Fixed ROI (Region of Interest) hardcoded to top of frame
   - No calibration system
   - Cannot adapt to different camera positions

3. **No Ball Tracking**: Cannot identify or follow the basketball
   - Just measures pixel differences
   - No trajectory analysis
   - No directional motion detection

4. **Result**: Every movement triggers a "shot made" - completely unusable for actual tracking

### Why OpenCV Wasn't Used
According to the phase 2 docs, OpenCV was intentionally skipped for the MVP to:
- Reduce complexity
- Reduce APK size
- Ship faster with "good enough" ~70% accuracy

However, the basic motion detection is **not** 70% accurate - it's essentially 0% accurate because it can't distinguish shots from general movement.

## Research Findings

### Industry Standard Approach
Modern basketball shot detection systems use a multi-stage pipeline:

1. **Object Detection** (YOLOv8, Faster R-CNN, RetinaNet)
   - Detect basketball in each frame
   - Detect hoop/rim in each frame
   - Real-time capable on mobile devices

2. **Ball Tracking**
   - Track ball position across frames
   - Build trajectory over time
   - Filter out noise and occlusions

3. **Trajectory Analysis**
   - Predict ball path using regression models
   - Check if trajectory intersects hoop
   - Verify downward entry angle (scoring shot)

4. **Temporal Filtering**
   - Confirm detection across multiple frames
   - Reduce false positives
   - Handle occlusions

### Mobile Implementation Options

#### Option 1: YOLOv8 + TensorFlow Lite (Recommended)
**Pros:**
- State-of-the-art accuracy (95%+ reported in research)
- Real-time on mobile devices
- Pre-trained models available
- Can detect both ball and hoop simultaneously
- Active community support
- Smaller than OpenCV

**Cons:**
- Requires TFLite model (~5-10MB)
- Need to train or fine-tune on basketball dataset
- Learning curve for YOLO integration

**Implementation:**
- Export YOLOv8 model to TensorFlow Lite format
- Add TFLite runtime to Android app (~3MB)
- Run inference on camera frames
- Track detections across frames

#### Option 2: OpenCV + Traditional CV
**Pros:**
- Well-documented for Android
- Good for basic shape/color detection
- No ML model training needed

**Cons:**
- Lower accuracy than ML approaches
- Struggles with varying lighting
- More manual tuning required
- Larger library size (~20MB)

#### Option 3: Hybrid Approach
**Pros:**
- Use TFLite for detection
- Use OpenCV for trajectory math
- Best of both worlds

**Cons:**
- Larger APK size
- More dependencies

## Recommended Solution

### Phase 2.5: TensorFlow Lite + YOLO-based Detection

Implement a proper computer vision pipeline using YOLOv8 via TensorFlow Lite:

1. **Ball Detection**
   - Use pre-trained YOLOv8n-TFLite model (detects sports balls)
   - Process every 3rd frame for performance
   - Extract ball bounding box coordinates

2. **Hoop Detection** 
   - Detect hoop in initial calibration phase
   - Store hoop position (can be stationary)
   - Optional: Re-detect periodically to handle camera movement

3. **Shot Logic**
   - Track ball position over time (last 10-15 frames)
   - Detect upward trajectory followed by downward trajectory
   - Check if ball path intersects hoop area
   - Confirm detection over 0.5-1 second window

4. **Fallback to Manual**
   - Keep manual +/- controls for corrections
   - Show confidence score on detections

### Technical Architecture

```
CameraX Frame
    ↓
Image Preprocessing (resize, normalize)
    ↓
TFLite Inference (YOLOv8)
    ↓
Ball & Hoop Detection
    ↓
Trajectory Tracker
    ↓
Shot Analyzer
    ↓
Shot Confirmed/Rejected
    ↓
Update Session Stats
```

## Implementation Workplan

### Phase A: Research & Setup
- [x] Download pre-trained YOLOv8n model for object detection *(pending local model artifact)*
- [x] Convert model to TensorFlow Lite format (.tflite) *(pending model download)*
- [ ] Test model on sample basketball images/videos *(pending model artifact and test media)*
  - Test command: `cd ~/projects/shot-tracker && source .venv-ml/bin/activate && yolo predict task=detect model=app/src/main/assets/ml/yolov8n.tflite source=test-media/images classes=32 conf=0.05 imgsz=1280 save=true project=test-results name=phaseA-tflite-ball-only exist_ok=True`
- [x] Add TensorFlow Lite dependencies to Android project
- [x] Create basic TFLite inference wrapper class

### Phase B: Ball Detection
- [ ] Create `BallDetector.kt` using TFLite
- [ ] Integrate with CameraX image analysis pipeline
- [ ] Draw debug overlay showing detected ball bounding box
- [ ] Test detection accuracy in various lighting
- [ ] Optimize inference performance (frame skipping, threading)

### Phase C: Hoop Detection
- [ ] Add hoop detection to TFLite model or use color/shape detection
- [ ] Create calibration screen for hoop positioning
- [ ] Store hoop coordinates in session state
- [ ] Draw debug overlay showing hoop region
- [ ] Handle cases where hoop isn't detected

### Phase D: Trajectory Analysis
- [ ] Create `TrajectoryTracker.kt` class
- [ ] Store last N ball positions with timestamps
- [ ] Calculate velocity and direction vectors
- [ ] Detect upward-then-downward motion pattern
- [ ] Check intersection with hoop area
- [ ] Implement temporal filtering (confirm over multiple frames)

### Phase E: Shot Logic Integration
- [ ] Replace old `ShotDetector.kt` with new pipeline
- [ ] Integrate ball detector + trajectory tracker
- [ ] Add shot confirmation logic
- [ ] Update `SessionViewModel` to consume new detector
- [ ] Keep manual controls as fallback
- [ ] Add confidence score display

### Phase F: Testing & Tuning
- [ ] Test with real basketball hoop setup
- [ ] Measure detection accuracy (made vs missed)
- [ ] Tune thresholds (trajectory angle, intersection area, etc.)
- [ ] Test in different lighting conditions
- [ ] Test with different camera distances
- [ ] Optimize battery usage

### Phase G: Documentation & Polish
- [ ] Update architecture docs
- [ ] Create troubleshooting guide
- [ ] Add inline code comments
- [ ] Update README with accuracy metrics
- [ ] Create demo video

## Success Criteria

1. **Accuracy**: Detect >85% of actual shots made
2. **False Positives**: <10% of detections are false positives
3. **Performance**: Maintain 20+ FPS camera preview
4. **Battery**: <20% battery drain per hour of use
5. **Usability**: Works in indoor and outdoor lighting

## Alternative Approaches (If TFLite Fails)

### Fallback 1: Color + Motion Hybrid
- Detect orange ball using HSV color space
- Combine with upward motion detection
- Lower accuracy but no ML model needed

### Fallback 2: Manual-Only Mode
- Disable auto-detection entirely
- Rely on manual +/- buttons
- Add quick-tap shortcuts for faster input

### Fallback 3: Cloud-based Detection
- Send frames to cloud API for detection
- Higher accuracy but requires internet
- Not suitable for MVP (offline-first)

## Resources & References

### Models
- YOLOv8n pre-trained on COCO (includes sports ball class)
- Custom basketball dataset for fine-tuning (optional)

### Repositories
- https://github.com/avishah3/AI-Basketball-Shot-Detection-Tracker
- https://github.com/superbabiiX/YOLOv8-AndroidApp-Tflite

### Papers
- Basketball Detection based on YOLOv8 (PLOS One 2025)
- ScoreActuary: Hoop-Centric Trajectory-Aware Network

### Tutorials
- TensorFlow Lite object detection: https://developers.google.com/codelabs/tflite-object-detection-android
- YOLOv8 Android implementation guides

## Timeline Estimate

**Note**: Timeline depends on familiarity with TensorFlow Lite and model training.

- Phase A (Research & Setup): Research phase
- Phase B (Ball Detection): Core detection implementation
- Phase C (Hoop Detection): Hoop calibration system
- Phase D (Trajectory Analysis): Motion tracking logic
- Phase E (Integration): Connect all pieces
- Phase F (Testing): Real-world validation
- Phase G (Documentation): Final polish

## Decision Points

Before starting implementation, need to decide:

1. **Model Source**: Use pre-trained COCO model or train custom basketball model?
2. **Hoop Detection**: ML-based or manual calibration UI?
3. **Performance Target**: 30 FPS vs 15 FPS (affects frame skip rate)
4. **Scope**: Both made/missed detection or just "shot attempted"?

## Notes

- Current ShotDetector.kt should be completely replaced, not patched
- Manual controls must remain as fallback
- Consider adding "Auto-detect" toggle in UI for testing
- May need larger test dataset for fine-tuning
- Consider adding shot video replay feature for debugging
