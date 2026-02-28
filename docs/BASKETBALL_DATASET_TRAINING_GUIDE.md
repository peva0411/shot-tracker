# Basketball Dataset + Training Guide (Mobile-First)

This guide gives you a practical path to train a better basketball detector and deploy it to Android with TensorFlow Lite.

---

## 0) What this guide optimizes for

- Better **basketball ball** detection than generic COCO models
- Mobile-friendly model size and latency
- Reproducible commands from your existing project setup

---

## 1) Prerequisites

From your machine:

```bash
cd ~/projects/shot-tracker
source .venv-ml/bin/activate
pip install -U ultralytics kaggle roboflow
```

---

## 2) Where to get datasets

Use a mix of public datasets + your own frames.

### Option A (recommended starting point): Kaggle public datasets

1. Basketball Object Tracking Dataset  
   https://www.kaggle.com/datasets/trainingdatapro/basketball-tracking-dataset
2. Basketball and Player Object Detection  
   https://www.kaggle.com/datasets/zhengshunyuan/basketball-and-player-object-detection

### Option B: Roboflow Universe datasets (YOLO-export friendly)

- Universe dataset download docs:  
  https://docs.roboflow.com/universe/download-a-universe-dataset
- CLI dataset download docs:  
  https://docs.roboflow.com/developer/command-line-interface/download-a-dataset

Use Universe search for basketball/ball/rim datasets and export in YOLO format:
- https://universe.roboflow.com/

### Option C: Your own data (important for real accuracy)

Extract frames from your own shot videos and label `basketball` (and optional `rim`) so training data matches your camera angle, lighting, and motion blur.

---

## 3) Download datasets

### 3.1 Configure Kaggle API once

1. Go to https://www.kaggle.com/account and create an API token (`kaggle.json`).
2. Save it locally:

```bash
mkdir -p ~/.kaggle
mv ~/Downloads/kaggle.json ~/.kaggle/kaggle.json
chmod 600 ~/.kaggle/kaggle.json
```

### 3.2 Download Kaggle datasets

```bash
cd ~/projects/shot-tracker
mkdir -p datasets/raw
cd datasets/raw

kaggle datasets download -d trainingdatapro/basketball-tracking-dataset
kaggle datasets download -d zhengshunyuan/basketball-and-player-object-detection

unzip -o basketball-tracking-dataset.zip -d basketball-tracking
unzip -o basketball-and-player-object-detection.zip -d basketball-player
```

### 3.3 (Optional) Download a Roboflow Universe dataset with CLI

```bash
# Format: roboflow download -f <format> -l <download-location> <workspace>/<project>/<version>
roboflow download -f yolov8 -l ~/projects/shot-tracker/datasets/raw/rf-dataset <workspace>/<project>/<version>
```

---

## 4) Create your training dataset structure

Target structure:

```text
datasets/basketball/
  images/{train,val,test}
  labels/{train,val,test}
  data.yaml
```

Create folders:

```bash
cd ~/projects/shot-tracker
mkdir -p datasets/basketball/images/{train,val,test}
mkdir -p datasets/basketball/labels/{train,val,test}
```

---

## 5) Convert/normalize annotations to YOLO format

Different sources use different formats (XML/COCO/YOLO). Convert everything to YOLO text labels:

- one `.txt` per image
- line format: `<class_id> <x_center> <y_center> <width> <height>` (normalized 0-1)

If a dataset is already YOLO, copy image/label pairs directly into `images/*` and `labels/*`.

For XML/COCO datasets, convert before merging (CVAT, FiftyOne, or a small Python converter script).  
Keep labels minimal at first:

- Class `0`: `basketball`
- (optional later) Class `1`: `rim`

---

## 6) Add your own video frames (high impact)

Extract frames from your real usage videos:

```bash
mkdir -p ~/projects/shot-tracker/datasets/raw/my-frames
ffmpeg -i /path/to/your_video.mp4 -vf "fps=5" ~/projects/shot-tracker/datasets/raw/my-frames/frame_%06d.jpg
```

Label these frames (CVAT/Roboflow/Label Studio), then export in YOLO format and merge into `datasets/basketball`.

---

## 7) Split train/val/test

Target ratio:
- train: 80%
- val: 10%
- test: 10%

Keep splits scene-aware (avoid near-duplicate frames across train/val/test from the same video segment).

---

## 8) Create `data.yaml`

Create file:

`~/projects/shot-tracker/datasets/basketball/data.yaml`

```yaml
path: /home/phil/projects/shot-tracker/datasets/basketball
train: images/train
val: images/val
test: images/test

names:
  0: basketball
  # 1: rim
```

---

## 9) Train a mobile-friendly model

Start with YOLO11n (good speed/quality balance):

```bash
cd ~/projects/shot-tracker
source .venv-ml/bin/activate

yolo train \
  model=yolo11n.pt \
  data=datasets/basketball/data.yaml \
  imgsz=960 \
  epochs=80 \
  batch=16 \
  device=0
```

If VRAM is limited: reduce `imgsz` to 768 or 640, reduce `batch` to 8/4.

---

## 10) Validate and test

```bash
yolo val model=runs/detect/train/weights/best.pt data=datasets/basketball/data.yaml

# Ball-only predictions on sample images
yolo predict model=runs/detect/train/weights/best.pt source=test-media/images conf=0.10 save=true
```

For videos:

```bash
yolo predict model=runs/detect/train/weights/best.pt source=/path/to/test_video.mp4 conf=0.10 save=true
```

---

## 11) Export to TensorFlow Lite for Android

### FP16 export (usually easiest first)

```bash
yolo export model=runs/detect/train/weights/best.pt format=tflite imgsz=640 half=True
```

### INT8 export (smaller/faster, may reduce accuracy)

```bash
yolo export \
  model=runs/detect/train/weights/best.pt \
  format=tflite \
  imgsz=640 \
  int8=True \
  data=datasets/basketball/data.yaml
```

Then copy chosen model into app assets:

```bash
mkdir -p app/src/main/assets/ml
cp runs/detect/train/weights/*_saved_model/*.tflite app/src/main/assets/ml/basketball_detector.tflite
```

---

## 12) Build app and test on device

```bash
cd ~/projects/shot-tracker
./gradlew assembleDebug
./gradlew installDebug
```

Test on real shooting videos and confirm:
- ball recall in motion blur
- false positives vs people/background
- frame rate on target device

---
## 13) Practical optimization loop (important)

1. Collect false negatives/false positives from real sessions.
2. Add those frames to dataset.
3. Retrain from last best weights.
4. Re-export TFLite.
5. Re-test on device.

This loop usually matters more than switching architectures repeatedly.
---

## 14) Recommended first milestone

1. Build a **single-class `basketball` dataset** of at least 2k-5k labeled frames.
2. Train `yolo11n`.
3. Export FP16 TFLite.
4. Verify mobile FPS + real shot clips.
5. Only then test alternatives (larger YOLO11 variants, SAHI, tracking additions).

---

## 15) Notes on licensing

Before training/commercial use, verify each dataset license/terms on Kaggle/Roboflow.

