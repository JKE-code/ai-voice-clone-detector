from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path
from typing import Any

from voice_detector.audio import AudioDecodeError, decode_audio, extract_features
from voice_detector.detector import (
    METHODOLOGY_VERSION,
    fingerprint_audio,
    score_synthetic,
    verdict_from_probability,
)
from voice_detector.forensics_detector import (
    DEFAULT_FORENSICS_MODEL_ID,
    ForensicsDeepfakeDetector,
    ForensicsModelError,
)
from voice_detector.hf_detector import DEFAULT_CACHE_DIR, DEFAULT_MODEL_ID, HuggingFaceDeepfakeDetector, ModelLoadError

MAX_AUDIO_BYTES = 25 * 1024 * 1024
DEFAULT_ENSEMBLE_MODELS = [
    DEFAULT_FORENSICS_MODEL_ID,
    DEFAULT_MODEL_ID,
    "garystafford/wav2vec2-deepfake-voice-detector",
]


def detect_file(
    path: Path,
    include_diagnostics: bool = False,
    backend: str = "hf",
    model_id: str = DEFAULT_MODEL_ID,
    model_ids: list[str] | None = None,
    cache_dir: Path = DEFAULT_CACHE_DIR,
    device: str = "auto",
    calibrate: bool = True,
) -> dict[str, Any]:
    start = time.perf_counter()
    audio_bytes = path.read_bytes()
    if len(audio_bytes) > MAX_AUDIO_BYTES:
        raise ValueError("Audio file exceeds 25 MB limit.")

    decoded = decode_audio(audio_bytes)
    features = extract_features(decoded)

    diagnostics: dict[str, Any]
    if backend == "forensics":
        prediction = ForensicsDeepfakeDetector(
            model_id=model_id if model_id != DEFAULT_MODEL_ID else DEFAULT_FORENSICS_MODEL_ID,
            cache_dir=cache_dir,
            device=device,
            calibrate=calibrate,
        ).predict(decoded, features)
        probability = prediction.probability
        confidence = prediction.confidence
        model_name = prediction.model_id
        methodology = f"wavlm-aasist-forensics-v1:{prediction.model_id}"
        diagnostics = {
            **prediction.diagnostics(),
            "audio_features": features.__dict__,
        }
    elif backend == "hf":
        prediction = HuggingFaceDeepfakeDetector(
            model_id=model_id,
            cache_dir=cache_dir,
            device=device,
            calibrate=calibrate,
        ).predict(decoded, features)
        probability = prediction.probability
        confidence = prediction.confidence
        model_name = prediction.model_id
        methodology = f"hf-wav2vec2-deepfake-v1:{model_id}"
        diagnostics = {
            **prediction.diagnostics(),
            "audio_features": features.__dict__,
        }
    elif backend == "ensemble":
        ids = model_ids or DEFAULT_ENSEMBLE_MODELS
        predictions = [_predict_model(item, decoded, features, cache_dir, device, calibrate) for item in ids]
        probability = _ensemble_probability([prediction.probability for prediction in predictions])
        confidence = _ensemble_confidence([prediction.probability for prediction in predictions], features.duration_seconds)
        model_name = "ensemble:" + ",".join(prediction.model_id for prediction in predictions)
        methodology = "hf-ensemble-v1:" + ",".join(prediction.model_id for prediction in predictions)
        diagnostics = {
            "models": [
                {
                    "model_id": prediction.model_id,
                    "probability": prediction.probability,
                    "raw_model_probability": prediction.raw_model_probability,
                    "confidence": prediction.confidence,
                    "class_probabilities": prediction.class_probabilities,
                    "segments": [segment.__dict__ for segment in prediction.segments],
                    "calibration": prediction.calibration,
                }
                for prediction in predictions
            ],
            "audio_features": features.__dict__,
        }
    elif backend == "heuristic":
        probability, confidence, diagnostics = score_synthetic(features)
        model_name = "local acoustic heuristic"
        methodology = METHODOLOGY_VERSION
    else:
        raise ValueError('backend must be "hf" or "heuristic".')

    verdict = verdict_from_probability(probability, confidence)
    duration_ms = int((time.perf_counter() - start) * 1000)

    result: dict[str, Any] = {
        "verdict": verdict,
        "probability": probability,
        "confidence": confidence,
        "model": model_name,
        "methodology": methodology,
        "duration_ms": duration_ms,
        "fingerprint": fingerprint_audio(audio_bytes),
        "metadata": {
            "filename": path.name,
            "duration_seconds": round(decoded.duration_seconds, 3),
            "sample_rate": decoded.sample_rate,
            "decoder": decoded.decoder,
        },
    }
    if include_diagnostics:
        result["diagnostics"] = diagnostics
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description="Return AI voice probability JSON for an audio file.")
    parser.add_argument("audio", type=Path, help="Path to a WAV, MP3, M4A, or WebM audio file.")
    parser.add_argument(
        "--backend",
        choices=["forensics", "hf", "ensemble", "heuristic"],
        default="forensics",
        help="Detection backend. forensics is the production WavLM+AASIST path.",
    )
    parser.add_argument(
        "--model-id",
        default=DEFAULT_MODEL_ID,
        help="Hugging Face model id. Ignored by default for --backend forensics unless explicitly set.",
    )
    parser.add_argument(
        "--models",
        default=",".join(DEFAULT_ENSEMBLE_MODELS),
        help="Comma-separated Hugging Face model ids for --backend ensemble.",
    )
    parser.add_argument("--cache-dir", type=Path, default=DEFAULT_CACHE_DIR, help="Local Hugging Face cache directory.")
    parser.add_argument("--device", default="auto", help='Inference device: "auto", "cpu", "cuda", or "cuda:0".')
    parser.add_argument(
        "--no-calibration",
        action="store_true",
        help="Return the raw aggregated model probability without quality calibration.",
    )
    parser.add_argument(
        "--diagnostics",
        action="store_true",
        help="Include acoustic feature diagnostics in the JSON output.",
    )
    args = parser.parse_args()

    if not args.audio.exists():
        print(json.dumps({"error": f"File not found: {args.audio}"}), file=sys.stderr)
        return 2
    if not args.audio.is_file():
        print(json.dumps({"error": f"Not a file: {args.audio}"}), file=sys.stderr)
        return 2

    try:
        result = detect_file(
            args.audio,
            include_diagnostics=args.diagnostics,
            backend=args.backend,
            model_id=args.model_id,
            model_ids=_parse_model_ids(args.models),
            cache_dir=args.cache_dir,
            device=args.device,
            calibrate=not args.no_calibration,
        )
    except (AudioDecodeError, ForensicsModelError, ModelLoadError, ValueError, OSError) as exc:
        print(json.dumps({"error": str(exc)}), file=sys.stderr)
        return 1

    print(json.dumps(result, indent=2))
    return 0


def _parse_model_ids(raw: str) -> list[str]:
    return [item.strip() for item in raw.split(",") if item.strip()]


def _predict_model(
    model_id: str,
    decoded: Any,
    features: Any,
    cache_dir: Path,
    device: str,
    calibrate: bool,
) -> Any:
    if model_id.startswith("eliya/forensics_"):
        return ForensicsDeepfakeDetector(
            model_id=model_id,
            cache_dir=cache_dir,
            device=device,
            calibrate=calibrate,
        ).predict(decoded, features)

    return HuggingFaceDeepfakeDetector(
        model_id=model_id,
        cache_dir=cache_dir,
        device=device,
        calibrate=calibrate,
    ).predict(decoded, features)


def _ensemble_probability(probabilities: list[float]) -> float:
    if not probabilities:
        raise ValueError("Ensemble needs at least one model.")
    ordered = sorted(probabilities)
    mean_probability = sum(ordered) / len(ordered)
    max_probability = ordered[-1]
    # A cautious detector should listen when one specialist is highly suspicious,
    # but still reward agreement across models.
    probability = 0.68 * mean_probability + 0.32 * max_probability
    return _truncate(probability, 3)


def _ensemble_confidence(probabilities: list[float], duration_seconds: float) -> str:
    if not probabilities:
        return "low"
    if len(probabilities) == 1:
        return "medium" if duration_seconds >= 2.5 else "low"

    mean_probability = sum(probabilities) / len(probabilities)
    disagreement = max(probabilities) - min(probabilities)
    margin = abs(mean_probability - 0.5) * 2
    if duration_seconds < 1.0 or disagreement >= 0.55:
        return "low"
    if duration_seconds >= 2.5 and disagreement <= 0.25 and margin >= 0.45:
        return "high"
    return "medium"


def _truncate(value: float, places: int) -> float:
    factor = 10**places
    return int(value * factor) / factor


if __name__ == "__main__":
    raise SystemExit(main())
