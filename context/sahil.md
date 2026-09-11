# Sahil's Context & Decision Log
## Smart India Hackathon (SIH 2026) — Problem Statement #26104
### Project: True Voice (AI-Powered Real-Time Voice Clone Impersonation Detection)

> **Contributor:** Sahil  
> **Purpose:** Shared team & AI assistant context document. Explains all architectural decisions, thought processes, changes made, codebase structure, and ongoing direction so teammates (and their AI coding agents) can collaborate with 100% alignment.

---

## 1. Problem Statement & Strategic Direction

### Problem Context (SIH #26104)
AI neural speech synthesis tools (ElevenLabs, XTTS, Bark, RVC) allow scammers to clone anyone's voice from a 3-second social media clip. Cybercriminals in India use these to impersonate family members, executives, or police officers, demanding urgent UPI payments.

### Why We Decided to Pivot / Adapt `ShizuCallRecorder`
Instead of writing an Android telephony audio capture engine from scratch (which would take weeks and hit strict Android 14/15 privacy sandbox restrictions), **I researched and selected [ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder) as our core foundation**.

**Key reasons for this decision:**
1. **Zero Root Required**: Uses **Shizuku** (UID 2000 ADB shell privilege) to invoke privileged Android APIs.
2. **Pure Remote Downlink Capture**: Integrates `scrcpy-server v4.0` in `app_process` to tap `VOICE_CALL_DOWNLINK`. This isolates the caller's pure voice with **zero microphone acoustic bleed** (caller voice isn't mixed with our own mic).
3. **Rock-Solid Modern Stack**: 100% Kotlin, Jetpack Compose, Material 3, Coroutines/Flows, Room DB, Foreground Services.

---

## 2. Thought Process & Architectural Decisions

### Decision 1: Dual-Sink Audio Architecture (In-Memory Live Stream + File Archive)
- **Problem**: We need real-time AI inference during the live call, but we also need an archived recording for the **1930 Cyber Cell Police Report / Forensic Dossier**.
- **Decision**: In `AudioRecordingEngine.kt`, split the binary Opus stream arriving from `scrcpy-server`:
  - **Sink A (File Storage)**: Feeds `ScrcpyAudioMuxer` to write encrypted `.ogg` files.
  - **Sink B (Live Memory Pipeline)**: Feeds an in-memory `OpusLiveDecoder` (Android `MediaCodec` -> raw PCM -> `PcmRingBuffer`).
- **Why**: Zero disk read-write overhead for AI inference; avoids battery drain and disk I/O bottlenecks during live calls.

### Decision 2: 100% Isolated Standalone Utility (Air-Gapped / Like a Torch App)
- **Problem**: Should the app depend on cloud servers, external APIs, or background updates?
- **Decision**: **True Voice is designed as a 100% self-contained, isolated edge utility** (identical to a phone's built-in Flashlight/Torch or Calculator app). Once downloaded and installed, it operates forever completely offline with zero external network dependencies and no mandatory updates.
- **Why**:
  - **Zero Network Latency**: Real-time on-device inference runs locally in <10ms via ONNX Mobile.
  - **Total Privacy & Air-Gapped Security**: Zero call data, transcripts, or telemetry ever leave the device (100% DPDP Act compliance).
  - **Reliability Anywhere**: Works seamlessly in rural areas, flights, basements, and during cellular data outages.
  - **Zero Server Infrastructure Costs**: Zero recurring server hosting expenses.

### Decision 3: Two-Stage Model Pipeline (Silero VAD + Anti-Spoofing Classifier)
- **Stage 1 (Silero VAD)**: Discards silence, background noise, and pauses (~2MB ONNX model).
- **Stage 2 (AASIST-Lite / MobileNetV3-Audio)**: Analyzes vocoder artifacts, 8kHz–16kHz frequency cutoffs, unnatural phase transitions, and pitch jitter (~1.16MB INT8 ONNX model).
- **Why**: Saves battery by only running the heavy neural model when actual human speech is detected.

### Decision 4: Non-Intrusive Floating HUD with Auto-Dismiss on UPI Apps
- **Problem**: Full-screen Android overlays trigger anti-tamper security in Indian banking apps (Google Pay, PhonePe, Paytm, BHIM, YONO SBI) with the error *"Delete interfering app / Overlay detected"*.
- **Decision**:
  - Implement a lightweight, draggable Material 3 glassmorphism pill (`TrueVoiceHudService`).
  - Use an Accessibility / Foreground App monitor to **instantly retract the floating HUD into the system notification tray** whenever a banking/UPI app opens.
- **Why**: Protects the user without blocking their financial applications.

### Decision 5: On-Device Multilingual Indic Intent & Whitelist
- **Local Indic Intent Engine**: Native on-device regex & pattern matrix evaluating Hindi, Telugu, Tamil, Kannada, Marathi, Bengali, and Indian English transcripts locally with zero cloud ASR.
- **Trusted Contacts Whitelist**: Local Room database storing trusted contacts to optionally bypass heavy AI processing and maximize battery life.


---

## 3. Codebase Structure & File Responsibilities

```
app/src/main/java/com/kitsumed/shizucallrecorder/
├── integrations/scrcpy/
│   ├── ScrcpyClient.kt          # Manages scrcpy-server JVM process via Shizuku shell
│   ├── ScrcpyAudioSource.kt      # Configures VOICE_CALL_DOWNLINK audio source
│   ├── ScrcpyAudioMuxer.kt       # Muxes Opus stream into OGG container for storage
│   └── ScrcpyConfig.kt           # Scrcpy process parameters
├── services/
│   ├── recording/
│   │   ├── AudioRecordingEngine.kt    # Main binary stream reader & sink dispatcher
│   │   ├── RecordingForegroundService.kt # Foreground lifecycle, telephony receivers
│   │   └── RecorderStateManager.kt    # Reactive recording state machine
│   └── shell/
│       └── ShellAudioPipeline.kt      # Shizuku rootless shell execution bridge
├── data/
│   ├── call/                          # EnrichedCallData, RawCallData, CallDirection
│   ├── database/                      # Room database, DAOs, recording entities
│   └── settings/                      # DataStore preferences & settings
└── ui/                                # Jetpack Compose screens, player, themes
```

---

## 4. Current Status & What Has Been Done

- [x] **Full Codebase Audit & Mapping**: Examined all Kotlin files, Gradle build scripts, AndroidManifest permissions, and Shizuku/scrcpy bindings.
- [x] **SIH Master Plan Formulated**: Full architectural design saved in `docs/TRUE_VOICE_SIH_MASTER_PLAN.md`.
- [x] **Problem Statement Integration**: Formal problem specification mapped in `docs/problem_statement.json`.
- [x] **Context Synchronization Setup**: Master team & personal context logs in `context/`.
- [x] **Python ML Model Export & Quantization Pipeline**: Built `ml_pipeline/export_onnx.py` (`VoiceCloneDetectorNet` with Sinc/Conv front-end, SE-ResNet dilated blocks, Attentive Stats Pooling, INT8 quantization) and `ml_pipeline/benchmark.py`.
- [x] **Android Edge ML & DSP Architecture (Kotlin)**:
  - `ml/dsp/AudioResampler.kt`: 48kHz stereo to 16kHz mono $[-1.0 .. 1.0]$ float decimation with anti-aliasing.
  - `ml/dsp/PcmRingBuffer.kt`: Sliding 3.0s window buffer with 0.5s hop.
  - `ml/SileroVadDetector.kt`: VAD wrapper for Silero ONNX.
  - `ml/VoiceCloneClassifier.kt`: On-device anti-spoofing classifier.
  - `ml/TemporalRiskEngine.kt`: Multi-window exponential smoothing and confidence guardrails.
  - `ml/VoiceCloneDetectionCoordinator.kt`: Orchestrates live audio tap, DSP, ML inference, and emits `StateFlow<VoiceRiskAssessment>`.
- [x] **Multilingual Indic Intent & Coercion Engine (SIH Nationwide Support)**:
  - Built `ml_pipeline/indic_intent_detector.py` and `app/src/main/java/com/kitsumed/shizucallrecorder/ml/IndicIntentEngine.kt`.
  - Full native & transliterated support for **Hindi, Telugu, Tamil, Kannada, Marathi, Bengali, and Indian English** across 4 fraud buckets (Financial Demands, Authority Impersonation, Emotional Extortion, Urgency Pressure).
  - Dual-track hybrid fusion in `TemporalRiskEngine.kt` (65% Acoustic Voice Physics + 35% Indic Intent Coercion).
- [x] **Python Virtual Environment (`.venv`)**: Initialized at workspace root with full ML dependency pipeline.
- [x] **Unit Testing Suite**: Created `AudioResamplerTest.kt`, `PcmRingBufferTest.kt`, `TemporalRiskEngineTest.kt`, and `IndicIntentEngineTest.kt`.



---

## 5. Teammate & AI Assistant Handoff Guide

If you or your AI agent are implementing a module:

| If you are working on... | Read these files first | What to build / follow |
| :--- | :--- | :--- |
| **Audio Decoder & Live DSP** | `AudioRecordingEngine.kt`, `ScrcpyAudioMuxer.kt` | Build `OpusLiveDecoder.kt` and `PcmRingBuffer.kt` to extract 16kHz mono `FloatArray` from the live stream. |
| **Edge ML / ONNX Models** | `build.gradle.kts`, `docs/TRUE_VOICE_SIH_MASTER_PLAN.md` | Add ONNX Runtime dependency, load `silero_vad.onnx` and `aasist_lite.onnx` from assets, implement `VoiceCloneClassifier.kt`. |
| **Floating HUD & UI** | `ui/`, `RecordingForegroundService.kt` | Build `TrueVoiceHudService.kt` overlay, Material 3 alert states (🟢/🟡/🔴), and UPI app auto-retract logic. |
| **Threat Intel Backend** | `docs/problem_statement.json` | Build FastAPI backend (`GET /api/v1/reputation`, `POST /api/v1/report-scam`) with SQLite/Postgres. |

> **Note to Teammates:** Please create your own `context/{your_name}.md` in the `context/` folder to document your module decisions and progress so our whole team stays perfectly coordinated!
