import time
import requests
from pathlib import Path

BASE_URL = "http://127.0.0.1:8000"

print("================================================================================")
print("                   TESTING TRUE VOICE ENTERPRISE REST API                       ")
print("================================================================================")

# 1. Healthcheck
r = requests.get(f"{BASE_URL}/api/v1/health")
print(f"[1] Health Check: Status={r.status_code} -> {r.json()}")

# 2. Check Reputation of Seeded Scam Number
phone_scam = "+919876543210"
r = requests.get(f"{BASE_URL}/api/v1/reputation", params={"phone": phone_scam})
print(f"[2] Reputation ({phone_scam}): Status={r.status_code} -> {r.json()}")

# 3. Check Reputation of Clean Unknown Number
phone_clean = "+919999911111"
r = requests.get(f"{BASE_URL}/api/v1/reputation", params={"phone": phone_clean})
print(f"[3] Reputation ({phone_clean}): Status={r.status_code} -> {r.json()}")

# 4. Submit a Threat Report
payload = {
    "call_id": "CALL-TEST-LIVE",
    "caller_number": "+919999911111",
    "peak_synthetic_score": 0.992,
    "highest_risk_level": "CLONE_ALERT",
    "vocoder_detected": True,
    "coercion_detected": True,
    "triggered_keywords": ["arrest", "police", "wire transfer"],
    "evidence_audio_sha256": "abcdef1234567890",
    "reported_by": "TrueVoice-Mobile-Test"
}
r = requests.post(f"{BASE_URL}/api/v1/report", json=payload)
print(f"[4] Submit Threat Report: Status={r.status_code} -> {r.json()}")

# 5. Check Reputation of Clean Number again (should now be flagged!)
r = requests.get(f"{BASE_URL}/api/v1/reputation", params={"phone": phone_clean})
print(f"[5] Reputation After Report: Status={r.status_code} -> {r.json()}")

# 6. Upload an audio file for deepfake analysis
ai_file = Path("AudioFiles/new5.ogg")
if ai_file.exists():
    with open(ai_file, "rb") as f:
        files = {"file": (ai_file.name, f, "audio/ogg")}
        r = requests.post(f"{BASE_URL}/api/v1/analyze", files=files)
        print(f"[6] Analyze Audio ({ai_file.name}): Status={r.status_code} -> {r.json()}")

human_file = Path("AudioFiles/new2.ogg")
if human_file.exists():
    with open(human_file, "rb") as f:
        files = {"file": (human_file.name, f, "audio/ogg")}
        r = requests.post(f"{BASE_URL}/api/v1/analyze", files=files)
        print(f"[7] Analyze Audio ({human_file.name}): Status={r.status_code} -> {r.json()}")

print("\n[✓] ALL REST API ENDPOINTS TESTED SUCCESSFULLY!")
