from __future__ import annotations

import hashlib
import math
from dataclasses import asdict

from voice_detector.audio import AudioFeatures

METHODOLOGY_VERSION = "local-heuristic-v1"


def score_synthetic(features: AudioFeatures) -> tuple[float, str, dict[str, float]]:
    """Return probability that speech-like audio was machine generated.

    The score is intentionally conservative. When a clip is short, silent,
    clipped, or otherwise low quality, the final probability is pulled toward
    0.5 and confidence drops.
    """

    signals = {
        "clean_noise_floor": _inverse_range(features.noise_floor_db, -30.0, -62.0),
        "steady_loudness": _inverse_range(features.rms_cv, 1.1, 0.35),
        "steady_spectrum": _inverse_range(features.spectral_centroid_cv, 0.8, 0.25),
        "steady_zero_crossing": _inverse_range(features.zero_crossing_rate_cv, 0.9, 0.28),
        "strong_periodicity": _range(features.periodicity_mean, 0.25, 0.68),
        "low_flatness": _inverse_range(features.spectral_flatness_mean, 0.38, 0.08),
        "low_flux": _inverse_range(features.spectral_flux_mean, 0.18, 0.045),
        "continuous_voice": _inverse_range(features.silence_ratio, 0.42, 0.04),
        "low_dynamic_range": _inverse_range(features.dynamic_range_db, 30.0, 10.0),
    }

    human_cues = {
        "large_dynamic_range": _range(features.dynamic_range_db, 16.0, 34.0),
        "natural_pauses": _range(features.silence_ratio, 0.22, 0.62),
        "noisy_texture": _range(features.spectral_flatness_mean, 0.24, 0.55),
        "unstable_spectrum": _range(features.spectral_centroid_cv, 0.45, 1.1),
    }

    synthetic_weight = {
        "clean_noise_floor": 0.13,
        "steady_loudness": 0.10,
        "steady_spectrum": 0.10,
        "steady_zero_crossing": 0.07,
        "strong_periodicity": 0.12,
        "low_flatness": 0.08,
        "low_flux": 0.08,
        "continuous_voice": 0.07,
        "low_dynamic_range": 0.07,
    }
    human_weight = {
        "large_dynamic_range": 0.08,
        "natural_pauses": 0.06,
        "noisy_texture": 0.05,
        "unstable_spectrum": 0.05,
    }

    raw = 0.42
    raw += sum(signals[name] * synthetic_weight[name] for name in signals)
    raw -= sum(human_cues[name] * human_weight[name] for name in human_cues)

    quality = clip_quality(features)
    probability = 0.5 + (max(0.01, min(0.99, raw)) - 0.5) * (0.35 + 0.65 * quality)
    probability = max(0.001, min(0.999, probability))

    confidence = confidence_label(features, quality)
    diagnostics = {
        **{f"signal_{name}": value for name, value in signals.items()},
        **{f"human_cue_{name}": value for name, value in human_cues.items()},
        "quality": quality,
        "raw_probability": raw,
        **asdict(features),
    }
    return _truncate(probability, 3), confidence, diagnostics


def confidence_label(features: AudioFeatures, quality: float | None = None) -> str:
    quality = clip_quality(features) if quality is None else quality
    if features.duration_seconds >= 3.0 and quality >= 0.78:
        return "high"
    if features.duration_seconds >= 1.0 and quality >= 0.45:
        return "medium"
    return "low"


def verdict_from_probability(probability: float, confidence: str) -> str:
    if confidence == "low" and 0.25 < probability < 0.75:
        return "uncertain"
    if probability >= 0.65:
        return "likely_synthetic"
    if probability <= 0.35:
        return "likely_authentic"
    return "uncertain"


def clip_quality(features: AudioFeatures) -> float:
    duration_score = _range(features.duration_seconds, 0.7, 3.0)
    active_score = _range(features.active_ratio, 0.12, 0.55)
    clipping_score = 1.0 - _range(features.clipping_ratio, 0.002, 0.04)
    loudness_score = _range(features.rms_mean, 0.004, 0.045)
    speech_like_score = 1.0 - abs(features.zero_crossing_rate_mean - 0.09) / 0.18
    speech_like_score = max(0.0, min(1.0, speech_like_score))

    quality = (
        duration_score * 0.33
        + active_score * 0.24
        + clipping_score * 0.16
        + loudness_score * 0.14
        + speech_like_score * 0.13
    )
    return _truncate(max(0.0, min(1.0, quality)), 3)


def fingerprint_audio(data: bytes) -> str:
    return f"sha256:{hashlib.sha256(data).hexdigest()}"


def verdict_id_from_fingerprint(fingerprint: str) -> str:
    digest = fingerprint.split(":", 1)[-1]
    return digest[:12]


def _range(value: float, low: float, high: float) -> float:
    if high == low:
        return 0.0
    return max(0.0, min(1.0, (value - low) / (high - low)))


def _inverse_range(value: float, low: float, high: float) -> float:
    return 1.0 - _range(value, low, high)


def _truncate(value: float, places: int) -> float:
    factor = 10**places
    return math.floor(value * factor) / factor
