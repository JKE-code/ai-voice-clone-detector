# TrueVoice — On-Device AI Voice Clone & Anti-Spoofing Model
### Smart India Hackathon 2026 | Problem Statement #26104

> **Ultra-Compact, Real-Time Edge Anti-Spoofing Neural Classifier**  
> Designed to detect deepfake synthetic voice clones (ElevenLabs, XTTS, HiFi-GAN, MelGAN, RVC) directly on mobile devices with zero cloud latency.

---

## 1. Model Specifications

| Parameter | Specification |
| :--- | :--- |
| **Model Format** | ONNX Runtime (`voice_clone_detector.onnx`) + PyTorch Checkpoint (`voice_clone_detector_weights.pt`) |
| **Model Size** | **4.38 MB** (1,146,801 parameters) |
| **RAM Footprint** | **~12 MB** during active inference |
| **Input Tensor** | `audio_pcm`: Float32 array of shape `[1, 48000]` (3.0 seconds @ 16 kHz mono) normalized to `[-1.0, 1.0]` |
| **Output Tensor** | `synthetic_prob`: Float32 value of shape `[1, 1]` in range `[0.0, 1.0]` |
| **Average Latency** | **2.20 ms** on mobile CPU (p95: 2.77 ms) |
| **Real-Time Factor (RTF)** | **0.0044** (Runs >220x faster than real-time speech) |
| **Target Hardware** | Android (NNAPI / CPU), iOS (CoreML / CPU), Raspberry Pi, Linux/Windows |

---

## 2. Benchmark Accuracy on Ground Truth Audio Files

Evaluated across actual carrier call samples and synthetic clones:

| Audio File | Ground Truth | Synthetic Probability | Verdict | Latency |
| :--- | :--- | :--- | :--- | :--- |
| **`test2.wav`** | 🔴 **AI Voice Clone** | **99.07%** (`0.9907`) | 🔴 **SYNTHETIC CLONE DETECTED** | 2.1 ms |
| **`test1.wav`** | 🟢 **Human Voice** | **2.07%** (`0.0207`) | 🟢 **GENUINE HUMAN** | 2.2 ms |
| **`try1.mp4`** | 🟢 **Human Voice** | **0.00%** (`0.0000`) | 🟢 **GENUINE HUMAN** | 2.0 ms |
| **`try2.mp4`** | 🟢 **Human Voice** | **0.49%** (`0.0049`) | 🟢 **GENUINE HUMAN** | 2.3 ms |
| **`try3.mp4`** | 🟢 **Human Voice** | **0.06%** (`0.0006`) | 🟢 **GENUINE HUMAN** | 2.1 ms |

---

## 3. Package Contents

- **`voice_clone_detector.onnx`** (4.38 MB): Production-ready ONNX model for Android ONNX Runtime Mobile, CoreML, or Python.
- **`voice_clone_detector_weights.pt`** (4.62 MB): PyTorch model state_dict for research, fine-tuning, or transfer learning.
- **`model_architecture.py`**: Clean PyTorch definition of `VoiceCloneDetectorNet` with Squeeze-and-Excitation 1D Dilated Residual blocks and Attentive Statistics Pooling.
- **`verify_model.py`**: Standalone cross-platform benchmark & verification test runner.
- **`README.md`**: Technical specification and integration manual.

---

## 4. Quick Start: Python Usage

```python
import numpy as np
import onnxruntime as ort

# 1. Load ONNX model
session = ort.InferenceSession("voice_clone_detector.onnx", providers=["CPUExecutionProvider"])
input_name = session.get_inputs()[0].name

# 2. Prepare 48,000 samples of 16kHz mono audio [-1.0 .. 1.0]
audio_3s = np.zeros((1, 48000), dtype=np.float32) # Replace with real audio samples

# 3. Predict synthetic probability
output = session.run(None, {input_name: audio_3s})
synthetic_prob = float(output[0][0][0])

if synthetic_prob >= 0.65:
    print(f"🔴 AI CLONE DETECTED (Score: {synthetic_prob:.2%})")
elif synthetic_prob >= 0.40:
    print(f"🟡 SUSPICIOUS / CAUTION (Score: {synthetic_prob:.2%})")
else:
    print(f"🟢 GENUINE HUMAN VOICE (Score: {synthetic_prob:.2%})")
```

---

## 5. Quick Start: Android / Kotlin (ONNX Runtime Mobile)

```kotlin
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

val env = OrtEnvironment.getEnvironment()
val session = env.createSession(context.assets.open("models/voice_clone_detector.onnx").readBytes())

// audioSamples is a FloatArray of size 48,000 (3 seconds @ 16kHz)
val inputTensor = OnnxTensor.createTensor(
    env,
    FloatBuffer.wrap(audioSamples),
    longArrayOf(1, 48000)
)

val results = session.run(mapOf("audio_pcm" to inputTensor))
val outputTensor = results[0] as OnnxTensor
val syntheticScore = outputTensor.floatBuffer.get(0) // [0.0 .. 1.0]

if (syntheticScore >= 0.65f) {
    // Escalate to CLONE_ALERT on Floating HUD
}
```

---

## 6. SIH 2026 Innovation Highlights

- **Zero Cloud Reliance**: Compliant with India's Digital Personal Data Protection (DPDP) Act — audio never leaves the phone.
- **Extreme Efficiency**: 2.2 ms inference ensures zero lag during live carrier phone calls.
- **Compound Defense**: Paired with TrueVoice's Trilingual Indic Intent Engine (English, Hindi, Telugu) to catch both AI Clones and Human Extortion Scams.
