from __future__ import annotations

import argparse
import json
import time
from pathlib import Path
from typing import Any

from voice_detector.audio import decode_audio, extract_features
from voice_detector.detector import fingerprint_audio, verdict_from_probability
from voice_detector.forensics_detector import ForensicsDeepfakeDetector, ForensicsModelError
from voice_detector.hf_detector import DEFAULT_CACHE_DIR, HuggingFaceDeepfakeDetector, ModelLoadError

DEFAULT_CANDIDATES = [
    "eliya/forensics_0.3B_base_deepfake_classifier",
    "Hemgg/Deepfake-audio-detection",
    "garystafford/wav2vec2-deepfake-voice-detector",
    "Gustking/wav2vec2-large-xlsr-deepfake-audio-classification",
    "MelodyMachine/Deepfake-audio-detection-V2",
    "shivam-2211/voice-detection-model",
]


def main() -> int:
    parser = argparse.ArgumentParser(description="Benchmark Hugging Face audio deepfake models on labeled files.")
    parser.add_argument("--real", type=Path, action="append", default=[], help="Known-real audio file.")
    parser.add_argument("--fake", type=Path, action="append", default=[], help="Known-AI/fake audio file.")
    parser.add_argument(
        "--models",
        default=",".join(DEFAULT_CANDIDATES[:2]),
        help="Comma-separated model ids to test.",
    )
    parser.add_argument("--cache-dir", type=Path, default=DEFAULT_CACHE_DIR, help="Local Hugging Face cache directory.")
    parser.add_argument("--device", default="auto", help='Inference device: "auto", "cpu", "cuda", or "cuda:0".')
    parser.add_argument("--no-calibration", action="store_true", help="Use raw aggregated model probabilities.")
    args = parser.parse_args()

    samples = [("real", path) for path in args.real] + [("fake", path) for path in args.fake]
    if not samples:
        raise SystemExit("Provide at least one --real or --fake file.")

    decoded_samples = []
    for expected, path in samples:
        audio_bytes = path.read_bytes()
        decoded = decode_audio(audio_bytes)
        features = extract_features(decoded)
        decoded_samples.append((expected, path, audio_bytes, decoded, features))

    output: dict[str, Any] = {"models": []}
    for model_id in _parse_model_ids(args.models):
        started = time.perf_counter()
        model_payload: dict[str, Any] = {"model_id": model_id, "samples": []}
        try:
            detector = _make_detector(model_id, args.cache_dir, args.device, not args.no_calibration)
            correct = 0
            for expected, path, audio_bytes, decoded, features in decoded_samples:
                prediction = detector.predict(decoded, features)
                verdict = verdict_from_probability(prediction.probability, prediction.confidence)
                predicted = "fake" if prediction.probability >= 0.5 else "real"
                correct += int(predicted == expected)
                model_payload["samples"].append(
                    {
                        "file": str(path),
                        "expected": expected,
                        "predicted": predicted,
                        "verdict": verdict,
                        "probability": prediction.probability,
                        "raw_model_probability": prediction.raw_model_probability,
                        "confidence": prediction.confidence,
                        "fingerprint": fingerprint_audio(audio_bytes),
                    }
                )
            model_payload["accuracy_on_supplied_files"] = correct / len(decoded_samples)
        except (ForensicsModelError, ModelLoadError) as exc:
            model_payload["error"] = str(exc)
        model_payload["duration_ms"] = int((time.perf_counter() - started) * 1000)
        output["models"].append(model_payload)

    print(json.dumps(output, indent=2))
    return 0


def _parse_model_ids(raw: str) -> list[str]:
    return [item.strip() for item in raw.split(",") if item.strip()]


def _make_detector(model_id: str, cache_dir: Path, device: str, calibrate: bool) -> Any:
    if model_id.startswith("eliya/forensics_"):
        return ForensicsDeepfakeDetector(model_id=model_id, cache_dir=cache_dir, device=device, calibrate=calibrate)
    return HuggingFaceDeepfakeDetector(model_id=model_id, cache_dir=cache_dir, device=device, calibrate=calibrate)


if __name__ == "__main__":
    raise SystemExit(main())
