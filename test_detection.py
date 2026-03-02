#!/usr/bin/env python3
"""
Offline test harness that mirrors the Android BallDetector pipeline exactly.

Usage:
  python test_detection.py                          # run on all test-media/images
  python test_detection.py path/to/image.jpg        # run on a single image
  python test_detection.py path/to/video.mp4        # run on a video (samples frames)
  python test_detection.py --threshold 0.15 ...     # override confidence threshold

Outputs annotated images/frames to test-media/output/
"""
import argparse, os, sys
import numpy as np
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

# ── constants matching BallDetector.kt ──────────────────────────────
MODEL_PATH = "app/src/main/assets/ml/yolo11n.tflite"
INPUT_SIZE = 640
DEFAULT_THRESHOLD = 0.3

def load_interpreter(model_path):
    import tensorflow as tf
    interp = tf.lite.Interpreter(model_path=model_path)
    interp.allocate_tensors()
    return interp

def preprocess(img: Image.Image) -> np.ndarray:
    """Resize to 640×640, float32 [0-1] — mirrors NormalizeOp(0,255)."""
    img_resized = img.convert("RGB").resize((INPUT_SIZE, INPUT_SIZE), Image.BILINEAR)
    arr = np.array(img_resized, dtype=np.float32) / 255.0
    return np.expand_dims(arr, axis=0)  # [1, 640, 640, 3]

def run_inference(interp, input_arr):
    inp = interp.get_input_details()[0]
    out = interp.get_output_details()[0]
    interp.set_tensor(inp["index"], input_arr)
    interp.invoke()
    return interp.get_tensor(out["index"])  # [1, 5, 8400]

def parse_best_detection(output, threshold):
    """Mirrors BallDetector.parseBestDetection — returns (cx,cy,w,h,conf) or None."""
    confs = output[0, 4, :]
    mask = confs > threshold
    if not mask.any():
        return None
    best_idx = int(np.argmax(confs))
    cx, cy, w, h, conf = output[0, :, best_idx]
    return cx, cy, w, h, float(conf)

def parse_all_detections(output, threshold):
    """Return ALL detections above threshold for visualization."""
    confs = output[0, 4, :]
    indices = np.where(confs > threshold)[0]
    dets = []
    for i in indices:
        cx, cy, w, h, conf = output[0, :, i]
        dets.append((float(cx), float(cy), float(w), float(h), float(conf)))
    # sort by confidence descending
    dets.sort(key=lambda d: d[4], reverse=True)
    return dets

def annotate_image(img: Image.Image, detections, threshold):
    """Draw bounding boxes on the original image."""
    draw = ImageDraw.Draw(img)
    iw, ih = img.size
    for cx, cy, w, h, conf in detections:
        left   = (cx - w / 2) * iw
        top    = (cy - h / 2) * ih
        right  = (cx + w / 2) * iw
        bottom = (cy + h / 2) * ih
        draw.rectangle([left, top, right, bottom], outline="cyan", width=3)
        draw.text((left, max(0, top - 14)), f"{conf:.0%}", fill="cyan")
    # stamp threshold in corner
    draw.text((8, 8), f"threshold={threshold:.0%}  detections={len(detections)}", fill="yellow")
    return img

def process_image(interp, img_path, threshold, out_dir):
    img = Image.open(img_path).convert("RGB")
    input_arr = preprocess(img)
    output = run_inference(interp, input_arr)

    best = parse_best_detection(output, threshold)
    all_dets = parse_all_detections(output, threshold)

    confs = output[0, 4, :]
    print(f"  {Path(img_path).name}: max_conf={confs.max():.4f}, "
          f"detections={len(all_dets)}, "
          f"best={'%.4f' % best[4] if best else 'none'}")

    annotated = annotate_image(img.copy(), all_dets, threshold)
    out_path = out_dir / f"{Path(img_path).stem}_det.jpg"
    annotated.save(out_path, quality=90)
    print(f"    → {out_path}")

def process_video(interp, video_path, threshold, out_dir, every_n=30):
    import cv2
    cap = cv2.VideoCapture(str(video_path))
    fps = cap.get(cv2.CAP_PROP_FPS) or 30
    total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    print(f"  Video: {total} frames @ {fps:.0f}fps, sampling every {every_n} frames")

    frame_idx = 0
    det_count = 0
    while cap.isOpened():
        ret, frame = cap.read()
        if not ret:
            break
        if frame_idx % every_n == 0:
            rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            img = Image.fromarray(rgb)
            input_arr = preprocess(img)
            output = run_inference(interp, input_arr)
            best = parse_best_detection(output, threshold)
            all_dets = parse_all_detections(output, threshold)
            if all_dets:
                det_count += 1
                annotated = annotate_image(img.copy(), all_dets, threshold)
                out_path = out_dir / f"frame_{frame_idx:05d}_det.jpg"
                annotated.save(out_path, quality=85)
                conf_str = f"{best[4]:.2%}" if best else "none"
                print(f"    frame {frame_idx}: {len(all_dets)} det(s), best={conf_str} → {out_path}")
        frame_idx += 1
    cap.release()
    print(f"  Done: {det_count} frames with detections out of {frame_idx // every_n} sampled")

def main():
    parser = argparse.ArgumentParser(description="Test BallDetector pipeline offline")
    parser.add_argument("inputs", nargs="*", help="Image/video files (default: test-media/images/*)")
    parser.add_argument("--threshold", type=float, default=DEFAULT_THRESHOLD)
    parser.add_argument("--model", default=MODEL_PATH)
    parser.add_argument("--every-n", type=int, default=30, help="For video: sample every N frames")
    args = parser.parse_args()

    out_dir = Path("test-media/output")
    out_dir.mkdir(parents=True, exist_ok=True)

    print(f"Loading model: {args.model}")
    interp = load_interpreter(args.model)
    print(f"Confidence threshold: {args.threshold:.0%}\n")

    inputs = args.inputs or sorted(
        str(p) for p in Path("test-media/images").glob("*") if p.suffix.lower() in (".jpg", ".jpeg", ".png")
    )

    for path in inputs:
        ext = Path(path).suffix.lower()
        if ext in (".mp4", ".avi", ".mov", ".mkv"):
            process_video(interp, path, args.threshold, out_dir, args.every_n)
        else:
            process_image(interp, path, args.threshold, out_dir)

    print(f"\nAll results in {out_dir}/")

if __name__ == "__main__":
    main()
