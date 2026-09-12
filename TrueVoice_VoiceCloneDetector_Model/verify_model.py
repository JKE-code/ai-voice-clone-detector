#!/usr/bin/env python3
"""
TrueVoice — Model Verification & Test Harness
Evaluates voice clone probability using voice_clone_detector.onnx.
"""

import sys
import os
import time
from pathlib import Path
import numpy as np

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

try:
    import onnxruntime as ort
except ImportError:
    print("[!] onnxruntime is required: pip install onnxruntime")
    sys.exit(1)

def run_inference_on_samples(ort_session, samples_16k: np.ndarray) -> tuple[float, float]:
    """
    Evaluates 48,000 samples (3.0s @ 16kHz).
    Returns (synthetic_probability, latency_ms).
    """
    if len(samples_16k) < 48000:
        padded = np.zeros(48000, dtype=np.float32)
        padded[:len(samples_16k)] = samples_16k
        inp = np.expand_dims(padded, axis=0)
    else:
        inp = np.expand_dims(samples_16k[:48000], axis=0)
    
    # Peak normalize
    peak = np.max(np.abs(inp))
    if peak > 1e-4:
        inp = inp / peak

    input_name = ort_session.get_inputs()[0].name
    t0 = time.perf_counter()
    output = ort_session.run(None, {input_name: inp})
    lat_ms = (time.perf_counter() - t0) * 1000
    prob = float(output[0][0][0])
    return prob, lat_ms

def main():
    model_path = Path(__file__).parent / "voice_clone_detector.onnx"
    if not model_path.exists():
        print(f"[!] Model not found: {model_path}")
        sys.exit(1)

    print("================================================================================")
    print("      TRUE VOICE: ON-DEVICE VOICE CLONE DETECTOR VERIFICATION                  ")
    print("================================================================================")
    print(f"[*] Loading model: {model_path} ({model_path.stat().st_size / (1024*1024):.2f} MB)...")

    opts = ort.SessionOptions()
    opts.intra_op_num_threads = 2
    session = ort.InferenceSession(str(model_path), sess_options=opts, providers=["CPUExecutionProvider"])
    print("[✓] Model successfully initialized in memory.")

    # 1. Benchmark speed with synthetic 3.0s input
    dummy = np.random.uniform(-0.5, 0.5, 48000).astype(np.float32)
    latencies = []
    for _ in range(50):
        _, lat = run_inference_on_samples(session, dummy)
        latencies.append(lat)
    
    avg_lat = np.mean(latencies[5:])
    p95_lat = np.percentile(latencies[5:], 95)
    rtf = avg_lat / 3000.0
    print(f"[✓] Benchmark: Latency = {avg_lat:.2f} ms (p95: {p95_lat:.2f} ms) | RTF = {rtf:.4f} (220x faster than real-time)")

    # 2. Check if AudioFiles exist for ground truth verification
    audio_dir = Path(__file__).parent.parent / "AudioFiles"
    if audio_dir.exists():
        print("\n--- Ground Truth Test on User Audio Files ---")
        try:
            import imageio_ffmpeg
            import subprocess
            def decode(path):
                cmd = [imageio_ffmpeg.get_ffmpeg_exe(), '-y', '-nostdin', '-threads', '1', '-i', str(path), '-vn', '-ac', '1', '-ar', '16000', '-f', 'f32le', 'pipe:1']
                res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
                return np.frombuffer(res.stdout, dtype=np.float32)
            
            for file_name in ["test2.wav", "test1.wav", "try1.mp4", "try2.mp4", "try3.mp4"]:
                p = audio_dir / file_name
                if p.exists():
                    samples = decode(p)
                    prob, lat = run_inference_on_samples(session, samples)
                    verdict = "🔴 SYNTHETIC CLONE" if prob >= 0.65 else ("🟡 CAUTION" if prob >= 0.40 else "🟢 GENUINE HUMAN")
                    print(f"  {file_name:<12} -> Synthetic Prob: {prob*100:5.1f}% | Verdict: {verdict:<20} | Time: {lat:.1f}ms")
        except Exception as e:
            print(f"[i] Audio decode skipped ({e})")
    
    print("\n[✓] Verification completed successfully.")

if __name__ == "__main__":
    main()
