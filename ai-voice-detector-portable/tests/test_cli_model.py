from __future__ import annotations

import io
import wave

import numpy as np

from detect import detect_file
from voice_detector.hf_detector import HFDetectionResult, SegmentPrediction


def test_detect_file_outputs_probability_json_shape(tmp_path) -> None:
    audio_path = tmp_path / "tone.wav"
    audio_path.write_bytes(_make_wav())

    payload = detect_file(audio_path, backend="heuristic")

    assert set(payload) >= {
        "verdict",
        "probability",
        "confidence",
        "model",
        "methodology",
        "duration_ms",
        "fingerprint",
        "metadata",
    }
    assert 0 <= payload["probability"] <= 1
    assert payload["fingerprint"].startswith("sha256:")
    assert payload["metadata"]["filename"] == "tone.wav"


def test_detect_file_can_include_diagnostics(tmp_path) -> None:
    audio_path = tmp_path / "tone.wav"
    audio_path.write_bytes(_make_wav())

    payload = detect_file(audio_path, include_diagnostics=True, backend="heuristic")

    assert "diagnostics" in payload
    assert "quality" in payload["diagnostics"]


def test_detect_file_can_use_hf_backend_with_mock(tmp_path, monkeypatch) -> None:
    audio_path = tmp_path / "tone.wav"
    audio_path.write_bytes(_make_wav(duration=2.8))

    class FakeDetector:
        def __init__(self, **kwargs) -> None:
            self.kwargs = kwargs

        def predict(self, audio, features) -> HFDetectionResult:
            return HFDetectionResult(
                probability=0.812,
                raw_model_probability=0.834,
                confidence="high",
                model_id=self.kwargs["model_id"],
                device="cpu",
                class_probabilities={"real": 0.166, "fake": 0.834},
                segments=[SegmentPrediction(0.0, 2.8, 0.834, 2.8)],
                calibration={"quality": 0.9, "quality_shrinkage": 0.955, "enabled": 1.0},
            )

    monkeypatch.setattr("detect.HuggingFaceDeepfakeDetector", FakeDetector)
    payload = detect_file(audio_path, backend="hf", model_id="fake/model")

    assert payload["probability"] == 0.812
    assert payload["verdict"] == "likely_synthetic"
    assert payload["model"] == "fake/model"
    assert payload["methodology"] == "hf-wav2vec2-deepfake-v1:fake/model"


def test_detect_file_can_use_ensemble_backend_with_mock(tmp_path, monkeypatch) -> None:
    audio_path = tmp_path / "tone.wav"
    audio_path.write_bytes(_make_wav(duration=2.8))

    class FakeDetector:
        def __init__(self, **kwargs) -> None:
            self.kwargs = kwargs

        def predict(self, audio, features) -> HFDetectionResult:
            probability = 0.1 if self.kwargs["model_id"] == "real/model" else 0.9
            return HFDetectionResult(
                probability=probability,
                raw_model_probability=probability,
                confidence="high",
                model_id=self.kwargs["model_id"],
                device="cpu",
                class_probabilities={"real": 1 - probability, "fake": probability},
                segments=[SegmentPrediction(0.0, 2.8, probability, 2.8)],
                calibration={"quality": 0.9, "quality_shrinkage": 0.955, "enabled": 1.0},
            )

    monkeypatch.setattr("detect.HuggingFaceDeepfakeDetector", FakeDetector)
    payload = detect_file(
        audio_path,
        backend="ensemble",
        model_ids=["real/model", "fake/model"],
        include_diagnostics=True,
    )

    assert payload["probability"] == 0.628
    assert payload["verdict"] == "uncertain"
    assert payload["confidence"] == "low"
    assert payload["model"] == "ensemble:real/model,fake/model"
    assert len(payload["diagnostics"]["models"]) == 2


def _make_wav(duration: float = 1.2, sample_rate: int = 16_000) -> bytes:
    t = np.linspace(0, duration, int(sample_rate * duration), endpoint=False)
    signal = 0.25 * np.sin(2 * np.pi * 220 * t)
    pcm = np.clip(signal * 32767, -32768, 32767).astype("<i2")

    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(sample_rate)
        wav.writeframes(pcm.tobytes())
    return buffer.getvalue()
