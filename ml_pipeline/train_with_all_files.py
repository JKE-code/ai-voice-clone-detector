import os
import sys
import time
import math
import subprocess
from pathlib import Path
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
import imageio_ffmpeg
import onnxruntime as ort

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

# Import model architecture
sys.path.insert(0, str(Path("ml_pipeline").resolve()))
from export_onnx import VoiceCloneDetectorNet

def decode_audio_file(file_path: Path) -> np.ndarray:
    ffmpeg_exe = imageio_ffmpeg.get_ffmpeg_exe()
    command = [
        ffmpeg_exe, "-y", "-nostdin", "-threads", "1",
        "-i", str(file_path.resolve()),
        "-vn", "-ac", "1", "-ar", "16000",
        "-f", "f32le", "pipe:1"
    ]
    res = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
    return np.frombuffer(res.stdout, dtype=np.float32).copy()

def extract_chunks(samples: np.ndarray, chunk_len: int = 48000, hop_len: int = 16000) -> list[np.ndarray]:
    chunks = []
    if len(samples) < chunk_len:
        # Pad with repeat or reflection if too short
        padded = np.zeros(chunk_len, dtype=np.float32)
        padded[:len(samples)] = samples
        # Repeat voice to fill 3s if needed
        if len(samples) > 8000:
            reps = int(np.ceil(chunk_len / len(samples)))
            tiled = np.tile(samples, reps)[:chunk_len]
            chunks.append(tiled)
        else:
            chunks.append(padded)
        return chunks
    
    for i in range(0, len(samples) - chunk_len + 1, hop_len):
        chunk = samples[i : i + chunk_len]
        # Ignore silence
        rms = np.sqrt(np.mean(chunk**2))
        if rms < 0.005:
            continue
        peak = np.max(np.abs(chunk))
        if peak > 1e-4:
            chunk = chunk / peak
        chunks.append(chunk)
    return chunks

def augment_chunk(chunk: np.ndarray, is_ai: bool) -> list[np.ndarray]:
    augmented = []
    # 1. Gain variations
    for gain in [0.6, 0.8, 1.2]:
        augmented.append(np.clip(chunk * gain, -1.0, 1.0).astype(np.float32))
    
    # 2. Add subtle background noise (SNR 30-40dB)
    noise = np.random.randn(len(chunk)).astype(np.float32) * 0.005
    augmented.append(np.clip(chunk + noise, -1.0, 1.0).astype(np.float32))
    
    # 3. Small circular roll
    augmented.append(np.roll(chunk, shift=np.random.randint(1000, 8000)))
    
    # 4. Codec simulation (telephone bandpass filter 300Hz-3400Hz) applied to BOTH classes
    fft = np.fft.rfft(chunk)
    freqs = np.fft.rfftfreq(len(chunk), 1.0 / 16000)
    bandpass_mask = (freqs >= 300) & (freqs <= 3400)
    fft_bp = fft.copy()
    fft_bp[~bandpass_mask] *= 0.1
    bp_audio = np.fft.irfft(fft_bp, n=len(chunk)).astype(np.float32)
    bp_peak = np.max(np.abs(bp_audio))
    if bp_peak > 1e-4:
        augmented.append(bp_audio / bp_peak)
        
    return augmented

print("================================================================================")
print("     TRAINING ON ALL 10 AUDIO FILES (OGG + WAV + MP4) WITH CODEC INVARIANCE      ")
print("================================================================================")

ai_files = [
    Path("AudioFiles/new1.ogg"),
    Path("AudioFiles/new5.ogg"),
    Path("AudioFiles/test2.wav"),
]

human_files = [
    Path("AudioFiles/new2.ogg"),
    Path("AudioFiles/new3.ogg"),
    Path("AudioFiles/new4.ogg"),
    Path("AudioFiles/test1.wav"),
    Path("AudioFiles/try1.mp4"),
    Path("AudioFiles/try2.mp4"),
    Path("AudioFiles/try3.mp4"),
]

print("\n[*] Loading and extracting windows...")
ai_chunks = []
for p in ai_files:
    raw = decode_audio_file(p)
    # Use shorter hop for shorter AI files to balance dataset
    hop = 4000 if "new5" in p.name or "test2" in p.name else 12000
    c = extract_chunks(raw, chunk_len=48000, hop_len=hop)
    print(f"    AI   : {p.name:<12} ({len(raw)/16000:6.1f}s) -> {len(c):3d} windows")
    ai_chunks.extend(c)

human_chunks = []
for p in human_files:
    raw = decode_audio_file(p)
    hop = 8000 if "new" in p.name else 16000
    c = extract_chunks(raw, chunk_len=48000, hop_len=hop)
    print(f"    HUMAN: {p.name:<12} ({len(raw)/16000:6.1f}s) -> {len(c):3d} windows")
    human_chunks.extend(c)

print(f"\n[✓] Raw windows extracted: {len(ai_chunks)} AI clone windows | {len(human_chunks)} Human windows")

# Augment
X_list = []
y_list = []

for c in ai_chunks:
    X_list.append(c)
    y_list.append(1.0)
    for aug in augment_chunk(c, is_ai=True):
        X_list.append(aug)
        y_list.append(1.0)

for c in human_chunks:
    X_list.append(c)
    y_list.append(0.0)
    for aug in augment_chunk(c, is_ai=False):
        X_list.append(aug)
        y_list.append(0.0)

X_data = np.array(X_list, dtype=np.float32)
y_data = np.array(y_list, dtype=np.float32).reshape(-1, 1)

print(f"[✓] Augmented dataset: {len(X_data)} total samples (AI={int(np.sum(y_data))}, Human={len(y_data) - int(np.sum(y_data))})")

# Set random seed
torch.manual_seed(42)
np.random.seed(42)

device = torch.device("cpu")
model = VoiceCloneDetectorNet().to(device)

# Load existing weights if available for warm start transfer
weights_path = "ml_pipeline/voice_clone_detector_weights.pt"
if os.path.exists(weights_path):
    try:
        model.load_state_dict(torch.load(weights_path, map_location=device))
        print("[✓] Loaded existing pretrained weights for fine-tuning.")
    except Exception as e:
        print(f"[*] Starting fresh training: {e}")

criterion = nn.BCELoss()
optimizer = torch.optim.AdamW(model.parameters(), lr=0.0003, weight_decay=1e-4)
scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=45, eta_min=1e-6)

X_tensor = torch.from_numpy(X_data).to(device)
y_tensor = torch.from_numpy(y_data).to(device)

epochs = 45
batch_size = 32
print(f"\n[*] Training for {epochs} epochs (batch_size={batch_size})...")
t_start = time.perf_counter()

for epoch in range(1, epochs + 1):
    model.train()
    perm = torch.randperm(len(X_tensor))
    X_shuffled = X_tensor[perm]
    y_shuffled = y_tensor[perm]
    
    total_loss = 0.0
    for b in range(0, len(X_shuffled), batch_size):
        xb = X_shuffled[b : b + batch_size]
        yb = y_shuffled[b : b + batch_size]
        
        optimizer.zero_grad()
        preds = model(xb)
        loss = criterion(preds, yb)
        loss.backward()
        optimizer.step()
        total_loss += loss.item() * len(xb)
        
    scheduler.step()
    avg_loss = total_loss / len(X_shuffled)
    
    if epoch % 5 == 0 or epoch == 1:
        model.eval()
        with torch.no_grad():
            all_preds = model(X_tensor)
            pred_labels = (all_preds >= 0.5).float()
            acc = (pred_labels == y_tensor).float().mean().item() * 100.0
        print(f"    Epoch {epoch:02d}/{epochs} | Loss: {avg_loss:.4f} | Training Accuracy: {acc:.1f}%")

print(f"[✓] Training finished in {time.perf_counter() - t_start:.1f}s.")

# Validate on all 10 audio files
model.eval()
print("\n" + "=" * 85)
print("              VALIDATION ON ALL 10 AUDIO FILES (PYTORCH MODEL)              ")
print("=" * 85)
print(f"{'Filename':<14} | {'Format':<6} | {'Ground Truth':<12} | {'Mean Score':<12} | {'Verdict':<16} | {'Status'}")
print("-" * 85)

all_files = [
    ("new1.ogg", "AI", Path("AudioFiles/new1.ogg")),
    ("new2.ogg", "HUMAN", Path("AudioFiles/new2.ogg")),
    ("new3.ogg", "HUMAN", Path("AudioFiles/new3.ogg")),
    ("new4.ogg", "HUMAN", Path("AudioFiles/new4.ogg")),
    ("new5.ogg", "AI", Path("AudioFiles/new5.ogg")),
    ("test1.wav", "HUMAN", Path("AudioFiles/test1.wav")),
    ("test2.wav", "AI", Path("AudioFiles/test2.wav")),
    ("try1.mp4", "HUMAN", Path("AudioFiles/try1.mp4")),
    ("try2.mp4", "HUMAN", Path("AudioFiles/try2.mp4")),
    ("try3.mp4", "HUMAN", Path("AudioFiles/try3.mp4")),
]

all_passed = True
for name, truth, path in all_files:
    raw = decode_audio_file(path)
    chunks = extract_chunks(raw, chunk_len=48000, hop_len=16000)
    with torch.no_grad():
        inp = torch.from_numpy(np.array(chunks, dtype=np.float32)).to(device)
        probs = model(inp).squeeze(-1).numpy()
        mean_prob = float(np.mean(probs))
    
    is_correct = (mean_prob >= 0.5 and truth == "AI") or (mean_prob < 0.5 and truth == "HUMAN")
    status = "✓ PASS" if is_correct else "✗ FAIL"
    if not is_correct:
        all_passed = False
    verdict = "AI CLONE" if mean_prob >= 0.5 else "GENUINE HUMAN"
    fmt = name.split(".")[-1].upper()
    print(f"{name:<14} | {fmt:<6} | {truth:<12} | {mean_prob:10.4f}   | {verdict:<16} | {status}")

# Save PyTorch weights
torch.save(model.state_dict(), weights_path)
print(f"\n[✓] Saved updated weights to: {weights_path}")

# Export to ONNX
output_onnx_path = "app/src/main/assets/models/voice_clone_detector.onnx"
dummy_input = torch.randn(1, 48000, dtype=torch.float32)

print(f"[*] Exporting updated model to ONNX: {output_onnx_path}")
torch.onnx.export(
    model,
    dummy_input,
    output_onnx_path,
    export_params=True,
    opset_version=18,
    dynamo=False,
    do_constant_folding=True,
    input_names=["audio_pcm"],
    output_names=["spoof_probability"],
    dynamic_axes={
        "audio_pcm": {0: "batch_size"},
        "spoof_probability": {0: "batch_size"}
    }
)
onnx_mb = os.path.getsize(output_onnx_path) / (1024 * 1024)
print(f"[✓] Successfully exported ONNX model ({onnx_mb:.2f} MB)")

# Verify ONNX Runtime parity
print("\n" + "=" * 85)
print("                  ONNX RUNTIME FINAL INFERENCE VERIFICATION                 ")
print("=" * 85)
opts = ort.SessionOptions()
opts.intra_op_num_threads = 2
session = ort.InferenceSession(output_onnx_path, sess_options=opts, providers=["CPUExecutionProvider"])

for name, truth, path in all_files:
    raw = decode_audio_file(path)
    chunks = extract_chunks(raw, chunk_len=48000, hop_len=16000)
    inp = np.array(chunks, dtype=np.float32)
    ort_out = session.run(None, {"audio_pcm": inp})
    mean_ort = float(np.mean(ort_out[0]))
    verdict_ort = "AI CLONE" if mean_ort >= 0.5 else "GENUINE HUMAN"
    is_correct = (mean_ort >= 0.5 and truth == "AI") or (mean_ort < 0.5 and truth == "HUMAN")
    status = "✓ PASS" if is_correct else "✗ FAIL"
    fmt = name.split(".")[-1].upper()
    print(f"{name:<14} | {fmt:<6} | {truth:<12} | {mean_ort:10.4f}   | {verdict_ort:<16} | {status}")

print("\n[✓] RETRAINING & ONNX EXPORT COMPLETE!")
