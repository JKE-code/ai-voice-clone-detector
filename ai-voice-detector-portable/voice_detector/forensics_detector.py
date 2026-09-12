from __future__ import annotations

import importlib.util
import math
from contextlib import contextmanager
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

import numpy as np

from voice_detector.audio import DecodedAudio
from voice_detector.detector import clip_quality
from voice_detector.hf_detector import DEFAULT_CACHE_DIR, _resolve_model_source

DEFAULT_FORENSICS_MODEL_ID = "eliya/forensics_0.3B_base_deepfake_classifier"
FORENSICS_BACKBONE_ID = "microsoft/wavlm-large"
FORENSICS_SECONDS = 5.0
FORENSICS_STRIDE_SECONDS = 2.5
FORENSICS_CHECKPOINTS = (
    "checkpoint_epoch_5.safetensors",
    "checkpoint_epoch_5.pt",
)


class ForensicsModelError(RuntimeError):
    """Raised when the WavLM+AASIST detector cannot be loaded."""


@dataclass(frozen=True)
class ForensicsSegmentPrediction:
    start_seconds: float
    end_seconds: float
    probability: float
    weight: float


@dataclass(frozen=True)
class ForensicsDetectionResult:
    probability: float
    raw_model_probability: float
    confidence: str
    model_id: str
    device: str
    segments: list[ForensicsSegmentPrediction]
    calibration: dict[str, float]

    def diagnostics(self) -> dict[str, Any]:
        return {
            "raw_model_probability": self.raw_model_probability,
            "device": self.device,
            "segments": [asdict(segment) for segment in self.segments],
            "calibration": self.calibration,
        }


class ForensicsDeepfakeDetector:
    def __init__(
        self,
        model_id: str = DEFAULT_FORENSICS_MODEL_ID,
        cache_dir: Path | None = None,
        device: str = "auto",
        calibrate: bool = True,
    ) -> None:
        self.model_id = model_id
        self.cache_dir = Path(cache_dir or DEFAULT_CACHE_DIR)
        self.device_request = device
        self.calibrate = calibrate
        self._model: Any | None = None
        self._device: str | None = None

    def predict(self, audio: DecodedAudio, features: Any) -> ForensicsDetectionResult:
        self._ensure_loaded()
        assert self._model is not None
        assert self._device is not None

        import torch

        segments: list[ForensicsSegmentPrediction] = []
        for start, end, samples in _chunk_audio(audio.samples, audio.sample_rate):
            waveform = torch.from_numpy(_prepare_window(samples, audio.sample_rate)).unsqueeze(0).to(self._device)
            with torch.inference_mode():
                bonafide = torch.sigmoid(self._model(waveform).float()).item()
            fake_probability = 1.0 - float(bonafide)
            segments.append(
                ForensicsSegmentPrediction(
                    start_seconds=round(start / audio.sample_rate, 3),
                    end_seconds=round(end / audio.sample_rate, 3),
                    probability=_truncate(fake_probability, 6),
                    weight=_truncate(_segment_weight(samples, audio.sample_rate), 6),
                )
            )

        raw_probability = _aggregate_segments(segments)
        quality = clip_quality(features)
        probability = _calibrate_probability(raw_probability, quality) if self.calibrate else raw_probability
        confidence = _confidence_label(probability, quality, segments, audio.duration_seconds)

        return ForensicsDetectionResult(
            probability=_truncate(probability, 3),
            raw_model_probability=_truncate(raw_probability, 6),
            confidence=confidence,
            model_id=self.model_id,
            device=self._device,
            segments=segments,
            calibration={
                "quality": quality,
                "quality_shrinkage": _truncate(_quality_shrinkage(quality), 6),
                "enabled": 1.0 if self.calibrate else 0.0,
            },
        )

    def _ensure_loaded(self) -> None:
        if self._model is not None:
            return

        try:
            import torch
        except Exception as exc:
            raise ForensicsModelError("Forensics backend requires torch.") from exc

        local_dir = _local_model_dir(self.model_id, self.cache_dir)
        if not local_dir.exists():
            raise ForensicsModelError(
                f'Model files are missing for "{self.model_id}". Run `python download_model.py '
                f"--model-id {self.model_id}` or place the files under {local_dir}."
            )

        checkpoint = _find_checkpoint(local_dir)
        model_py = local_dir / "model.py"
        if not model_py.exists():
            raise ForensicsModelError(f"Missing model.py in {local_dir}.")

        self._device = _resolve_device(self.device_request, torch)
        module = _load_model_module(model_py)
        with _wavlm_cache_redirect(self.cache_dir):
            model = module.DeepfakeDetector().to(self._device).eval()
        state_dict = _load_state_dict(checkpoint)
        model.load_state_dict(state_dict, strict=False)
        self._model = model


def _load_model_module(model_py: Path) -> Any:
    spec = importlib.util.spec_from_file_location("forensics_model", model_py)
    if spec is None or spec.loader is None:
        raise ForensicsModelError(f"Could not import {model_py}.")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


@contextmanager
def _wavlm_cache_redirect(cache_dir: Path) -> Any:
    try:
        import transformers
    except Exception as exc:
        raise ForensicsModelError("Forensics backend requires transformers.") from exc

    original_from_pretrained = transformers.WavLMModel.from_pretrained

    def from_pretrained_with_cache(pretrained_model_name_or_path: str, *args: Any, **kwargs: Any) -> Any:
        source = _resolve_model_source(pretrained_model_name_or_path, cache_dir)
        kwargs.setdefault("cache_dir", str(cache_dir))
        kwargs.setdefault("local_files_only", Path(source).exists())
        return original_from_pretrained(str(source), *args, **kwargs)

    transformers.WavLMModel.from_pretrained = from_pretrained_with_cache
    try:
        yield
    finally:
        transformers.WavLMModel.from_pretrained = original_from_pretrained


def _load_state_dict(checkpoint: Path) -> dict[str, Any]:
    if checkpoint.suffix == ".safetensors":
        from safetensors.torch import load_file

        return load_file(str(checkpoint))

    import torch

    loaded = torch.load(str(checkpoint), map_location="cpu", weights_only=False)
    if isinstance(loaded, dict) and "model_state_dict" in loaded:
        return loaded["model_state_dict"]
    return loaded


def _chunk_audio(samples: np.ndarray, sample_rate: int) -> list[tuple[int, int, np.ndarray]]:
    window = int(FORENSICS_SECONDS * sample_rate)
    stride = int(FORENSICS_STRIDE_SECONDS * sample_rate)
    total = samples.size
    if total <= window:
        return [(0, total, samples)]

    chunks: list[tuple[int, int, np.ndarray]] = []
    start = 0
    while start < total:
        end = min(start + window, total)
        if end - start < window // 2 and chunks:
            start = max(0, total - window)
            end = total
        chunks.append((start, end, samples[start:end]))
        if end == total:
            break
        start += stride
    return chunks


def _prepare_window(samples: np.ndarray, sample_rate: int) -> np.ndarray:
    target = int(FORENSICS_SECONDS * sample_rate)
    wav = np.asarray(samples, dtype=np.float32)
    peak = float(np.max(np.abs(wav))) if wav.size else 0.0
    if peak > 0:
        wav = wav / (peak + 1e-8)

    if wav.size < target:
        repeats = int(math.ceil(target / max(wav.size, 1)))
        wav = np.tile(wav, repeats)[:target]
    elif wav.size > target:
        start = (wav.size - target) // 2
        wav = wav[start : start + target]
    return wav.astype(np.float32)


def _aggregate_segments(segments: list[ForensicsSegmentPrediction]) -> float:
    probabilities = np.array([segment.probability for segment in segments], dtype=np.float64)
    weights = np.array([segment.weight for segment in segments], dtype=np.float64)
    weighted_mean = float(np.average(probabilities, weights=weights))
    top_count = max(1, math.ceil(probabilities.size * 0.25))
    top_mean = float(np.mean(np.sort(probabilities)[-top_count:]))
    return 0.75 * weighted_mean + 0.25 * top_mean


def _segment_weight(samples: np.ndarray, sample_rate: int) -> float:
    duration = max(samples.size / sample_rate, 0.25)
    rms = float(np.sqrt(np.mean(np.square(samples)) + 1e-12))
    loudness = min(1.0, rms / 0.05)
    return duration * (0.2 + 0.8 * loudness)


def _calibrate_probability(probability: float, quality: float) -> float:
    return 0.5 + (probability - 0.5) * _quality_shrinkage(quality)


def _quality_shrinkage(quality: float) -> float:
    return 0.6 + 0.4 * max(0.0, min(1.0, quality))


def _confidence_label(
    probability: float,
    quality: float,
    segments: list[ForensicsSegmentPrediction],
    duration_seconds: float,
) -> str:
    values = np.array([segment.probability for segment in segments], dtype=np.float64)
    margin = abs(probability - 0.5) * 2.0
    agreement = 1.0 - min(1.0, float(np.std(values)) / 0.25)
    duration_score = min(1.0, duration_seconds / FORENSICS_SECONDS)
    score = quality * 0.35 + margin * 0.35 + agreement * 0.2 + duration_score * 0.1
    if duration_seconds < 1.0 or quality < 0.25:
        return "low"
    if score >= 0.72 and duration_seconds >= FORENSICS_SECONDS:
        return "high"
    if score >= 0.45:
        return "medium"
    return "low"


def _find_checkpoint(local_dir: Path) -> Path:
    for name in FORENSICS_CHECKPOINTS:
        path = local_dir / name
        if path.exists():
            return path
    raise ForensicsModelError(
        f"Missing checkpoint in {local_dir}. Expected one of: {', '.join(FORENSICS_CHECKPOINTS)}."
    )


def _local_model_dir(model_id: str, cache_dir: Path) -> Path:
    return cache_dir / "local" / model_id.replace("/", "__")


def _resolve_device(device: str, torch: Any) -> str:
    normalized = device.lower()
    if normalized == "auto":
        return "cuda" if torch.cuda.is_available() else "cpu"
    if normalized.startswith("cuda") and not torch.cuda.is_available():
        raise ForensicsModelError("CUDA was requested but torch cannot see a CUDA device.")
    return normalized


def _truncate(value: float, places: int) -> float:
    factor = 10**places
    return math.floor(value * factor) / factor
