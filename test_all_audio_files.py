import os
import sys
import time
import subprocess
import io
import wave
from pathlib import Path

# Ensure UTF-8 output
if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")

sys.path.insert(0, str(Path("ai-voice-detector-portable").resolve()))

import imageio_ffmpeg
import numpy as np
import onnxruntime as ort

from voice_detector.audio import DecodedAudio, extract_features, TARGET_SAMPLE_RATE, _sanitize_samples
from voice_detector.detector import score_synthetic, verdict_from_probability
from voice_detector.forensics_detector import ForensicsDeepfakeDetector

def decode_file_robust(file_path: Path) -> DecodedAudio:
    ffmpeg_exe = imageio_ffmpeg.get_ffmpeg_exe()
    command = [
        ffmpeg_exe,
        "-y",
        "-nostdin",
        "-threads", "1",
        "-i", str(file_path.resolve()),
        "-vn",
        "-ac", "1",
        "-ar", str(TARGET_SAMPLE_RATE),
        "-f", "f32le",
        "pipe:1"
    ]
    res = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    if res.returncode == 0 and res.stdout:
        samples = np.frombuffer(res.stdout, dtype=np.float32).copy()
        samples = _sanitize_samples(samples)
        duration = samples.size / TARGET_SAMPLE_RATE
        return DecodedAudio(samples=samples, sample_rate=TARGET_SAMPLE_RATE, duration_seconds=duration, decoder="ffmpeg")
    
    # Fallback to standard wav
    with wave.open(str(file_path), "rb") as wav:
        frames = wav.readframes(wav.getnframes())
        if wav.getsampwidth() == 2:
            data = np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32768.0
        else:
            data = np.frombuffer(frames, dtype=np.float32)
        if wav.getnchannels() > 1:
            data = data.reshape(-1, wav.getnchannels()).mean(axis=1)
        return DecodedAudio(samples=data, sample_rate=wav.getframerate(), duration_seconds=len(data)/wav.getframerate(), decoder="wav")

audio_files = [
    Path("AudioFiles/test1.wav"),
    Path("AudioFiles/test2.wav"),
    Path("AudioFiles/try1.mp4"),
    Path("AudioFiles/try2.mp4"),
    Path("AudioFiles/try3.mp4"),
]

print("====================================================================================================")
print("                       TRUE VOICE: RIGOROUS EVALUATION ON USER'S 5 AUDIO FILES                      ")
print("====================================================================================================")

# 1. Initialize Sahil ONNX
onnx_model_path = "app/src/main/assets/models/voice_clone_detector.onnx"
ort_session = None
if os.path.exists(onnx_model_path):
    opts = ort.SessionOptions()
    opts.intra_op_num_threads = 2
    ort_session = ort.InferenceSession(onnx_model_path, sess_options=opts, providers=["CPUExecutionProvider"])
    ort_input_name = ort_session.get_inputs()[0].name
    print("[✓] Sahil ONNX Model loaded (1.16 MB).")

# 2. Initialize Akshat Forensics Model ONCE
print("[*] Loading Akshat Forensics DeepfakeDetector (WavLM-large + AASIST)...")
t0 = time.perf_counter()
forensics_detector = ForensicsDeepfakeDetector(
    model_id="eliya/forensics_0.3B_base_deepfake_classifier",
    device="cpu",
    calibrate=True
)
forensics_detector._ensure_loaded()
print(f"[✓] Akshat Forensics Model loaded in {time.perf_counter() - t0:.1f}s.")

results = []

for file_path in audio_files:
    if not file_path.exists():
        print(f"[!] Missing file: {file_path}")
        continue

    print(f"\n>>> Analyzing: {file_path.name} ({file_path.stat().st_size / 1024:.1f} KB) <<<")
    decoded = decode_file_robust(file_path)
    features = extract_features(decoded)
    print(f"    Duration: {decoded.duration_seconds:.2f}s | Sample Rate: {decoded.sample_rate}Hz | RMS: {features.rms_mean:.4f}")

    # 1. Akshat DSP Heuristic
    t0 = time.perf_counter()
    h_prob, h_conf, _ = score_synthetic(features)
    h_verdict = verdict_from_probability(h_prob, h_conf)
    t_heuristic = (time.perf_counter() - t0) * 1000

    # 2. Sahil ONNX
    t_onnx = 0.0
    onnx_prob = 0.0
    if ort_session is not None:
        t0 = time.perf_counter()
        samples = decoded.samples
        if len(samples) < 48000:
            padded = np.zeros(48000, dtype=np.float32)
            padded[:len(samples)] = samples
            inp = np.expand_dims(padded, axis=0)
        else:
            inp = np.expand_dims(samples[:48000], axis=0)
        out = ort_session.run(None, {ort_input_name: inp})
        t_onnx = (time.perf_counter() - t0) * 1000
        onnx_prob = float(out[0][0][0])
        onnx_verdict = "fake" if onnx_prob >= 0.5 else "real"

    # 3. Akshat Forensics Model (WavLM-large + AASIST)
    t0 = time.perf_counter()
    pred = forensics_detector.predict(decoded, features)
    t_forensics = (time.perf_counter() - t0) * 1000
    f_verdict = verdict_from_probability(pred.probability, pred.confidence)

    print(f"    Akshat Forensics (2.5GB):  Prob={pred.probability:.3f} (Raw={pred.raw_model_probability:.4f}) | Verdict={f_verdict:<16} | Latency={t_forensics:7.1f}ms")
    print(f"    Akshat DSP Heuristic:     Prob={h_prob:.3f}                 | Verdict={h_verdict:<16} | Latency={t_heuristic:7.1f}ms")
    print(f"    Sahil ONNX (1.2MB):       Prob={onnx_prob:.4f}               | Verdict={onnx_verdict:<16} | Latency={t_onnx:7.1f}ms")

    results.append({
        "file": file_path.name,
        "duration": decoded.duration_seconds,
        "forensics_prob": pred.probability,
        "forensics_raw": pred.raw_model_probability,
        "forensics_verdict": f_verdict,
        "forensics_conf": pred.confidence,
        "forensics_latency": t_forensics,
        "heuristic_prob": h_prob,
        "heuristic_verdict": h_verdict,
        "heuristic_latency": t_heuristic,
        "onnx_prob": onnx_prob,
        "onnx_latency": t_onnx,
    })

print("\n========================================================================================================================")
print("                                              FINAL EMPIRICAL RESULTS TABLE                                             ")
print("========================================================================================================================")
print(f"{'Filename':<12} | {'Dur':<6} | {'Akshat Forensics 2.5GB (Accuracy)':<38} | {'Akshat DSP Heuristic':<25} | {'Sahil ONNX':<12}")
print("-" * 115)
for r in results:
    f_str = f"Prob={r['forensics_prob']:.3f} ({r['forensics_verdict']}) [{r['forensics_latency']:.0f}ms]"
    h_str = f"Prob={r['heuristic_prob']:.3f} ({r['heuristic_verdict']})"
    s_str = f"Prob={r['onnx_prob']:.4f}"
    print(f"{r['file']:<12} | {r['duration']:>4.1f}s | {f_str:<38} | {h_str:<25} | {s_str:<12}")
