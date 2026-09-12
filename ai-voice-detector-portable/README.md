# AI Voice Detector Model CLI

Terminal-only voice authenticity detector.

Give it an audio file and it prints JSON with the probability that the voice is
machine-generated. The default production backend uses a WavLM-large + AASIST
forensics model and chunked inference for longer clips.

Default model:

```text
eliya/forensics_0.3B_base_deepfake_classifier
```

## Setup

```powershell
python -m pip install -r requirements.txt
python download_model.py --model-id eliya/forensics_0.3B_base_deepfake_classifier
```

The model cache is stored under `.models/huggingface` in this workspace.

If the Hub download stalls, download these manually:

Forensics checkpoint folder:

```text
E:\ai-voice-detector\.models\huggingface\local\eliya__forensics_0.3B_base_deepfake_classifier\
```

- https://huggingface.co/eliya/forensics_0.3B_base_deepfake_classifier/resolve/main/checkpoint_epoch_5.safetensors
- https://huggingface.co/eliya/forensics_0.3B_base_deepfake_classifier/resolve/main/config.json
- https://huggingface.co/eliya/forensics_0.3B_base_deepfake_classifier/resolve/main/model.py

WavLM backbone folder:

```text
E:\ai-voice-detector\.models\huggingface\local\microsoft__wavlm-large\
```

- https://huggingface.co/microsoft/wavlm-large/resolve/main/pytorch_model.bin
- https://huggingface.co/microsoft/wavlm-large/resolve/main/config.json
- https://huggingface.co/microsoft/wavlm-large/resolve/main/preprocessor_config.json

## Run Detection

```powershell
python detect.py .\sample.wav
```

Response:

```json
{
  "verdict": "likely_synthetic",
  "probability": 0.731,
  "confidence": "medium",
  "model": "eliya/forensics_0.3B_base_deepfake_classifier",
  "methodology": "wavlm-aasist-forensics-v1:eliya/forensics_0.3B_base_deepfake_classifier",
  "duration_ms": 186,
  "fingerprint": "sha256:...",
  "metadata": {
    "filename": "sample.wav",
    "duration_seconds": 3.2,
    "sample_rate": 16000,
    "decoder": "ffmpeg"
  }
}
```

For feature details:

```powershell
python detect.py .\sample.wav --diagnostics
```

Offline heuristic fallback:

```powershell
python detect.py .\sample.wav --backend heuristic
```

Plain Transformers audio-classifier backend:

```powershell
python detect.py .\sample.wav --backend hf --model-id Hemgg/Deepfake-audio-detection
```

Use the two-model ensemble after downloading both models:

```powershell
python detect.py .\sample.wav --backend ensemble
```

Benchmark several models against known files:

```powershell
python benchmark_models.py --real .\test.wav --fake .\test2.wav `
  --models Hemgg/Deepfake-audio-detection,garystafford/wav2vec2-deepfake-voice-detector
```

Use another Hugging Face classifier:

```powershell
python detect.py .\sample.wav --model-id garystafford/wav2vec2-deepfake-voice-detector
```

## Supported Input

WAV, MP3, M4A, and WebM decode is handled through the embedded ffmpeg binary
provided by `imageio-ffmpeg` when available. A WAV fallback is included for
environments without ffmpeg. Files over 25 MB are rejected.

## Accuracy Note

This is not the real aivoicedetector.com model. The production backend uses a
public Hugging Face classifier and returns a calibrated probability. Treat the
score as decision support, especially for noisy, compressed, very short, or
out-of-domain recordings.
