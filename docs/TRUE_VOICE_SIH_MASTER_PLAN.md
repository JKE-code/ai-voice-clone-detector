# True Voice: Real-Time On-Device AI Voice Clone & Scam Defense
## Final SIH Master Architecture & Implementation Plan

---

### 1. Executive Summary & Problem Statement

#### The Real-World Crisis
India is witnessing an unprecedented surge in **AI-powered voice cloning scams**. Cybercriminals harvest short audio clips from social media to clone the voices of family members or officials using neural text-to-speech (TTS) engines (ElevenLabs, XTTS, Bark, RVC). Victims receive urgent phone calls claiming a child is arrested, in a medical emergency, or urgently needing an immediate UPI transfer. 

#### Why Existing Solutions Fail
1. **Cloud-Latency Dilemma**: Uploading full audio to cloud servers introduces a 10–30 second delay and violates privacy laws. By the time an alert arrives, the money is already transferred.
2. **Crowdsourcing Blindspot (Truecaller Limitations)**: Truecaller relies on manual, delayed user spam votes. When scammers buy new burner SIMs daily, Truecaller marks them as "Unknown" until hundreds are scammed. Furthermore, Truecaller cannot verify whether a voice on a call is synthetic or human.
3. **App Interference**: Aggressive screen overlays trigger Indian banking anti-tamper SDKs (Google Pay, PhonePe, Paytm, YONO SBI) with the error *"Delete interfering app."*

#### The True Voice Breakthrough
**True Voice** is an edge-native, privacy-preserving telecom cyber defense system built for Android:
- **Pre-Call Threat Intelligence**: Queries the **True Voice Community Intelligence Backend** to display verified AI-scam reports before the call is answered.
- **In-Memory Downlink Audio Tap**: Captures pure remote caller voice (`VOICE_CALL_DOWNLINK`) via a privileged, non-root adapter (Shizuku + scrcpy-server), streaming directly into RAM.
- **On-Device Voice Anti-Spoofing**: Ultra-low-latency neural model (AASIST-Lite / MobileNetV3 ONNX) that detects neural vocoder signatures, spectral cutoffs, and unnatural phase.
- **Trusted Contacts Whitelist**: Automatically bypasses heavy neural processing for trusted contacts to optimize battery and CPU usage, with an optional High-Security mode against Caller ID spoofing.
- **Conversational Coercion Heuristic**: Detects financial pressure, OTP requests, and urgency signals.
- **Non-Intrusive Floating HUD**: Material 3 glassmorphism pill that alerts the user in real time and automatically retracts to protect UPI payments.
- **Collective Immunity**: One-tap anonymized threat reporting updates the backend, protecting the next 10,000 users instantly.

---

### 2. End-to-End System Architecture

```
                                  [ INCOMING CARRIER CALL ]
                                   (Jio / Airtel Telephony)
                                              │
                                              ├─────────────────────────────────────────┐
                                              ▼                                         ▼
                                   [ Pre-Call Reputation Query ]               [ Contacts Whitelist ]
                                    (GET /api/v1/reputation)                  • Trusted: Bypass AI (Battery Saver)
                                              │                               • Untrusted: Full AI Inspection
                                              ▼                                         │
                                [ Pre-Call Threat Badge ]                               │
                                ("Reported 1,420x AI Scam")                             │
                                              │                                         │
                                              └────────────────────┬────────────────────┘
                                                                   ▼
                                                       [ CALL ANSWERED ]
                                                                   │
                                                                   ▼
                                           [ Privileged Capture: Shizuku + scrcpy ]
                                             (Audio Source: VOICE_CALL_DOWNLINK)
                                                                   │
                                                                   ▼
                                             [ Binary Audio Packet Stream: Opus 48kHz ]
                                                                   │
                                    ┌──────────────────────────────┴──────────────────────────────┐
                                    ▼                                                             ▼
                          [ Recording Sink ]                                            [ Live Analysis Sink ]
                       (ScrcpyAudioMuxer -> OGG)                                             (In-Memory RAM)
                                    │                                                             │
                                    ▼                                                             ▼
                         [ CallRecorder/*.ogg ]                                         [ MediaCodec Opus Decoder ]
                      (Archived for post-call audit)                                              │
                                                                                                  ▼
                                                                                        [ 48kHz Stereo PCM ]
                                                                                                  │
                                                                                                  ▼
                                                                                       [ Downmix & Resample ]
                                                                                       (16kHz Mono Float32)
                                                                                                  │
                                                                                                  ▼
                                                                                         [ PcmRingBuffer ]
                                                                                       (Sliding 3.0s Window)
                                                                                                  │
                                                                                                  ▼
                                                                                      [ Silero VAD (ONNX) ]
                                                                                    (Speech vs Silence Gate)
                                                                                                  │
                                                               ┌──────────────────────────────────┴──────────────────────────────────┐
                                                               ▼                                                                     ▼
                                                     [ Voice Authenticity ]                                                [ Conversational Risk ]
                                                     (Anti-Spoofing ONNX)                                                 (Urgency / OTP / Scam NLP)
                                                     • Vocoder Artifacts                                                   • Coercion patterns
                                                     • 8kHz-16kHz Cutoff                                                   • Bank / Police / Hospital
                                                     • Unnatural Pitch Jitter
                                                               │                                                                     │
                                                               └──────────────────────────────────┬──────────────────────────────────┘
                                                                                                  ▼
                                                                                     [ Temporal Risk Engine ]
                                                                                    • Multi-window persistence
                                                                                    • Confidence weighting
                                                                                    • INCONCLUSIVE state guardrail
                                                                                                  │
                                                                                                  ▼
                                                                                     [ In-Call Security HUD ]
                                                                                  (Material 3 Expressive Pill)
                                                                                  • 🟢 Safe | 🟡 Caution | 🔴 Clone Alert
                                                                                  • Auto-dismiss on UPI app launch
                                                                                                  │
                                                                                                  ▼
                                                                                  [ Post-Call Forensic Dossier ]
                                                                                  • Risk timeline waveform graph
                                                                                  • Exportable 1930 Police PDF Report
                                                                                  • Sync report to Threat Intelligence
```

---

### 3. Detailed Software & Technology Stack

| Layer | Component | Technology / Specification | Purpose |
| :--- | :--- | :--- | :--- |
| **Pre-Call Intelligence** | Threat Backend | FastAPI + SQLite / Supabase (REST API) | Instant reputation check (`GET /api/v1/reputation`) and anonymous scam reporting. |
| **Audio Acquisition** | Shizuku Framework | Privileged Shell API (UID 2000) | Non-root access to hardware-level telephony streams. |
| | scrcpy-server v4.0 | Injected JVM Process (`app_process`) | Captures `VOICE_CALL_DOWNLINK` (pure remote caller voice, zero mic bleed). |
| **Audio Processing** | Android MediaCodec | Native `audio/opus` asynchronous decoder | Decodes Opus frames directly to raw 16-bit PCM in memory. |
| | Custom DSP Module | Kotlin / Vectorized Math | Downmixes stereo to mono, resamples 48kHz to 16kHz, normalizes into `FloatArray`. |
| | Circular Buffer | `PcmRingBuffer` (Thread-safe concurrent) | Maintains rolling 3.0-second audio window in RAM; zero temporary disk files. |
| **Machine Learning** | ONNX Runtime Mobile | `com.microsoft.onnxruntime:onnxruntime-android` | High-performance on-device neural inference (CPU / XNNPACK / NNAPI). |
| | Voice Activity Filter | Silero VAD v5 (ONNX, ~2 MB) | Discards silence and pauses to save battery and compute. |
| | Anti-Spoofing Model | AASIST-Lite / MobileNetV3-Audio (ONNX, ~6 MB) | Detects synthetic vocoder signatures, spectral roll-off, and phase inconsistencies. |
| **Security & Logic** | Whitelist Manager | Android Room Database | Manages Trusted Contacts to bypass heavy processing; toggle for High-Security mode. |
| | Risk Engine | Kotlin Deterministic Decision Engine | Weighted risk fusion, multi-window persistence, and explicit `INCONCLUSIVE` state. |
| **User Interface** | Jetpack Compose | Material 3 + Kotlin Coroutines & Flows | Modern mobile dashboard, call history, and forensic detail screens. |
| | Floating In-Call HUD | WindowManager (`TYPE_APPLICATION_OVERLAY`) | Compact floating pill displaying live risk scores with smart UPI auto-retraction. |

---

### 4. Innovation: Trusted Contacts Whitelist & Community Threat Network

#### A. Trusted Contacts Whitelist
Instead of requiring users to record family voices into a biometric vault, True Voice uses an intelligent **Trusted Whitelist**:
- **Battery Saver Mode (Default)**: Calls from contacts in the whitelist immediately show 🟢 **"Verified Contact"** and bypass the heavy ML inference pipeline, conserving battery and CPU.
- **High-Security Mode**: For high-risk users (e.g., senior citizens), the app still scans whitelisted incoming numbers to protect against **Caller ID Spoofing** and **SIM Swap** attacks.

#### B. True Voice Community Threat Intelligence Network
Truecaller only knows what users manually flag. True Voice creates **The First AI-Verified Telecom Defense Grid**:
- **Pre-Call Defense**: When a call rings, the app queries `GET /api/v1/reputation?phone=+91XXXXXXXXXX` and displays a threat badge if the number has a history of synthetic voice attacks.
- **Day-1 Protection for New Numbers**: If a scammer uses a fresh SIM, the on-device AI detects the synthetic voice in real time during the call.
- **Community Immunity (`POST /api/v1/report`)**: Once a deepfake is detected, the app anonymously hashes the number and uploads the report with its forensic confidence score. Every other True Voice user is instantly protected against that number before answering.

---

### 5. Solving the UPI / Banking App Interference (Google Pay, PhonePe)

#### The Problem:
Indian UPI apps detect `SYSTEM_ALERT_WINDOW` overlays (to prevent tapjacking) and active ADB/developer options, displaying the error *"Delete interfering app."*

#### True Voice Safeguards:
1. **Smart Overlay Retraction**: The floating HUD monitors window focus and foreground app packages. The moment the user leaves the call or opens any financial/UPI app, the overlay **instantly dismisses itself** or collapses into a non-intrusive Android Notification.
2. **Zero Touch-Obscuration**: Window flags are set to `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE` with minimal pixel footprint, ensuring Android's `FLAG_WINDOW_IS_OBSCURED` is never triggered.
3. **Clean Package Identity**: The production package name is formatted as `com.truevoice.security` (telecom security provider), avoiding suspicious naming.
4. **Shizuku Standby**: ADB shell communication is dormant when no call is active.

---

### 6. The 3-Minute SIH Winning Demo Script

```
0:00 — 0:30 | SETUP & ARCHITECTURE
• Open True Voice on the Android phone.
• Highlight the clean Material 3 Dark-Glass dashboard showing: "True Voice Shield: ACTIVE".
• Explain the core innovation: 100% On-Device, In-Memory DSP, zero cloud audio upload, complete privacy.

0:30 — 1:00 | CALL 1: TRUSTED CONTACT CALL (BATTERY SAVER DEMO)
• An incoming carrier call arrives from a whitelisted contact ("Mom").
• True Voice instantly displays: "🟢 Verified Contact • Analysis Bypassed (Battery Saver)".
• Demonstrates zero lag, zero battery waste for daily normal calls.

1:00 — 1:40 | CALL 2: AI VOICE CLONE ATTACK (THE HERO DEMO)
• Incoming call from an unknown number.
• Pre-call Threat Alert flashes: "🚨 Community Alert: Number reported 1,420 times for AI Extortion".
• Call is answered (using an ElevenLabs/XTTS voice clone of a relative claiming an emergency).
• Pure incoming audio (VOICE_CALL_DOWNLINK) is streamed into RAM.
• At 1:05 (within 2–3 seconds): Floating HUD pulses Amber 🟡 ("Analyzing synthetic markers...").
• At 1:12 (after persistent evidence across windows): HUD expands to High-Risk Red 🔴:
  "⚠️ CRITICAL ALERT: AI Cloned Voice Detected (91% Synthetic Score)"
• Explain the forensic triggers: Vocoder frequency cutoff at 8kHz, lack of micro-jitter, phase discontinuity.

1:40 — 2:15 | CALL 3: HUMAN SCAMMER CALL (NO FALSE POSITIVES)
• A real human scammer calls demanding an urgent OTP / bank transfer.
• Voice Authenticity confirms: "🟢 Human Voice (100%)" (No false deepfake flag!).
• Conversational Risk identifies urgency and financial coercion keywords.
• Combined Risk Engine elevates status to: "🟠 HIGH RISK: Financial Coercion Detected".
• Proves the system handles both synthetic clones and human social engineering.

2:15 — 2:35 | POST-CALL FORENSIC VAULT & COMMUNITY SYNC
• Call ends. User taps to view the Forensic Dossier:
  - Interactive timeline showing second-by-second synthetic risk graph.
  - "Submit to Community Threat Grid" button automatically updates the global database.
  - "Export Law Enforcement Report" generates a pre-filled PDF for the 1930 Cybercrime Portal.

2:35 — 3:00 | THE GOOGLE PAY TEST & CONCLUSION
• Live on stage: Open Google Pay or PhonePe on the exact same phone.
• The payment app launches instantly with ZERO "interfering app" or "overlay detected" warnings.
• Wrap-up: "True Voice delivers enterprise-grade edge AI defense without breaking everyday usability."
```

---

### 7. Step-by-Step Implementation Roadmap for the Codebase

```
[Phase 1] Core In-Memory Audio Pipeline (Milestone 1)
├── Configure ScrcpyAudioSource to VOICE_CALL_DOWNLINK (Pure caller audio)
├── Create com.truevoice.capture.LiveAnalysisSink interface
├── Implement OpusPcmDecoder using Android MediaCodec
├── Build AudioResampler (48kHz stereo to 16kHz mono FloatArray)
└── Implement thread-safe PcmRingBuffer (holding 3.0s in RAM)

[Phase 2] Edge AI Inference Engines (Milestone 2)
├── Integrate ONNX Runtime Android dependency
├── Add Silero VAD ONNX model for silence filtering
├── Add AASIST-Lite / MobileNetV3 Anti-Spoofing ONNX model
└── Implement Whitelist Manager (Room Database) for trusted contact bypass

[Phase 3] Risk Engine & UI/UX (Milestone 3)
├── Build TemporalRiskEngine (weighted scoring + INCONCLUSIVE guardrail)
├── Develop Floating Security HUD using Jetpack Compose & WindowManager
├── Implement automatic overlay retraction on UPI app launch
└── Build Call History & Forensic Timeline screen with exportable report

[Phase 4] Threat Intelligence Backend (Milestone 4)
├── Implement lightweight FastAPI server (GET /api/v1/reputation, POST /api/v1/report)
└── Integrate pre-call reputation query and post-call sync in Android client
```

---

### 8. Production Scalability & Long-Term Roadmap

1. **Direct APK Onboarding**:
   - Built-in interactive guide for modern Android 11+ Wireless Debugging pairing (100% on-phone, zero PC required).
2. **Telecom Operator Partnership (Jio / Airtel)**:
   - Integration as an opt-in cyber-shield module within *MyJio* and *Airtel Thanks* apps.
3. **OEM System Integration**:
   - Partnering with smartphone manufacturers (Samsung Knox, OnePlus OxygenOS) to integrate True Voice directly into the native Phone/Dialer app.
4. **Regulatory & Privacy Alignment**:
   - Compliant with India's **Digital Personal Data Protection (DPDP) Act 2023**: Call audio is processed in volatile memory (RAM) and never transmitted off the user's personal device.
