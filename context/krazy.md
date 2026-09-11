# Personal Development Context & Local Environment
## True Voice Developer: krazy

> **Note**: This file is kept in `.gitignore` to prevent leaking personal device paths, private test phone numbers, or machine-specific environment configurations to the shared repository.

---

### 1. Local Machine Specifications & Paths
* **OS**: Windows (PowerShell)
* **Workspace Directory**: `d:\AI-Voice-Clone-Detector`
* **User Profile**: `C:\Users\krazy`
* **Git Remote**: `https://github.com/JKE-code/ai-voice-clone-detector.git` (Branch: `main`)

---

### 2. Device & Testing Setup
* **Target Device**: Android 12+ smartphone with USB Debugging enabled.
* **Carrier**: Jio / Airtel (cellular call testing verified with two-sided capture).
* **Storage Target**: `/sdcard/CallRecorder/` (linked SAF directory for `.ogg` output files).
* **Audio Capture Mode**: `VOICE_CALL_DOWNLINK` (configured for pure incoming caller audio isolation).
* **Privileged Bridge**: Shizuku + `scrcpy-server` v4.0.

---

### 3. Quick Local Build & Test Commands

#### Run via Android Studio
* Open `d:\AI-Voice-Clone-Detector` in Android Studio.
* Select connected phone from device target list.
* Click **Run (Shift + F10)**.

#### View Real-Time Audio Pipeline Telemetry via ADB
```powershell
adb logcat -s TrueVoice AudioRecordingEngine
```

#### Expected Telemetry in Logs during Live Call:
```text
[TrueVoice] LiveAnalysisSink initialized: opus 48000 Hz, 2 ch -> 16kHz mono ring buffer
[TrueVoice Telemetry] Live PCM: Active=true | Rate=50 pkt/s | Buffer=3.00s / 3.0s | RMS=0.0340 | Decoded=48000 samples
```

---

### 4. Personal SIH Demo Checklist
- [ ] Connect phone via USB-C with USB Debugging & "Install via USB" enabled.
- [ ] Ensure Shizuku service is running on the phone.
- [ ] Ensure `CallRecorder` folder is selected in app settings.
- [ ] Make a live test call on Jio/Airtel and confirm `.ogg` is saved in `CallRecorder/`.
- [ ] Verify logcat displays active PCM decoding without dropping frames.
- [ ] Verify Google Pay / PhonePe opens smoothly without overlay error.
