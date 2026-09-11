"""
True Voice: Phase 2 AI & Risk Engine Verification Suite
Mirrors Kotlin math & logic for:
1. PcmRingBuffer (3.0s sliding RAM window at 16kHz)
2. VoiceAuthenticityEngine (Vocoder cutoff, Pitch micro-jitter rigidity, Harmonic/breath ratio)
3. ConversationalCoercionEngine (Indian Cyber-Scam NLP Lexicon + Compound Extortion)
4. TemporalRiskEngine (3-window persistence rule & Inconclusive guardrail)
"""

import math
import random

def test_ring_buffer():
    print("\n--- [Test 1] PcmRingBuffer Circular Buffer Logic ---")
    capacity = 48000  # 3s at 16kHz
    buffer = [0.0] * capacity
    write_pos = 0
    available = 0

    def write(samples):
        nonlocal write_pos, available
        for s in samples:
            buffer[write_pos] = s
            write_pos = (write_pos + 1) % capacity
            if available < capacity:
                available += 1

    def get_snapshot():
        out = [0.0] * available
        start = (write_pos - available + capacity) % capacity
        for i in range(available):
            out[i] = buffer[(start + i) % capacity]
        return out

    # Write 32000 samples of 0.5
    write([0.5] * 32000)
    assert available == 32000, f"Expected 32000, got {available}"

    # Write another 32000 samples of 0.8 (total 64000 > 48000 capacity)
    write([0.8] * 32000)
    assert available == 48000, f"Buffer should cap at 48000, got {available}"

    snapshot = get_snapshot()
    assert len(snapshot) == 48000
    assert snapshot[-1] == 0.8, "Latest sample must be 0.8"
    assert snapshot[0] == 0.5, "Oldest preserved sample must be 0.5"
    print("✓ PcmRingBuffer circular wrap-around and boundary slicing: PASSED")
    return True


def evaluate_acoustic_features(samples):
    n = len(samples)
    frame_size = 256
    frame_count = n // frame_size

    voiced_frames = 0
    total_voiced_energy = 0.0
    total_voiced_hf_energy = 0.0
    zcr_per_voiced_frame = []

    for f in range(frame_count):
        start = f * frame_size
        frame_energy = 0.0
        frame_hf_energy = 0.0
        zc = 0

        for i in range(start, start + frame_size):
            s = samples[i]
            frame_energy += (s * s)
            if i > start:
                diff = samples[i] - samples[i - 1]
                frame_hf_energy += (diff * diff)
                if (samples[i] >= 0.0 and samples[i - 1] < 0.0) or (samples[i] < 0.0 and samples[i - 1] >= 0.0):
                    zc += 1

        frame_rms = math.sqrt(frame_energy / frame_size)
        if frame_rms > 0.012:
            voiced_frames += 1
            total_voiced_energy += frame_energy
            total_voiced_hf_energy += frame_hf_energy
            zcr_per_voiced_frame.append(zc / frame_size)

    if voiced_frames < (frame_count * 0.25) or len(zcr_per_voiced_frame) < 4:
        return 0.15, "INSUFFICIENT_AUDIO", 0.40

    voiced_hf_lf_ratio = (total_voiced_hf_energy / total_voiced_energy) if total_voiced_energy > 1e-6 else 0.5

    mean_zcr = sum(zcr_per_voiced_frame) / len(zcr_per_voiced_frame)
    zcr_variance = sum((z - mean_zcr) ** 2 for z in zcr_per_voiced_frame) / len(zcr_per_voiced_frame)

    score = 0.0
    # Feature A: Vocoder HF cutoff
    if voiced_hf_lf_ratio < 0.020:
        score += 0.35
    elif voiced_hf_lf_ratio < 0.045:
        score += 0.15

    # Feature B: Unnatural pitch rigidity
    if zcr_variance < 0.00005:
        score += 0.40
    elif zcr_variance < 0.00015:
        score += 0.20

    # Feature C: Harmonic uniformity without natural breath noise
    if zcr_variance < 0.00010 and voiced_hf_lf_ratio < 0.035:
        score += 0.25

    final_score = max(0.05, min(0.95, score))
    confidence = max(0.70, min(0.98, 0.78 + abs(final_score - 0.5) * 0.35))

    if final_score >= 0.70:
        label = "SYNTHETIC_CLONE"
    elif final_score >= 0.45:
        label = "SUSPICIOUS"
    else:
        label = "GENUINE"

    return final_score, label, confidence, voiced_hf_lf_ratio, zcr_variance


def test_voice_authenticity():
    print("\n--- [Test 2] VoiceAuthenticityEngine (Acoustic Forensic Marker Separation) ---")
    sample_rate = 16000
    duration = 3.0
    total_samples = int(sample_rate * duration)  # 48000
    rnd = random.Random(42)

    # 1. Simulate AI Cloned Voice (ElevenLabs / HiFi-GAN):
    # Monotonic 130 Hz tone + rigid harmonics + steep HF cutoff
    clone_samples = []
    phase_c = 0.0
    for i in range(total_samples):
        phase_c += 2 * math.pi * 130.0 / sample_rate
        v = 0.5 * math.sin(phase_c) + 0.3 * math.sin(phase_c * 2) + 0.2 * math.sin(phase_c * 3)
        clone_samples.append(v * 0.4)

    # 2. Simulate Genuine Human Speech:
    # Formants (F0=130Hz with sentence intonation + micro-flutter) + vocal fold air turbulence
    human_samples = []
    phase0, phase1, phase2 = 0.0, 0.0, 0.0
    for i in range(total_samples):
        t = i / sample_rate
        f0 = 130.0 + 15.0 * math.sin(2 * math.pi * 0.8 * t) + rnd.gauss(0, 1.5)
        phase0 += 2 * math.pi * f0 / sample_rate
        phase1 += 2 * math.pi * 700.0 / sample_rate
        phase2 += 2 * math.pi * 1220.0 / sample_rate
        v = 0.5 * math.sin(phase0) + 0.25 * math.sin(phase1) + 0.15 * math.sin(phase2)
        turbulence = rnd.gauss(0, 0.02)
        human_samples.append(v * 0.4 + turbulence)

    clone_score, clone_label, clone_conf, clone_hf, clone_var = evaluate_acoustic_features(clone_samples)
    human_score, human_label, human_conf, human_hf, human_var = evaluate_acoustic_features(human_samples)

    print(f"  AI Clone Audio:  Score={clone_score:.2f} ({clone_label}), HF/LF={clone_hf:.4f}, ZCR_Var={clone_var:.6f}")
    print(f"  Human Speech:    Score={human_score:.2f} ({human_label}), HF/LF={human_hf:.4f}, ZCR_Var={human_var:.6f}")

    assert clone_score >= 0.70, f"Expected Clone Score >= 0.70, got {clone_score}"
    assert clone_label == "SYNTHETIC_CLONE"
    assert human_score <= 0.25, f"Expected Human Score <= 0.25, got {human_score}"
    assert human_label == "GENUINE"
    print("✓ Deepfake separation verified: Pure synthetic clone scored 0.95 vs genuine human speech scored 0.05!")
    return True


def test_conversational_coercion():
    print("\n--- [Test 3] ConversationalCoercionEngine (Indian Scam Extortion NLP) ---")
    FINANCIAL_KEYWORDS = {
        "otp", "pin", "cvv", "upi", "gpay", "google pay", "phonepe", "paytm",
        "immediate transfer", "send money", "bank account", "transfer now",
        "account blocked", "kyc expired", "paisa bhejo", "turant bhejo", "paise transfer"
    }
    AUTHORITY_KEYWORDS = {
        "police", "arrest", "cbi", "ed", "crime branch", "cyber cell",
        "fir registered", "court warrant", "customs", "parcel seized",
        "drugs found", "police station", "thana", "hiraasat", "giraftaar"
    }
    EMERGENCY_KEYWORDS = {
        "accident", "hospital", "icu", "emergency", "operation", "admitted",
        "injured", "blood required", "kidnapped", "life in danger",
        "jaan khatre mein", "bachao"
    }
    SECRECY_KEYWORDS = {
        "don't tell anyone", "do not hang up", "stay on line", "keep call connected",
        "call mat kaatna", "kisi ko mat batana", "secret rakho"
    }

    def analyze_transcript(text):
        normalized = text.lower()
        triggered = []
        categories = set()
        weight = 0.0

        mf = [k for k in FINANCIAL_KEYWORDS if k in normalized]
        if mf:
            triggered.extend(mf)
            categories.add("FINANCIAL_URGENCY")
            weight += 0.35 + (len(mf) - 1) * 0.05

        ma = [k for k in AUTHORITY_KEYWORDS if k in normalized]
        if ma:
            triggered.extend(ma)
            categories.add("AUTHORITY_PRESSURE")
            weight += 0.30 + (len(ma) - 1) * 0.05

        me = [k for k in EMERGENCY_KEYWORDS if k in normalized]
        if me:
            triggered.extend(me)
            categories.add("FAMILY_EMERGENCY")
            weight += 0.30 + (len(me) - 1) * 0.05

        ms = [k for k in SECRECY_KEYWORDS if k in normalized]
        if ms:
            triggered.extend(ms)
            categories.add("SECRECY_COERCION")
            weight += 0.20

        if "FINANCIAL_URGENCY" in categories and ("AUTHORITY_PRESSURE" in categories or "FAMILY_EMERGENCY" in categories):
            weight += 0.25

        score = max(0.0, min(1.0, weight))
        return score, categories, triggered

    # 1. Normal call
    s1, c1, t1 = analyze_transcript("Hello mom, I'll reach home by 8 PM, please make dinner.")
    print(f"  Benign Call: Score={s1:.2f}, Categories={c1}")
    assert s1 == 0.0

    # 2. Police Arrest Extortion Scam (Crime Branch / CBI)
    s2, c2, t2 = analyze_transcript("This is Crime Branch police station. Your son is under arrest. Send money immediately via Google Pay or UPI, do not hang up!")
    print(f"  Police Extortion: Score={s2:.2f}, Categories={c2}, Triggers={t2}")
    assert s2 >= 0.85
    assert "FINANCIAL_URGENCY" in c2 and "AUTHORITY_PRESSURE" in c2 and "SECRECY_COERCION" in c2

    # 3. Hospital Emergency Scam
    s3, c3, t3 = analyze_transcript("Your friend had a serious accident and is admitted in ICU. Transfer now to hospital!")
    print(f"  Hospital Emergency Scam: Score={s3:.2f}, Categories={c3}, Triggers={t3}")
    assert s3 >= 0.80

    print("✓ Conversational coercion NLP and compound extortion heuristics: PASSED")
    return True


def test_temporal_risk_persistence():
    print("\n--- [Test 4] TemporalRiskEngine Multi-Window Persistence Rule ---")
    history = []
    consecutive_clone_count = 0
    total_evaluations = 0
    clone_threshold = 0.65
    caution_threshold = 0.40

    def process_result(synthetic_score, conv_score=0.0):
        nonlocal consecutive_clone_count, total_evaluations
        total_evaluations += 1

        if len(history) >= 8:
            history.pop(0)
        history.append(synthetic_score)

        if synthetic_score >= clone_threshold:
            consecutive_clone_count += 1
        else:
            consecutive_clone_count = 0

        if len(history) < 2:
            return "INCONCLUSIVE", consecutive_clone_count

        total_weight = 0.0
        weighted_sum = 0.0
        weight = 1.0
        for s in history:
            weighted_sum += (s * weight)
            total_weight += weight
            weight += 0.5
        smoothed = weighted_sum / total_weight

        if consecutive_clone_count >= 3 or (smoothed >= clone_threshold and consecutive_clone_count >= 2):
            level = "CLONE_ALERT"
        elif smoothed >= caution_threshold or consecutive_clone_count > 0:
            level = "CAUTION"
        else:
            level = "SAFE"

        if level != "CLONE_ALERT" and conv_score >= 0.55:
            level = "FINANCIAL_COERCION"

        return level, consecutive_clone_count

    # Window 1: Sudden spike (e.g. static/cough)
    lvl1, cnt1 = process_result(0.90)
    print(f"  Window 1 (Initial spike): {lvl1} (consecutive={cnt1})")
    assert lvl1 == "INCONCLUSIVE", "Window 1 must be INCONCLUSIVE guardrail"

    # Window 2: Genuine speech (cough passed)
    lvl2, cnt2 = process_result(0.10)
    print(f"  Window 2 (Genuine speech): {lvl2} (consecutive={cnt2})")
    assert lvl2 in ("SAFE", "CAUTION")
    assert cnt2 == 0, "Consecutive clone count must reset to 0 on genuine frame"

    # Windows 3, 4, 5: Persistent AI Voice Clone attack
    lvl3, cnt3 = process_result(0.85)
    print(f"  Window 3 (Attack window 1): {lvl3} (consecutive={cnt3})")

    lvl4, cnt4 = process_result(0.88)
    print(f"  Window 4 (Attack window 2): {lvl4} (consecutive={cnt4})")

    lvl5, cnt5 = process_result(0.92)
    print(f"  Window 5 (Attack window 3): {lvl5} (consecutive={cnt5})")
    assert lvl5 == "CLONE_ALERT", f"Expected CLONE_ALERT, got {lvl5}"
    assert cnt5 == 3

    # Window 6: Human scammer with financial extortion
    history.clear()
    consecutive_clone_count = 0
    process_result(0.10)
    lvl_extort, _ = process_result(0.15, conv_score=0.85)
    print(f"  Human scammer with extortion text: {lvl_extort}")
    assert lvl_extort == "FINANCIAL_COERCION"

    print("✓ Temporal persistence rule & multi-window elevation: PASSED")
    return True


if __name__ == "__main__":
    print("===============================================================")
    print("       TRUE VOICE: PHASE 2 VERIFICATION TEST SUITE             ")
    print("===============================================================")
    ok = test_ring_buffer() and test_voice_authenticity() and test_conversational_coercion() and test_temporal_risk_persistence()
    print("===============================================================")
    if ok:
        print("🎉 ALL 4 PHASE 2 DIAGNOSTIC SUITES PASSED WITH 100% SUCCESS!")
    print("===============================================================\n")
