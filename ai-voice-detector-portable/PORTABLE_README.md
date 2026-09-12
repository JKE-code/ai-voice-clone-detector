# Portable AI Voice Detector

This folder is designed to be copied into another Python repo.

## What It Uses

Default detector:

```text
eliya/forensics_0.3B_base_deepfake_classifier
```

Architecture:

```text
WavLM-large speech backbone + AASIST graph-attention anti-spoofing classifier
```

The model turns an audio waveform into a probability that the voice is fake or
AI-generated. It analyzes 5-second 16 kHz windows, aggregates the window scores,
and returns JSON with `probability`, `verdict`, `confidence`, and metadata.

The required WavLM-large backbone is stored as:

```text
microsoft/wavlm-large
```

## Install

```powershell
python -m pip install -r requirements.txt
```

## Run

```powershell
python detect.py .\audio.wav
```

## Import From Another Repo

```python
from pathlib import Path

from detect import detect_file

result = detect_file(Path("audio.wav"))
print(result["probability"])
```

## License Note

The Forensics model is published under CC-BY-NC-4.0. That is suitable for
personal/research/non-commercial use; commercial use needs permission from the
model owner.
