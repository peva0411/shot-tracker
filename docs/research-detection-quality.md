# Research: Improving Ball Detection Quality

**Context:** Screen recording analysis (2026-03-03) showed that ~41ms inference at frame-skip-1
is fast enough to sample a shot arc ~24 times per second — sufficient temporal resolution.
The problem is **false positives**: gym lights, floor reflections, and bright circular objects
are regularly detected as the ball, producing a noisy trail that would make arc reconstruction
unreliable on real shots.

Three approaches are explored here:

1. [Temporal / Trajectory Filtering](#1-temporal--trajectory-filtering)
2. [Motion Gating](#2-motion-gating)
3. [Improved Training Data](#3-improved-training-data)

---

## 1. Temporal / Trajectory Filtering

### Idea
A real basketball in flight follows a **parabolic arc** under gravity. False positives (lights,
reflections) are stationary or move in ways that are physically impossible for a thrown ball.
We can exploit this by filtering the detection stream against a running kinematic model —
keeping only detections that are consistent with a plausible trajectory.

### Current State
`TrajectoryTracker` already maintains a ring buffer of the last 30 `BallPosition` samples and
exposes `verticalVelocity()` (via a 500ms rolling window). `ShotAnalyzer` uses this for
shot-confirmation logic (arc check, downward velocity). However, **no filtering is applied to
incoming detections before they enter the trail buffer** — every detection above the confidence
threshold is accepted.

### Approach A: Kalman Filter on the Position Stream

A Kalman filter maintains a state estimate `[x, y, vx, vy]` and produces a predicted position
for each new frame. An incoming detection is only accepted if it falls within a gated region
(the **innovation gate**) around the prediction.

```
State:     x, y, vx, vy
Predict:   x' = x + vx·dt,  y' = y + vy·dt + ½g·dt²  (add gravity to vy model)
Update:    only if ||z - Hx'|| < k·σ   (Mahalanobis distance gate)
```

**Pros:**
- Well-understood, computationally trivial
- The app already uses a Kalman predictor in `BallDetector` for skipped-frame interpolation —
  that same filter could gate incoming detections instead of just filling gaps
- Naturally handles the transition from "ball at rest → ball in flight" by widening the gate
  when velocity is uncertain

**Cons:**
- Requires the filter to be initialised with a confident first detection
- A stationary false positive sitting in the scene can "seed" a bad Kalman state
- Needs tuning of process noise Q and measurement noise R for basketball dynamics

**Implementation pointer:** Extend the existing Kalman state in `BallDetector` — before
accepting a new detection, check its distance from the current prediction. Reject detections
outside `k=3` standard deviations. Reset the filter state if no valid detection arrives for
>500ms (ball out of frame or possession change).

---

### Approach B: Parabolic RANSAC

Rather than frame-by-frame gating, accumulate a short window of candidate detections
(e.g. last 1.5s) and **fit a parabola** to the (x, y) positions. Use RANSAC to find the
inlier set — detections that lie close to the best-fit parabola are the real ball; outliers
are false positives. Retroactively clean the trail buffer.

```
For each frame window:
    candidates = all detections in last N frames
    RANSAC:
        sample 3 points → fit y = ax² + bx + c
        count inliers within ε pixels
    keep best fit if inlier ratio > threshold
    mark non-inliers as false positives, remove from trail
```

**Pros:**
- Highly robust to isolated false positives
- Cleans up old trail data retroactively (useful for arc display after the shot)
- The parabolic constraint is physically well-motivated

**Cons:**
- Needs enough real detections to converge (~5–8 points)
- More CPU than Kalman; probably runs as a post-processing step after the shot, not
  frame-by-frame during flight
- Won't help in real-time during the shot, only for trail cleanup and arc reconstruction

**Implementation pointer:** Run RANSAC in `ShotAnalyzer.analyze()` after a `ShotEvent` is
tentatively detected. Clean the `TrajectoryTracker` buffer before scoring the arc quality.

---

### Approach C: Gravity-Consistent Velocity Check

Simpler than full Kalman: between consecutive accepted detections, check that the vertical
acceleration is within physical bounds (gravity ≈ 9.8 m/s²). If two back-to-back detections
imply an upward acceleration greater than what a player could throw, one of them is a false
positive — discard the lower-confidence one.

This is cheap to implement as a filter step in `TrajectoryTracker.add()`.

---

## 2. Motion Gating

### Idea
False positives in this gym are largely **stationary** — ceiling lights and floor reflections
don't move between frames. A real basketball in flight moves substantially. We can gate on
the detected object's displacement from the previous frame: below a minimum displacement
threshold, reject the detection (or down-weight it heavily).

### Current State
No displacement check exists. Every detection above the confidence threshold is accepted
regardless of whether the detected position has moved.

### Approach A: Inter-Frame Displacement Threshold

After a detection at position `(x, y)`, require that the next accepted detection is at least
`d_min` normalised units away. `d_min` should correspond to the minimum plausible ball
displacement in one detection interval.

```
At 24Hz effective detection rate:
  Minimum throw speed ~3 m/s → ~0.003 normalised units/frame at typical camera distance
  Set d_min = 0.01 (normalised) as a conservative floor
```

If the new detection is closer than `d_min` to the last accepted position, it is likely
a stationary false positive — reject it during active tracking, or give it zero weight in
the Kalman update.

**Pros:** Trivial to implement in `TrajectoryTracker.add()`, very low cost.

**Cons:**
- Breaks when the ball is genuinely stationary (held by player, sitting on floor)
- The threshold is scene- and distance-dependent
- Needs to be disabled during possession / pre-shot phase

**Implementation pointer:** Gate only when `DetectionState == DETECTING` or `CONFIRMED`
(i.e. a shot is in progress). During `IDLE`, accept all detections so the tracker can find
the ball at rest before the shot.

---

### Approach B: Optical Flow Verification

For each candidate detection bounding box, compute **sparse optical flow** (Lucas-Kanade)
on a grid of points inside the box between the previous and current frame. If the median
flow magnitude is below a threshold, the object is stationary → likely a false positive.

```
For candidate box at (cx, cy, w, h):
    sample 9 points in a 3×3 grid inside the box
    compute LK flow from frame t-1 → frame t
    if median(|flow|) < flow_threshold: reject detection
```

**Pros:**
- Much more robust than displacement of the detection box centre (which can jump around
  for low-confidence detections)
- Works even if the ball moves back into the same screen position it occupied earlier
- OpenCV is already available on Android via the `org.opencv` dependency if it's added

**Cons:**
- Non-trivial to add OpenCV (or equivalent) to the build
- Adds latency (~5–15ms per frame on a Pixel 6-class device)
- CameraX `ImageAnalysis` delivers `YUV_420_888`; converting to a grayscale Mat for
  flow computation is straightforward but requires careful memory management

**Simpler alternative:** Instead of full optical flow, compute the **pixel intensity
variance** inside the bounding box across two consecutive frames. High variance = motion,
low variance = stationary. This avoids the OpenCV dependency entirely and runs in O(box_pixels).

---

### Approach C: Size Consistency Gate

The apparent size of the ball changes predictably during a shot — it gets smaller as it
travels away and larger as it comes toward the camera. A detected bounding box that is wildly
larger or smaller than the previous frame's box (e.g. 3× change in area) is almost certainly
a false positive. Gate on box area ratio between consecutive detections.

---

## 3. Improved Training Data

### Why This Is The Foundation

Approaches 1 and 2 are filters on top of a noisy signal. If the underlying YOLO model
produces high-confidence detections of gym lights (which it currently does), filtering
will always be a game of whack-a-mole. The right fix is teaching the model that gym
lights are **not** basketballs. This is a **hard negative mining** problem.

### Current Model
`yolo11n.pt` — a general-purpose nano YOLO model. It was not fine-tuned for basketball
tracking specifically. The `sports ball` class in COCO includes footballs, baseballs, etc.,
and the model has never seen a gym ceiling with sodium-vapour lights that look like balls.

### What the Training Data Needs

#### A. Hard Negatives: Gym Features That Look Like Balls
The model needs many examples of things it is currently false-positive-ing on, labelled
as background (no annotation):

- **Ceiling lights** — round, bright, often orange-tinted (especially sodium vapour)
- **Floor reflections** — bright oval patches on polished gym floors
- **Hoop backboard** — the circular hoop ring itself, seen at certain angles
- **Player's head** — round, flesh-toned, moving

The current `training/` directory captures images from the live session — these could be
reviewed and annotated with explicit "this is a light, not a ball" labels, or simply
left un-annotated for the ball class (so the model learns to suppress them).

#### B. Ball In Flight: Underrepresented Poses
The model likely sees very few examples of:

- **Motion-blurred ball** — at realistic throw speeds (~7–10 m/s) and 1/30s shutter,
  the ball streaks ~20–30cm across the frame. YOLO may simply not detect this at all.
  Fix: capture frames specifically during the flight phase, or synthetically add motion
  blur to existing ball images during training augmentation.
- **Ball at distance** — near the peak of arc and near the basket, the ball is small
  (perhaps 20–40px diameter at 720p). Ensure training data includes small-ball examples
  from this court distance.
- **Partial occlusion** — ball obscured by net, rim, player's hand on release.
- **Ball against bright background** — orange ball against pale gym ceiling is a hard case.

#### C. Data Collection Strategy Using the App
The app already captures frames via `TrainingCaptureController` into `files/training/`.
A targeted collection session could:

1. **Capture during known-false-positive moments** — point the camera at the ceiling
   lights for 30 seconds to harvest light images with no ball label
2. **Capture the shot arc specifically** — trigger capture only during `DetectionState
   == DETECTING` to bias toward in-flight frames
3. **Annotate using Roboflow or Label Studio** — the captured JPEGs are already
   full-resolution (3–4MB each); they can be uploaded directly

#### D. Augmentation Recommendations
During training, augment aggressively for the hard cases:

| Augmentation | Rationale |
|---|---|
| Motion blur (horizontal + arc direction) | Simulate in-flight blur |
| Mosaic + small object emphasis | Improve small-ball detection |
| HSV shifts (orange ↔ yellow) | Ball colour varies by brand/lighting |
| Mixup with gym background | Teach separation from background |
| Heavy brightness/contrast variation | Sodium vapour vs. LED lighting |

#### E. Model Size Consideration
`yolo11n` is ~2.6M parameters, chosen for speed. After fine-tuning on basketball data,
inference quality for this specific task should improve significantly without needing a
larger model. However, if false positives persist after fine-tuning, `yolo11s` (~9M params)
runs in ~80–100ms on Pixel 6-class hardware — still fast enough at frame-skip-2 (every
other frame) since the Kalman predictor fills the gaps.

---

## Recommended Approach

These three approaches are complementary, not mutually exclusive. A practical sequence:

1. **Start with motion gating (Approach 2A)** — 10 lines of code in `TrajectoryTracker`,
   immediately eliminates stationary false positives, no model changes needed.

2. **Add Kalman gating (Approach 1A)** — extend the existing Kalman predictor in
   `BallDetector` to gate incoming detections. This handles the residual moving false
   positives (e.g. bouncing light reflections).

3. **Collect and annotate hard negatives** — run targeted capture sessions in the gym,
   annotate, fine-tune the model. This fixes the root cause and reduces how hard the
   filters need to work.

4. **RANSAC parabola fit (Approach 1B)** — add as a post-shot cleanup step for high-quality
   arc visualisation after the shot is confirmed.
