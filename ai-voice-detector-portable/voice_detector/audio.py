from __future__ import annotations

import io
import math
import subprocess
import wave
from dataclasses import dataclass

import numpy as np

TARGET_SAMPLE_RATE = 16_000
MAX_ANALYSIS_SECONDS = 120


class AudioDecodeError(ValueError):
    """Raised when uploaded bytes cannot be decoded as audio."""


@dataclass(frozen=True)
class DecodedAudio:
    samples: np.ndarray
    sample_rate: int
    duration_seconds: float
    decoder: str


@dataclass(frozen=True)
class AudioFeatures:
    duration_seconds: float
    sample_rate: int
    active_ratio: float
    silence_ratio: float
    rms_mean: float
    rms_cv: float
    dynamic_range_db: float
    zero_crossing_rate_mean: float
    zero_crossing_rate_cv: float
    spectral_centroid_mean: float
    spectral_centroid_cv: float
    spectral_flatness_mean: float
    spectral_flux_mean: float
    spectral_flux_std: float
    spectral_entropy_mean: float
    high_frequency_ratio: float
    periodicity_mean: float
    noise_floor_db: float
    clipping_ratio: float
    decoder: str


def decode_audio(data: bytes) -> DecodedAudio:
    if not data:
        raise AudioDecodeError("Uploaded audio is empty.")

    try:
        return _decode_with_ffmpeg(data)
    except AudioDecodeError:
        return _decode_wav(data)


def extract_features(audio: DecodedAudio) -> AudioFeatures:
    samples = _sanitize_samples(audio.samples)
    if samples.size < TARGET_SAMPLE_RATE // 4:
        raise AudioDecodeError("Audio clip must contain at least 0.25 seconds of decodable audio.")

    frame_size = int(audio.sample_rate * 0.025)
    hop_size = int(audio.sample_rate * 0.010)
    frames = _frame_signal(samples, frame_size, hop_size)
    if frames.size == 0:
        raise AudioDecodeError("Audio clip is too short to analyze.")

    window = np.hanning(frame_size).astype(np.float32)
    windowed = frames * window
    rms = np.sqrt(np.mean(np.square(frames), axis=1) + 1e-12)

    active_threshold = max(float(np.percentile(rms, 35) * 1.35), 0.003)
    active_mask = rms > active_threshold
    if active_mask.mean() < 0.05:
        active_threshold = max(float(np.percentile(rms, 65) * 0.5), 0.001)
        active_mask = rms > active_threshold

    active_rms = rms[active_mask] if np.any(active_mask) else rms
    active_ratio = float(np.mean(active_mask))
    silence_ratio = 1.0 - active_ratio

    dynamic_range_db = _db_ratio(float(np.percentile(active_rms, 95)), float(np.percentile(active_rms, 10)))
    rms_mean = float(np.mean(active_rms))
    rms_cv = _coefficient_of_variation(active_rms)

    signs = np.signbit(frames)
    zcr = np.mean(signs[:, 1:] != signs[:, :-1], axis=1)
    active_zcr = zcr[active_mask] if np.any(active_mask) else zcr

    spectra = np.abs(np.fft.rfft(windowed, axis=1)).astype(np.float32) + 1e-12
    power = np.square(spectra)
    freqs = np.fft.rfftfreq(frame_size, d=1.0 / audio.sample_rate).astype(np.float32)
    power_sum = np.sum(power, axis=1) + 1e-12

    centroid = np.sum(power * freqs, axis=1) / power_sum
    active_centroid = centroid[active_mask] if np.any(active_mask) else centroid

    flatness = np.exp(np.mean(np.log(power), axis=1)) / (np.mean(power, axis=1) + 1e-12)
    active_flatness = flatness[active_mask] if np.any(active_mask) else flatness

    normalized_power = power / power_sum[:, None]
    entropy = -np.sum(normalized_power * np.log2(normalized_power + 1e-12), axis=1) / math.log2(power.shape[1])
    active_entropy = entropy[active_mask] if np.any(active_mask) else entropy

    normalized_spectra = spectra / (np.linalg.norm(spectra, axis=1, keepdims=True) + 1e-12)
    flux = np.sqrt(np.mean(np.diff(normalized_spectra, axis=0) ** 2, axis=1))
    pair_active_mask = active_mask[:-1] & active_mask[1:]
    active_flux = flux[pair_active_mask] if flux.size and np.any(pair_active_mask) else flux

    high_frequency_mask = freqs >= 6_000
    high_frequency_energy = np.sum(power[:, high_frequency_mask], axis=1)
    high_frequency_ratio = float(np.mean(high_frequency_energy / power_sum))

    periodicity = _estimate_periodicity(frames[active_mask] if np.any(active_mask) else frames, audio.sample_rate)

    sample_abs = np.abs(samples)
    noise_floor_db = _db_ratio(float(np.percentile(sample_abs, 10)), float(np.sqrt(np.mean(np.square(samples))) + 1e-12))
    clipping_ratio = float(np.mean(sample_abs > 0.995))

    return AudioFeatures(
        duration_seconds=audio.duration_seconds,
        sample_rate=audio.sample_rate,
        active_ratio=active_ratio,
        silence_ratio=silence_ratio,
        rms_mean=rms_mean,
        rms_cv=rms_cv,
        dynamic_range_db=dynamic_range_db,
        zero_crossing_rate_mean=float(np.mean(active_zcr)),
        zero_crossing_rate_cv=_coefficient_of_variation(active_zcr),
        spectral_centroid_mean=float(np.mean(active_centroid)),
        spectral_centroid_cv=_coefficient_of_variation(active_centroid),
        spectral_flatness_mean=float(np.mean(active_flatness)),
        spectral_flux_mean=float(np.mean(active_flux)) if active_flux.size else 0.0,
        spectral_flux_std=float(np.std(active_flux)) if active_flux.size else 0.0,
        spectral_entropy_mean=float(np.mean(active_entropy)),
        high_frequency_ratio=high_frequency_ratio,
        periodicity_mean=periodicity,
        noise_floor_db=noise_floor_db,
        clipping_ratio=clipping_ratio,
        decoder=audio.decoder,
    )


def _decode_with_ffmpeg(data: bytes) -> DecodedAudio:
    try:
        import imageio_ffmpeg

        ffmpeg_path = imageio_ffmpeg.get_ffmpeg_exe()
    except Exception as exc:  # pragma: no cover - exercised only when dependency is missing
        raise AudioDecodeError("ffmpeg is unavailable.") from exc

    command = [
        ffmpeg_path,
        "-hide_banner",
        "-loglevel",
        "error",
        "-nostdin",
        "-i",
        "pipe:0",
        "-t",
        str(MAX_ANALYSIS_SECONDS),
        "-vn",
        "-ac",
        "1",
        "-ar",
        str(TARGET_SAMPLE_RATE),
        "-f",
        "f32le",
        "pipe:1",
    ]

    try:
        completed = subprocess.run(
            command,
            input=data,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
            timeout=30,
        )
    except (OSError, subprocess.SubprocessError) as exc:
        raise AudioDecodeError("Could not launch ffmpeg decoder.") from exc

    if completed.returncode != 0 or not completed.stdout:
        message = completed.stderr.decode("utf-8", errors="ignore").strip()
        raise AudioDecodeError(message or "Could not decode audio.")

    samples = np.frombuffer(completed.stdout, dtype=np.float32).copy()
    samples = _sanitize_samples(samples)
    duration = samples.size / TARGET_SAMPLE_RATE
    return DecodedAudio(samples=samples, sample_rate=TARGET_SAMPLE_RATE, duration_seconds=duration, decoder="ffmpeg")


def _decode_wav(data: bytes) -> DecodedAudio:
    try:
        with wave.open(io.BytesIO(data), "rb") as wav:
            sample_rate = wav.getframerate()
            channels = wav.getnchannels()
            sample_width = wav.getsampwidth()
            raw = wav.readframes(min(wav.getnframes(), sample_rate * MAX_ANALYSIS_SECONDS))
    except (wave.Error, EOFError) as exc:
        raise AudioDecodeError("Could not decode audio. Try WAV, MP3, M4A, or WebM.") from exc

    samples = _pcm_to_float(raw, sample_width)
    if channels > 1:
        usable = (samples.size // channels) * channels
        samples = samples[:usable].reshape(-1, channels).mean(axis=1)

    if sample_rate != TARGET_SAMPLE_RATE:
        from scipy.signal import resample_poly

        gcd = math.gcd(sample_rate, TARGET_SAMPLE_RATE)
        samples = resample_poly(samples, TARGET_SAMPLE_RATE // gcd, sample_rate // gcd).astype(np.float32)
        sample_rate = TARGET_SAMPLE_RATE

    samples = _sanitize_samples(samples)
    duration = samples.size / sample_rate
    return DecodedAudio(samples=samples, sample_rate=sample_rate, duration_seconds=duration, decoder="wave")


def _pcm_to_float(raw: bytes, sample_width: int) -> np.ndarray:
    if sample_width == 1:
        pcm = np.frombuffer(raw, dtype=np.uint8).astype(np.float32)
        return (pcm - 128.0) / 128.0
    if sample_width == 2:
        return np.frombuffer(raw, dtype="<i2").astype(np.float32) / 32768.0
    if sample_width == 3:
        bytes_ = np.frombuffer(raw, dtype=np.uint8).reshape(-1, 3)
        padded = np.zeros((bytes_.shape[0], 4), dtype=np.uint8)
        padded[:, :3] = bytes_
        sign = bytes_[:, 2] >= 128
        padded[sign, 3] = 255
        return padded.view("<i4").reshape(-1).astype(np.float32) / 8_388_608.0
    if sample_width == 4:
        return np.frombuffer(raw, dtype="<i4").astype(np.float32) / 2_147_483_648.0
    raise AudioDecodeError(f"Unsupported WAV sample width: {sample_width} bytes.")


def _sanitize_samples(samples: np.ndarray) -> np.ndarray:
    samples = np.asarray(samples, dtype=np.float32)
    samples = samples[np.isfinite(samples)]
    if samples.size == 0:
        raise AudioDecodeError("Decoded audio contains no finite samples.")

    peak = float(np.max(np.abs(samples)))
    if peak > 1.0:
        samples = samples / peak
    return np.clip(samples, -1.0, 1.0).astype(np.float32)


def _frame_signal(samples: np.ndarray, frame_size: int, hop_size: int) -> np.ndarray:
    if samples.size < frame_size:
        samples = np.pad(samples, (0, frame_size - samples.size))

    frame_count = 1 + (samples.size - frame_size) // hop_size
    shape = (frame_count, frame_size)
    strides = (samples.strides[0] * hop_size, samples.strides[0])
    return np.lib.stride_tricks.as_strided(samples, shape=shape, strides=strides).copy()


def _coefficient_of_variation(values: np.ndarray) -> float:
    values = np.asarray(values, dtype=np.float32)
    mean = float(np.mean(np.abs(values))) + 1e-12
    return float(np.std(values) / mean)


def _db_ratio(numerator: float, denominator: float) -> float:
    numerator = max(float(numerator), 1e-12)
    denominator = max(float(denominator), 1e-12)
    return float(20.0 * math.log10(numerator / denominator))


def _estimate_periodicity(frames: np.ndarray, sample_rate: int) -> float:
    if frames.size == 0:
        return 0.0

    max_frames = min(frames.shape[0], 80)
    if frames.shape[0] > max_frames:
        indexes = np.linspace(0, frames.shape[0] - 1, max_frames).astype(int)
        frames = frames[indexes]

    min_lag = max(1, int(sample_rate / 420))
    max_lag = min(frames.shape[1] - 1, int(sample_rate / 70))
    if max_lag <= min_lag:
        return 0.0

    scores: list[float] = []
    for frame in frames:
        centered = frame - float(np.mean(frame))
        energy = float(np.dot(centered, centered))
        if energy < 1e-8:
            continue

        corr = np.correlate(centered, centered, mode="full")[frame.size - 1 :]
        corr = corr / (corr[0] + 1e-12)
        scores.append(float(np.max(corr[min_lag:max_lag])))

    if not scores:
        return 0.0
    return float(np.clip(np.mean(scores), 0.0, 1.0))
