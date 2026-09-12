from __future__ import annotations

import math
import os
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

import numpy as np

from voice_detector.audio import DecodedAudio
from voice_detector.detector import clip_quality

DEFAULT_MODEL_ID = "Hemgg/Deepfake-audio-detection"
DEFAULT_CACHE_DIR = Path(__file__).resolve().parents[1] / ".models" / "huggingface"
MIN_MODEL_SECONDS = 2.5
CHUNK_SECONDS = 8.0
CHUNK_STRIDE_SECONDS = 6.0


class ModelLoadError(RuntimeError):
    """Raised when the production model cannot be loaded."""


@dataclass(frozen=True)
class SegmentPrediction:
    start_seconds: float
    end_seconds: float
    probability: float
    weight: float


@dataclass(frozen=True)
class HFDetectionResult:
    probability: float
    raw_model_probability: float
    confidence: str
    model_id: str
    device: str
    class_probabilities: dict[str, float]
    segments: list[SegmentPrediction]
    calibration: dict[str, float]

    def diagnostics(self) -> dict[str, Any]:
        return {
            "raw_model_probability": self.raw_model_probability,
            "device": self.device,
            "class_probabilities": self.class_probabilities,
            "segments": [asdict(segment) for segment in self.segments],
            "calibration": self.calibration,
        }


class HuggingFaceDeepfakeDetector:
    def __init__(
        self,
        model_id: str = DEFAULT_MODEL_ID,
        cache_dir: Path | None = None,
        device: str = "auto",
        calibrate: bool = True,
    ) -> None:
        self.model_id = model_id
        self.cache_dir = Path(cache_dir or DEFAULT_CACHE_DIR)
        self.device_request = device
        self.calibrate = calibrate
        self._model: Any | None = None
        self._feature_extractor: Any | None = None
        self._device: str | None = None
        self._fake_index: int | None = None

    def predict(self, audio: DecodedAudio, features: Any) -> HFDetectionResult:
        self._ensure_loaded()
        assert self._model is not None
        assert self._feature_extractor is not None
        assert self._device is not None
        assert self._fake_index is not None

        import torch

        chunks = _chunk_audio(audio.samples, audio.sample_rate)
        segment_predictions: list[SegmentPrediction] = []
        probability_vectors: list[np.ndarray] = []

        for start, end, samples in chunks:
            inputs = self._feature_extractor(
                samples,
                sampling_rate=audio.sample_rate,
                return_tensors="pt",
                padding=True,
            )
            inputs = {name: value.to(self._device) for name, value in inputs.items()}
            with torch.inference_mode():
                logits = self._model(**inputs).logits
                probs = torch.softmax(logits, dim=-1)[0].detach().cpu().numpy()

            fake_probability = float(probs[self._fake_index])
            weight = _segment_weight(samples, audio.sample_rate)
            probability_vectors.append(probs)
            segment_predictions.append(
                SegmentPrediction(
                    start_seconds=round(start / audio.sample_rate, 3),
                    end_seconds=round(end / audio.sample_rate, 3),
                    probability=_truncate(fake_probability, 6),
                    weight=_truncate(weight, 6),
                )
            )

        raw_probability = _aggregate_segments(segment_predictions)
        quality = clip_quality(features)
        probability = _calibrate_probability(raw_probability, quality) if self.calibrate else raw_probability
        confidence = _confidence_label(probability, quality, segment_predictions, audio.duration_seconds)
        class_probabilities = self._aggregate_class_probabilities(probability_vectors, segment_predictions)

        return HFDetectionResult(
            probability=_truncate(probability, 3),
            raw_model_probability=_truncate(raw_probability, 6),
            confidence=confidence,
            model_id=self.model_id,
            device=self._device,
            class_probabilities=class_probabilities,
            segments=segment_predictions,
            calibration={
                "quality": quality,
                "quality_shrinkage": _truncate(_quality_shrinkage(quality), 6),
                "enabled": 1.0 if self.calibrate else 0.0,
            },
        )

    def _ensure_loaded(self) -> None:
        if self._model is not None and self._feature_extractor is not None:
            return

        os.environ.setdefault("HF_HUB_DISABLE_XET", "1")
        try:
            import torch
            from transformers import AutoFeatureExtractor, AutoModelForAudioClassification
        except Exception as exc:
            raise ModelLoadError(
                "Production backend requires torch and transformers. Install requirements.txt first."
            ) from exc

        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self._device = _resolve_device(self.device_request, torch)
        load_source = _resolve_model_source(self.model_id, self.cache_dir)

        try:
            self._feature_extractor = AutoFeatureExtractor.from_pretrained(
                str(load_source),
                cache_dir=str(self.cache_dir),
                local_files_only=Path(load_source).exists(),
            )
            self._model = AutoModelForAudioClassification.from_pretrained(
                str(load_source),
                cache_dir=str(self.cache_dir),
                local_files_only=Path(load_source).exists(),
            )
        except Exception as exc:
            raise ModelLoadError(
                f'Could not load Hugging Face model "{self.model_id}". '
                "Run `python download_model.py` while online, or pass `--backend heuristic`."
            ) from exc

        self._model.to(self._device)
        self._model.eval()
        self._fake_index = _detect_fake_index(self._model.config)

    def _aggregate_class_probabilities(
        self,
        vectors: list[np.ndarray],
        segments: list[SegmentPrediction],
    ) -> dict[str, float]:
        assert self._model is not None
        id2label = getattr(self._model.config, "id2label", None) or {}
        labels = [_label_for_index(id2label, index) for index in range(len(vectors[0]))]
        weights = np.array([segment.weight for segment in segments], dtype=np.float64)
        stacked = np.stack(vectors).astype(np.float64)
        averaged = np.average(stacked, axis=0, weights=weights)
        return {labels[index]: _truncate(float(value), 6) for index, value in enumerate(averaged)}


def download_model(model_id: str = DEFAULT_MODEL_ID, cache_dir: Path | None = None) -> Path:
    os.environ.setdefault("HF_HUB_DISABLE_XET", "1")
    try:
        from huggingface_hub import snapshot_download
        from transformers import AutoFeatureExtractor, AutoModelForAudioClassification
    except Exception as exc:
        raise ModelLoadError("Downloading requires torch and transformers. Install requirements.txt first.") from exc

    target_cache = Path(cache_dir or DEFAULT_CACHE_DIR)
    target_cache.mkdir(parents=True, exist_ok=True)
    local_dir = _local_model_dir(model_id, target_cache)
    local_dir.mkdir(parents=True, exist_ok=True)

    snapshot_download(
        repo_id=model_id,
        local_dir=str(local_dir),
        allow_patterns=[
            "*.json",
            "*.py",
            "*.safetensors",
            "*.bin",
            "*.pt",
            "*.pth",
            "*.txt",
            "*.model",
            "*.tokenizer",
            "*.vocab",
        ],
    )
    try:
        AutoFeatureExtractor.from_pretrained(str(local_dir))
        AutoModelForAudioClassification.from_pretrained(str(local_dir))
    except Exception:
        if (
            not any(local_dir.glob("*.safetensors"))
            and not any(local_dir.glob("*.bin"))
            and not any(local_dir.glob("*.pt"))
            and not any(local_dir.glob("*.pth"))
        ):
            raise
    return local_dir


def _chunk_audio(samples: np.ndarray, sample_rate: int) -> list[tuple[int, int, np.ndarray]]:
    max_samples = int(CHUNK_SECONDS * sample_rate)
    stride_samples = int(CHUNK_STRIDE_SECONDS * sample_rate)
    min_samples = int(MIN_MODEL_SECONDS * sample_rate)
    total = samples.size

    if total <= max_samples:
        return [(0, total, _pad_for_model(samples, min_samples))]

    chunks: list[tuple[int, int, np.ndarray]] = []
    start = 0
    while start < total:
        end = min(start + max_samples, total)
        if end - start < min_samples and chunks:
            start = max(0, total - max_samples)
            end = total
        chunks.append((start, end, _pad_for_model(samples[start:end], min_samples)))
        if end == total:
            break
        start += stride_samples
    return chunks


def _pad_for_model(samples: np.ndarray, min_samples: int) -> np.ndarray:
    samples = np.asarray(samples, dtype=np.float32)
    if samples.size >= min_samples:
        return samples
    return np.pad(samples, (0, min_samples - samples.size), mode="constant").astype(np.float32)


def _segment_weight(samples: np.ndarray, sample_rate: int) -> float:
    duration = max(samples.size / sample_rate, 0.25)
    rms = float(np.sqrt(np.mean(np.square(samples)) + 1e-12))
    loudness = min(1.0, rms / 0.05)
    return duration * (0.2 + 0.8 * loudness)


def _aggregate_segments(segments: list[SegmentPrediction]) -> float:
    probabilities = np.array([segment.probability for segment in segments], dtype=np.float64)
    weights = np.array([segment.weight for segment in segments], dtype=np.float64)
    weighted_mean = float(np.average(probabilities, weights=weights))
    top_count = max(1, math.ceil(probabilities.size * 0.25))
    top_mean = float(np.mean(np.sort(probabilities)[-top_count:]))
    return 0.8 * weighted_mean + 0.2 * top_mean


def _calibrate_probability(probability: float, quality: float) -> float:
    shrinkage = _quality_shrinkage(quality)
    return 0.5 + (probability - 0.5) * shrinkage


def _quality_shrinkage(quality: float) -> float:
    return 0.55 + 0.45 * max(0.0, min(1.0, quality))


def _confidence_label(
    probability: float,
    quality: float,
    segments: list[SegmentPrediction],
    duration_seconds: float,
) -> str:
    segment_values = np.array([segment.probability for segment in segments], dtype=np.float64)
    margin = abs(probability - 0.5) * 2.0
    agreement = 1.0 - min(1.0, float(np.std(segment_values)) / 0.25)
    duration_score = min(1.0, duration_seconds / MIN_MODEL_SECONDS)
    score = quality * 0.4 + margin * 0.35 + agreement * 0.15 + duration_score * 0.10

    if duration_seconds < 1.0 or quality < 0.25:
        return "low"
    if score >= 0.72 and duration_seconds >= MIN_MODEL_SECONDS:
        return "high"
    if score >= 0.45:
        return "medium"
    return "low"


def _resolve_device(device: str, torch: Any) -> str:
    normalized = device.lower()
    if normalized == "auto":
        return "cuda" if torch.cuda.is_available() else "cpu"
    if normalized.startswith("cuda") and not torch.cuda.is_available():
        raise ModelLoadError("CUDA was requested but torch cannot see a CUDA device.")
    return normalized


def _detect_fake_index(config: Any) -> int:
    id2label = getattr(config, "id2label", None) or {}
    normalized = {int(index): str(label).lower() for index, label in id2label.items()}
    fake_keywords = ("fake", "spoof", "synthetic", "generated", "ai", "deepfake")
    real_keywords = ("real", "bonafide", "bona fide", "authentic", "human")

    for index, label in normalized.items():
        if any(keyword in label for keyword in fake_keywords):
            return index

    for index, label in normalized.items():
        if any(keyword in label for keyword in real_keywords):
            continue
        return index

    num_labels = int(getattr(config, "num_labels", 2) or 2)
    if num_labels == 2:
        return 1
    raise ModelLoadError(f"Could not infer fake/synthetic label from model labels: {id2label}")


def _local_model_dir(model_id: str, cache_dir: Path) -> Path:
    safe_name = model_id.replace("/", "__")
    return cache_dir / "local" / safe_name


def _resolve_model_source(model_id: str, cache_dir: Path) -> str | Path:
    local_dir = _local_model_dir(model_id, cache_dir)
    if _has_local_model_files(local_dir):
        return local_dir

    snapshot_dir = _latest_snapshot_dir(model_id, cache_dir)
    if snapshot_dir is not None:
        return snapshot_dir

    return model_id


def _latest_snapshot_dir(model_id: str, cache_dir: Path) -> Path | None:
    namespace, _, name = model_id.partition("/")
    if not namespace or not name:
        return None

    snapshots_root = cache_dir / f"models--{namespace}--{name}" / "snapshots"
    if not snapshots_root.exists():
        return None

    candidates = [path for path in snapshots_root.iterdir() if path.is_dir() and _has_local_model_files(path)]
    if not candidates:
        return None
    return max(candidates, key=lambda path: path.stat().st_mtime)


def _has_local_model_files(path: Path) -> bool:
    if not path.exists():
        return False
    has_config = (path / "config.json").exists()
    has_weights = any((path / name).exists() for name in ("model.safetensors", "pytorch_model.bin"))
    return has_config and has_weights


def _label_for_index(id2label: dict[Any, Any], index: int) -> str:
    return str(id2label.get(index) or id2label.get(str(index)) or f"label_{index}").lower().replace(" ", "_")


def _truncate(value: float, places: int) -> float:
    factor = 10**places
    return math.floor(value * factor) / factor
