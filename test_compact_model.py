import os
import sys
import time
import subprocess
from pathlib import Path
import imageio_ffmpeg
import numpy as np
from transformers import pipeline

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

def decode_audio_file(file_path: Path):
    ffmpeg_exe = imageio_ffmpeg.get_ffmpeg_exe()
    command = [
        ffmpeg_exe,
        "-y",
        "-nostdin",
        "-threads", "1",
        "-i", str(file_path.resolve()),
        "-vn",
        "-ac", "1",
        "-ar", "16000",
        "-f", "f32le",
        "pipe:1"
    ]
    res = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    if res.returncode == 0 and res.stdout:
        samples = np.frombuffer(res.stdout, dtype=np.float32).copy()
        return samples, len(samples) / 16000.0
    raise RuntimeError(f"Decode failed for {file_path}")

print("================================================================================")
print("     COMPACT WAV2VEC2 MODEL (Hemgg/Deepfake-audio-detection: 360MB) EVAL       ")
print("================================================================================")

t0 = time.perf_counter()
pipe = pipeline("audio-classification", model="Hemgg/Deepfake-audio-detection")
print(f"[✓] Model loaded in {time.perf_counter() - t0:.1f}s. Labels: {pipe.model.config.id2label}")

audio_files = [
    Path("AudioFiles/test1.wav"),
    Path("AudioFiles/test2.wav"),
    Path("AudioFiles/try1.mp4"),
    Path("AudioFiles/try2.mp4"),
    Path("AudioFiles/try3.mp4"),
]

for file_path in audio_files:
    if not file_path.exists():
        continue
    samples, duration = decode_audio_file(file_path)
    
    t0 = time.perf_counter()
    res = pipe({"raw": samples, "sampling_rate": 16000})
    lat_ms = (time.perf_counter() - t0) * 1000
    
    scores = {item['label']: round(item['score'], 4) for item in res}
    ai_score = scores.get('AIVoice', 0.0)
    human_score = scores.get('HumanVoice', 0.0)
    verdict = "likely_synthetic (AI)" if ai_score >= 0.5 else "likely_authentic (Human)"
    
    print(f"{file_path.name:<12} ({duration:>4.1f}s) | AI={ai_score:.4f}  Human={human_score:.4f} | Verdict: {verdict:<25} | Latency: {lat_ms:6.1f}ms")
