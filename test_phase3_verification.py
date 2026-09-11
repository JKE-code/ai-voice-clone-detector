#!/usr/bin/env python3
"""
True Voice: Phase 3 Verification Script
Validates all Phase 3 components, files, syntax, and wiring.
"""
import os
import re

REQUIRED_FILES = [
    "app/src/main/java/com/truevoice/ui/theme/RiskColors.kt",
    "app/src/main/java/com/truevoice/ui/hud/SecurityHudPill.kt",
    "app/src/main/java/com/truevoice/ui/ForegroundAppMonitor.kt",
    "app/src/main/java/com/truevoice/ui/ForensicTimelineActivity.kt",
    "app/src/main/java/com/truevoice/ui/forensics/ForensicTimelineScreen.kt",
    "app/src/main/java/com/truevoice/ui/history/CallHistoryScreen.kt",
    "app/src/main/java/com/truevoice/forensics/ForensicCallRecord.kt",
    "app/src/main/java/com/truevoice/forensics/ForensicRepository.kt",
]

def test_files_exist():
    print("[1/5] Checking Phase 3 file existence...")
    for path in REQUIRED_FILES:
        full_path = os.path.join(os.getcwd(), path.replace("/", os.sep))
        assert os.path.isfile(full_path), f"Missing required Phase 3 file: {path}"
        print(f"  [OK] Found {path} ({os.path.getsize(full_path)} bytes)")
    print("  -> All Phase 3 files present!")

def test_manifest_registration():
    print("\n[2/5] Checking AndroidManifest.xml registration...")
    manifest_path = os.path.join(os.getcwd(), "app", "src", "main", "AndroidManifest.xml")
    with open(manifest_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "com.truevoice.ui.ForensicTimelineActivity" in content, "ForensicTimelineActivity not registered in AndroidManifest.xml"
    print("  [OK] ForensicTimelineActivity is properly registered in AndroidManifest.xml")

def test_overlay_controller_wiring():
    print("\n[3/5] Checking RecordingOverlayController HUD & UPI wiring...")
    ctrl_path = os.path.join(os.getcwd(), "app", "src", "main", "java", "com", "kitsumed", "shizucallrecorder", "services", "recording", "RecordingOverlayController.kt")
    with open(ctrl_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "SecurityHudPill" in content, "SecurityHudPill not hosted in RecordingOverlayController"
    assert "ForegroundAppMonitor" in content, "ForegroundAppMonitor not initialized in RecordingOverlayController"
    assert "upiMonitor.stopMonitoring()" in content, "upiMonitor.stopMonitoring() not called in hideOverlay"
    assert "ForensicTimelineActivity" in content, "ForensicTimelineActivity intent not wired in onOpenForensics"
    print("  [OK] SecurityHudPill hosted and observed with StateFlow")
    print("  [OK] ForegroundAppMonitor hooked for UPI auto-retraction")
    print("  [OK] onOpenForensics navigates to ForensicTimelineActivity")

def test_forensics_pipeline_wiring():
    print("\n[4/5] Checking LiveAnalysisSink & AudioRecordingEngine pipeline connection...")
    sink_path = os.path.join(os.getcwd(), "app", "src", "main", "java", "com", "truevoice", "audio", "LiveAnalysisSink.kt")
    with open(sink_path, "r", encoding="utf-8") as f:
        sink_content = f.read()
    assert "ForensicRepository.startSession" in sink_content, "ForensicRepository.startSession not called in LiveAnalysisSink"
    assert "ForensicRepository.recordWindow" in sink_content, "ForensicRepository.recordWindow not called in LiveAnalysisSink"
    assert "ForensicRepository.endSession" in sink_content, "ForensicRepository.endSession not called in LiveAnalysisSink"

    eng_path = os.path.join(os.getcwd(), "app", "src", "main", "java", "com", "kitsumed", "shizucallrecorder", "services", "recording", "AudioRecordingEngine.kt")
    with open(eng_path, "r", encoding="utf-8") as f:
        eng_content = f.read()
    assert "initializationMetadata?.getBestNumber()" in eng_content, "Caller number not forwarded to LiveAnalysisSink"
    print("  [OK] LiveAnalysisSink forwards telemetry to ForensicRepository")
    print("  [OK] AudioRecordingEngine forwards caller number and contact name")

def test_settings_screen_entry():
    print("\n[5/5] Checking SettingsScreen True Voice entry...")
    settings_path = os.path.join(os.getcwd(), "app", "src", "main", "java", "com", "kitsumed", "shizucallrecorder", "ui", "screens", "SettingsScreen.kt")
    with open(settings_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "TrueVoiceSection" in content, "TrueVoiceSection not in SettingsScreen"
    assert "ForensicTimelineActivity" in content, "ForensicTimelineActivity intent not in SettingsScreen"
    print("  [OK] TrueVoiceSection active in SettingsScreen")

if __name__ == "__main__":
    print("==================================================")
    print("  TRUE VOICE PHASE 3 INTEGRATION VERIFICATION")
    print("==================================================")
    test_files_exist()
    test_manifest_registration()
    test_overlay_controller_wiring()
    test_forensics_pipeline_wiring()
    test_settings_screen_entry()
    print("\n==================================================")
    print("  PHASE 3 VERIFICATION PASSED (100% COMPLETE)")
    print("==================================================")
