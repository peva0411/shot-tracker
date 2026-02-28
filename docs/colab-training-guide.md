# Training YOLO11 on Google Colab

This guide walks through training a YOLO11n model on your basketball dataset using Google Colab.

---

## Prerequisites

- A Google account
- Your `datasets/basketball` folder (or `datasets/basketball-outside`) zipped and ready to upload
- Your `yolo11n.pt` weights file

---

## Step 1 — Prepare Your Dataset Locally

Zip your dataset so it can be uploaded to Google Drive:

```bash
cd /home/phil/projects/shot-tracker
zip -r basketball-dataset.zip datasets/basketball
```

---

## Step 2 — Upload to Google Drive

1. Go to [drive.google.com](https://drive.google.com)
2. Create a folder, e.g. `shot-tracker/`
3. Upload:
   - `basketball-dataset.zip`
   - `yolo11n.pt` (your pretrained weights)

---

## Step 3 — Open a Colab Notebook

1. Go to [colab.research.google.com](https://colab.research.google.com)
2. Create a new notebook
3. Set the runtime to GPU: **Runtime → Change runtime type → T4 GPU**

---

## Step 4 — Mount Google Drive & Extract Dataset

```python
from google.colab import drive
drive.mount('/content/drive')
```

```python
import zipfile, os

# Extract dataset
with zipfile.ZipFile('/content/drive/MyDrive/shot-tracker/basketball-dataset.zip', 'r') as z:
    z.extractall('/content/datasets')

# Verify
os.listdir('/content/datasets/basketball')
```

---

## Step 5 — Install Ultralytics

```python
!pip install ultralytics -q
```

---

## Step 6 — Fix the Dataset Path

The `data.yaml` file contains an absolute path from your local machine. Patch it for Colab:

```python
import yaml

yaml_path = '/content/datasets/basketball/data.yaml'

with open(yaml_path, 'r') as f:
    config = yaml.safe_load(f)

config['path'] = '/content/datasets/basketball'

with open(yaml_path, 'w') as f:
    yaml.dump(config, f)

print(config)
```

---

## Step 7 — Run Training

```python
from ultralytics import YOLO

model = YOLO('/content/drive/MyDrive/shot-tracker/yolo11n.pt')

model.train(
    data='/content/datasets/basketball/data.yaml',
    epochs=100,
    imgsz=640,
    batch=16,
    name='basketball-yolo11',
    project='/content/drive/MyDrive/shot-tracker/runs',  # saves directly to Drive
)
```

> **Tip:** Setting `project` to a Google Drive path means your trained weights are saved
> automatically — you won't lose them if the session disconnects.

---

## Step 8 — Evaluate the Model

```python
metrics = model.val()
print(metrics)
```

---

## Step 9 — Download the Trained Weights

After training, your best weights will be at:
```
/content/drive/MyDrive/shot-tracker/runs/basketball-yolo11/weights/best.pt
```

Download them to your local project:
```bash
# From your local machine
cp ~/GoogleDrive/shot-tracker/runs/basketball-yolo11/weights/best.pt \
   /home/phil/projects/shot-tracker/runs/
```

Or directly in Colab:
```python
from google.colab import files
files.download('/content/drive/MyDrive/shot-tracker/runs/basketball-yolo11/weights/best.pt')
```

---

## Tips

| Issue | Fix |
|---|---|
| Session disconnects | Save `project=` to Drive (done above) |
| Out of memory | Reduce `batch=8` or `imgsz=320` |
| Slow training | Check Runtime is set to GPU, not CPU |
| Free tier time limit | Use Kaggle (30hr/week free) as an alternative |

---

## Colab vs Local Comparison

| | GTX 1650 (local) | Colab Free (T4) |
|---|---|---|
| VRAM | 4 GB | 16 GB |
| Batch size | 2–4 | 16–32 |
| ~100 epoch time | Very slow / OOM | ~30–60 min |
| Cost | Free | Free (with limits) |
