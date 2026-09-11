# True Voice — SIH Team & AI Context Guide
## Project: Real-Time On-Device AI Voice Clone & Scam Defense (Smart India Hackathon)

> **Purpose of this document**:  
> This file is the single source of truth for all team members and their AI coding assistants. It documents the problem statement, system architecture, architectural decisions and thought process, codebase modifications made so far, and the roadmap for ongoing development.

---

### 1. Executive Context & Mission

* **The Problem**: Deepfake audio and AI voice cloning scams (ElevenLabs, XTTS, Bark, RVC) are being used for emergency kidnapping extortion, fake police arrests, and urgent UPI fund transfers in India.
* **The Project**: **True Voice** transforms the phone into an edge-native, privacy-preserving cyber defense shield that detects synthetic voices in real time during carrier calls, flags coercion patterns, provides post-call forensic reports, and protects everyday UPI banking apps from interference.

---

### 2. Architectural Decisions & Thought Process

#### A. Why We Forked ShizuCallRecorder (Capture Adapter)
* Capturing two-sided carrier calls on Android 12+ without root is virtually impossible using public APIs.
* `ShizuCallRecorder` achieves this via **Shizuku (privileged shell UID 2000)** and **scrcpy-server v4.0** running inside `app_process`.
* **Decision**: We keep this proven capture layer as our low-level adapter, but decouple the application identity into **True Voice**.

#### B. The Dual-Sink Architecture (No 3-Second Audio Files!)
* **Initial/Naive Idea**: Record calls to disk, slice them into 3-second `.ogg` files, and feed them to an AI.
* **Why that was rejected**: Heavy disk I/O, file fragmentation, flash memory wear, and high latency (defeats real-time defense).
* **The Solution (Dual Sink)**:
  * **Sink 1 (Storage)**: `ScrcpyAudioMuxer` writes compressed Opus packets directly into `.ogg` in the user's `CallRecorder/` folder for call history and legal evidence.
  * **Sink 2 (Live Analysis)**: An in-memory fan-out intercepts raw `AudioPacket` frames every 20ms, decodes them via Android's hardware `MediaCodec` into PCM, downmixes to 16kHz mono, and pushes into a circular RAM buffer (`PcmRingBuffer`). **Zero disk I/O for AI.**

#### C. Isolating Pure Caller Audio (`VOICE_CALL_DOWNLINK`)
* Standard `VOICE_CALL` captures both the local mic (victim) and the remote caller (scammer) mixed together.
* If the victim speaks, coughs, or talks during the call, cross-talk corrupts acoustic feature extraction and triggers false alarms.
* **Decision**: We configure the capture engine to use `VOICE_CALL_DOWNLINK`. The AI receives 100% pure remote caller voice, eliminating victim mic bleed.

#### D. Trusted Contacts Whitelist vs. Voice Biometrics
* We initially evaluated enrolling family voiceprints into a biometric vault.
* **Why we simplified**: Requiring users to record 10-second voice clips of relatives is tedious and creates user friction.
* **The Solution**: An intelligent **Trusted Contacts Whitelist**:
  * *Battery Saver Mode (Default)*: Whitelisted contacts immediately show 🟢 *"Verified Contact • Analysis Bypassed"*, saving CPU and battery.
  * *High-Security Mode*: Optional continuous scanning even for whitelisted numbers to guard against Caller ID spoofing / SIM swap attacks.

#### E. Community Threat Intelligence Grid
* Truecaller only knows what users manually flag days later. Scammers discard burner SIMs daily.
* **The Solution**: A lightweight backend (`GET /api/v1/reputation`, `POST /api/v1/report`). When Victim 1's phone mathematically detects a 91% synthetic clone, the anonymized number hash is reported to the community database. Victims 2 through 10,000 receive a pre-call warning before answering!

#### F. Solving UPI / Banking App Interference
* Indian banking apps (GPay, PhonePe, Paytm, YONO SBI) throw *"Delete interfering app"* when active overlays or ADB debugging are detected.
* **The Solution**:
  * The floating HUD sets `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE` with zero-touch obscuration.
  * The overlay automatically retracts/hides the instant the user leaves the call screen or opens a financial app.

---

### 3. Changes Made to Date (Codebase Changelog)

#### New Package Created: `com.truevoice.audio`
1. **`AudioTelemetry.kt`**:
   - Tracks real-time pipeline diagnostics (packets/sec, PCM samples decoded, buffer fill duration, RMS audio energy, and error states).
2. **`PcmRingBuffer.kt`**:
   - Thread-safe circular buffer holding a sliding 3.0-second window (48,000 float samples at 16kHz). Zero temporary file allocations.
   - Provides `write(samples)` and `getLatestWindow(seconds)`.
3. **`AudioResampler.kt`**:
   - Fast 3:1 integer decimation with 3-tap anti-aliasing filter to convert 48kHz stereo 16-bit PCM into 16kHz mono `FloatArray` (range [-1.0, 1.0]).
   - Computes real-time RMS energy to detect voice presence vs silence.
4. **`OpusPcmDecoder.kt`**:
   - Uses Android's native `MediaCodec` asynchronously to decode raw Opus (and AAC) packets directly into 16-bit PCM in RAM.
   - Generates RFC 7845 compliant 19-byte `OpusHead` CSD buffers if scrcpy sends raw frames without headers.
5. **`LiveAnalysisSink.kt`**:
   - Coordinates Sink 2. Receives `ScrcpyClient.AudioPacket` frames, feeds the decoder, resamples to 16kHz mono, updates telemetry, and populates `PcmRingBuffer`.

#### Existing Files Modified:
1. **`AudioRecordingEngine.kt`**:
   - Added `liveAnalysisSink = LiveAnalysisSink()`.
   - Updated `onMetadataReceived()` to initialize the in-memory decoder with confirmed stream properties (48kHz stereo).
   - Updated `onAudioPacket()` to dual-route packets:
     - To `scrcpyAudioMuxer?.writePacket(...)` (Sink 1: Storage).
     - To `liveAnalysisSink.enqueuePacket(...)` (Sink 2: In-Memory Analysis).
   - Updated `release()` and `onStreamEnd()` to cleanly stop and flush `liveAnalysisSink`.

---

### 4. Codebase Navigation & Key Files

```
d:\AI-Voice-Clone-Detector\
├── app\src\main\java\
│   ├── com\truevoice\audio\                 <-- TRUE VOICE IN-MEMORY AUDIO PIPELINE
│   │   ├── AudioTelemetry.kt                (Health monitor & live metrics)
│   │   ├── PcmRingBuffer.kt                 (3-sec sliding window in RAM)
│   │   ├── AudioResampler.kt                (48kHz stereo -> 16kHz mono FloatArray)
│   │   ├── OpusPcmDecoder.kt                (Android MediaCodec in-memory decoder)
│   │   └── LiveAnalysisSink.kt              (Sink 2 coordinator)
│   │
│   ├── com\kitsumed\shizucallrecorder\
│   │   ├── services\recording\
│   │   │   ├── AudioRecordingEngine.kt      <-- Dual-sink fan-out point (Sink 1 & Sink 2)
│   │   │   ├── RecordingForegroundService.kt(Manages service lifecycle & state)
│   │   │   └── RecordingOverlayController.kt(Manages in-call floating overlay)
│   │   ├── integrations\scrcpy\
│   │   │   ├── ScrcpyClient.kt              (Binary parser for audio stream pipe)
│   │   │   ├── ScrcpyAudioMuxer.kt          (Sink 1: writes .ogg / .m4a to disk)
│   │   │   └── ScrcpyAudioSource.kt         (Maps audio sources, incl. VOICE_CALL_DOWNLINK)
│   │   └── data\
│   │       └── AppPreferences.kt            (App settings & defaults)
│
├── docs\
│   └── TRUE_VOICE_SIH_MASTER_PLAN.md        (Comprehensive master architecture document)
└── context\
    └── SIH_TEAM_CONTEXT.md                  (This document for teammates and AI agents)
```

---

### 5. Next Steps / Immediate Implementation Roadmap

* **Step 1 (Testing Phase 1)**: Build debug APK, verify live Jio/Airtel call recording, and check in `adb logcat -s TrueVoice` that Sink 1 saves `.ogg` and Sink 2 logs live decoded PCM metrics:
  ```
  [TrueVoice Telemetry] Live PCM: Active=true | Rate=50 pkt/s | Buffer=3.00s / 3.0s | RMS=0.0340
  ```
* **Step 2 (Phase 2: ML Engine)**:
  - Add `onnxruntime-android` dependency.
  - Integrate **Silero VAD** (ONNX, ~2MB) to gate speech vs silence.
  - Integrate **AASIST-Lite / MobileNetV3 Anti-Spoofing** (ONNX, ~6MB) to generate the 0.0–1.0 synthetic score.
  - Implement Trusted Whitelist in Android Room DB.
* **Step 3 (Phase 3: Floating HUD & UPI Safety)**:
  - Redesign `RecordingOverlay` into a modern Material 3 Glass Pill (🟢 Safe, 🟡 Caution, 🔴 Clone Alert).
  - Implement auto-retraction when switching to payment apps (GPay / PhonePe).
* **Step 4 (Phase 4: Threat Intelligence Backend)**:
  - Deploy lightweight FastAPI backend for community reputation lookup and scam reporting.
