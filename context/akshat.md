# Akshat's Context & Decision Log

## Smart India Hackathon 2026

### Project

True Voice: AI-powered real-time voice clone impersonation detection for SIH Problem Statement #26104.

### Purpose

This is Akshat's shared project context for teammates and their AI assistants. Keep it focused on project history, decisions, current work, and handoff information. Do not add private notes, credentials, tokens, or machine-specific details here.

## Project Direction

The project is built on the existing ShizuCallRecorder Android application rather than implementing telephony capture from scratch. The foundation provides Kotlin, Jetpack Compose, Material 3, Coroutines/Flows, Room, foreground services, Shizuku integration, and scrcpy-server audio capture.

The product goal is to detect suspected AI-generated or cloned caller voices during a call, while preserving an audio record that can support a cybercrime report. The intended design prioritizes on-device processing, privacy, offline operation, and low latency.

## Important Decisions

### Audio capture and storage

The planned audio path separates the incoming Opus stream into two sinks:

- A file sink for archived OGG recordings and later forensic review.
- An in-memory sink for live decoding, PCM buffering, and model inference.

This avoids repeatedly reading live audio from disk and keeps real-time inference responsive.

### On-device machine learning

Voice analysis is intended to run locally with ONNX Runtime Mobile. The planned two-stage pipeline uses voice activity detection first, followed by an anti-spoofing classifier. This reduces unnecessary inference, avoids sending call audio to a server, and supports poor-connectivity scenarios.

### User interface and banking apps

The planned warning experience is a small draggable floating HUD rather than a full-screen interruption. The HUD should retract when a banking or UPI app is in the foreground so it does not interfere with anti-overlay protections.

### Threat intelligence

A pre-call reputation lookup may provide an early warning for known numbers. Trusted contacts can reduce unnecessary inference, with a high-security option available when caller-ID spoofing is a concern.

## Current Reference Documents

- [SIH master plan](../docs/TRUE_VOICE_SIH_MASTER_PLAN.md): product and implementation direction.
- [Problem statement](../docs/problem_statement.json): formal SIH requirements.
- [Project README](../README.md): repository-level setup and overview.
- [Sahil's shared context](sahil.md): another teammate's decision log. Treat it as reference, not as Akshat-specific work history.

## Collaboration Rules

- Record significant implementation changes, the reason for each change, and any tradeoffs here.
- Before changing a shared interface or architecture, check the relevant source files and update the handoff notes.
- Keep work modular so teammates can develop audio capture, ML inference, UI, and backend integration in parallel.
- Mark assumptions clearly until they are verified on a physical Android device.
- Do not treat planned components as complete until they exist in the repository and have a verification result.

## Handoff Template

### Date / Contributor

- **Area:**
- **Change made:**
- **Why:**
- **Files:**
- **Verification:**
- **Follow-up or risk:**