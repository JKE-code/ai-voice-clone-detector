"""
True Voice — Enterprise Threat Intelligence & Voice Biometric REST API
Problem Statement #26104 (Smart India Hackathon)

Provides high-throughput REST API endpoints for:
1. Bank / Call Center Real-Time Caller Reputation Query (/api/v1/reputation)
2. Anonymous Telecom Threat Reporting & Cybercrime Sync (/api/v1/report)
3. Remote Audio File Deepfake & Clone Forensic Analysis (/api/v1/analyze)
"""

import os
import sys
import time
import sqlite3
import subprocess
import tempfile
from pathlib import Path
from typing import List, Optional
import numpy as np

import imageio_ffmpeg
import onnxruntime as ort
from fastapi import FastAPI, File, UploadFile, Query, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

# Set up FastAPI application
app = FastAPI(
    title="True Voice — Enterprise Voice Security API",
    description="Real-time telecom fraud, deepfake clone detection, and threat intelligence grid for banks, fintech, and call centers.",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc"
)

# Enable CORS for enterprise dashboards
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# -----------------------------------------------------------------------------
# SQLite Threat Intelligence Database
# -----------------------------------------------------------------------------
DB_PATH = Path("api_backend/threat_intelligence.db")
DB_PATH.parent.mkdir(parents=True, exist_ok=True)

def init_db():
    conn = sqlite3.connect(str(DB_PATH))
    cursor = conn.cursor()
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS threat_reports (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            call_id TEXT NOT NULL,
            caller_number TEXT NOT NULL,
            clean_number TEXT NOT NULL,
            peak_synthetic_score REAL NOT NULL,
            highest_risk_level TEXT NOT NULL,
            vocoder_detected INTEGER NOT NULL,
            coercion_detected INTEGER NOT NULL,
            triggered_keywords TEXT,
            timestamp INTEGER NOT NULL,
            reported_by TEXT DEFAULT 'TrueVoice-Mobile'
        );
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_threat_number ON threat_reports(clean_number);")
    
    # Seed known scam numbers if table empty
    cursor.execute("SELECT COUNT(*) FROM threat_reports")
    if cursor.fetchone()[0] == 0:
        now = int(time.time())
        seed_data = [
            ("CALL-SEED-01", "+919876543210", "9876543210", 0.98, "CLONE_ALERT", 1, 1, "police,arrest,customs", now - 3600),
            ("CALL-SEED-02", "+918765432109", "8765432109", 0.95, "CLONE_ALERT", 1, 0, "cbi,digital arrest", now - 7200),
            ("CALL-SEED-03", "+917654321098", "7654321098", 0.35, "FINANCIAL_COERCION", 0, 1, "otp,urgent,transfer", now - 1800),
        ]
        cursor.executemany("""
            INSERT INTO threat_reports (call_id, caller_number, clean_number, peak_synthetic_score, highest_risk_level, vocoder_detected, coercion_detected, triggered_keywords, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, seed_data)
        conn.commit()
    conn.close()

init_db()

# -----------------------------------------------------------------------------
# ONNX Model Loader for Instant File Analysis
# -----------------------------------------------------------------------------
ONNX_MODEL_PATH = Path("app/src/main/assets/models/voice_clone_detector.onnx")
ort_session = None

def get_onnx_session():
    global ort_session
    if ort_session is None and ONNX_MODEL_PATH.exists():
        opts = ort.SessionOptions()
        opts.intra_op_num_threads = 2
        ort_session = ort.InferenceSession(str(ONNX_MODEL_PATH), sess_options=opts, providers=["CPUExecutionProvider"])
    return ort_session

def decode_audio_bytes(raw_bytes: bytes) -> np.ndarray:
    ffmpeg_exe = imageio_ffmpeg.get_ffmpeg_exe()
    with tempfile.NamedTemporaryFile(delete=False, suffix=".tmp") as tmp:
        tmp.write(raw_bytes)
        tmp_path = tmp.name

    try:
        cmd = [
            ffmpeg_exe, "-y", "-nostdin", "-threads", "1",
            "-i", tmp_path,
            "-vn", "-ac", "1", "-ar", "16000",
            "-f", "f32le", "pipe:1"
        ]
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
        samples = np.frombuffer(res.stdout, dtype=np.float32).copy()
        return samples
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)

# -----------------------------------------------------------------------------
# Request / Response Schemas
# -----------------------------------------------------------------------------
class ReputationResponse(BaseModel):
    phone_number: str
    clean_number: str
    reputation_status: str # "SAFE", "SUSPICIOUS", "CONFIRMED_FRAUD", "AI_CLONE_ATTACK"
    risk_score: float # 0.0 to 1.0
    total_threat_reports: int
    latest_threat_category: Optional[str]
    last_reported_timestamp: Optional[int]
    recommended_action: str # "ALLOW", "WARN_USER", "BLOCK_TRANSACTION"

class ThreatReportRequest(BaseModel):
    call_id: str
    caller_number: str
    peak_synthetic_score: float = Field(..., ge=0.0, le=1.0)
    highest_risk_level: str
    vocoder_detected: bool = False
    coercion_detected: bool = False
    triggered_keywords: List[str] = []
    evidence_audio_sha256: Optional[str] = None
    reported_by: Optional[str] = "TrueVoice-Android"

class ReportResponse(BaseModel):
    status: str
    report_id: int
    message: str

class AudioAnalysisResponse(BaseModel):
    filename: str
    duration_seconds: float
    synthetic_probability: float
    verdict: str
    confidence: float
    latency_ms: float

# -----------------------------------------------------------------------------
# API Endpoints
# -----------------------------------------------------------------------------
@app.get("/api/v1/health", tags=["System"])
def health_check():
    session = get_onnx_session()
    conn = sqlite3.connect(str(DB_PATH))
    c = conn.cursor()
    c.execute("SELECT COUNT(*) FROM threat_reports")
    total_reports = c.fetchone()[0]
    conn.close()

    return {
        "status": "online",
        "service": "True Voice Enterprise Security Grid",
        "version": "1.0.0",
        "model_loaded": session is not None,
        "database_connected": True,
        "total_threat_reports_logged": total_reports
    }

@app.get("/api/v1/reputation", response_model=ReputationResponse, tags=["Telecom Intelligence"])
def get_caller_reputation(phone: str = Query(..., description="E.164 phone number to check, e.g. +919876543210")):
    clean = "".join(filter(str.isdigit, phone))
    if len(clean) >= 10:
        clean = clean[-10:]

    conn = sqlite3.connect(str(DB_PATH))
    cursor = conn.cursor()
    cursor.execute("""
        SELECT peak_synthetic_score, highest_risk_level, timestamp
        FROM threat_reports
        WHERE clean_number = ?
        ORDER BY timestamp DESC
    """, (clean,))
    rows = cursor.fetchall()
    conn.close()

    if not rows:
        return ReputationResponse(
            phone_number=phone,
            clean_number=clean,
            reputation_status="SAFE",
            risk_score=0.05,
            total_threat_reports=0,
            latest_threat_category=None,
            last_reported_timestamp=None,
            recommended_action="ALLOW"
        )

    total_reports = len(rows)
    peak_score = max(r[0] for r in rows)
    latest_level = rows[0][1]
    latest_time = rows[0][2]

    if "CLONE" in latest_level or peak_score >= 0.70:
        status = "AI_CLONE_ATTACK"
        action = "BLOCK_TRANSACTION"
    elif "COERCION" in latest_level:
        status = "CONFIRMED_FRAUD"
        action = "WARN_USER"
    elif peak_score >= 0.40:
        status = "SUSPICIOUS"
        action = "WARN_USER"
    else:
        status = "SAFE"
        action = "ALLOW"

    return ReputationResponse(
        phone_number=phone,
        clean_number=clean,
        reputation_status=status,
        risk_score=peak_score,
        total_threat_reports=total_reports,
        latest_threat_category=latest_level,
        last_reported_timestamp=latest_time,
        recommended_action=action
    )

@app.post("/api/v1/report", response_model=ReportResponse, tags=["Telecom Intelligence"])
def report_threat(report: ThreatReportRequest):
    clean = "".join(filter(str.isdigit, report.caller_number))
    if len(clean) >= 10:
        clean = clean[-10:]

    conn = sqlite3.connect(str(DB_PATH))
    cursor = conn.cursor()
    cursor.execute("""
        INSERT INTO threat_reports (
            call_id, caller_number, clean_number, peak_synthetic_score,
            highest_risk_level, vocoder_detected, coercion_detected,
            triggered_keywords, timestamp, reported_by
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (
        report.call_id,
        report.caller_number,
        clean,
        report.peak_synthetic_score,
        report.highest_risk_level,
        1 if report.vocoder_detected else 0,
        1 if report.coercion_detected else 0,
        ",".join(report.triggered_keywords),
        int(time.time()),
        report.reported_by or "TrueVoice-Android"
    ))
    report_id = cursor.lastrowid
    conn.commit()
    conn.close()

    return ReportResponse(
        status="success",
        report_id=report_id,
        message=f"Threat report successfully recorded for {report.caller_number}"
    )

@app.post("/api/v1/analyze", response_model=AudioAnalysisResponse, tags=["Forensic Analysis"])
async def analyze_audio(file: UploadFile = File(...)):
    session = get_onnx_session()
    if session is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Voice Clone ONNX Model is not loaded"
        )

    t0 = time.perf_counter()
    content = await file.read()
    try:
        samples = decode_audio_bytes(content)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Audio decoding failed: {e}"
        )

    duration = len(samples) / 16000.0
    if len(samples) < 48000:
        padded = np.zeros(48000, dtype=np.float32)
        padded[:len(samples)] = samples
        inp = np.expand_dims(padded, axis=0)
    else:
        # Average across sliding 3-second windows
        windows = []
        for i in range(0, len(samples) - 48000 + 1, 16000):
            chunk = samples[i : i + 48000]
            if np.sqrt(np.mean(chunk**2)) > 0.008:
                windows.append(chunk)
        if not windows:
            windows.append(samples[:48000])
        inp = np.array(windows, dtype=np.float32)

    input_name = session.get_inputs()[0].name
    ort_out = session.run(None, {input_name: inp})
    probs = ort_out[0].flatten()
    mean_prob = float(np.mean(probs))

    latency = (time.perf_counter() - t0) * 1000.0
    verdict = "AI_CLONE" if mean_prob >= 0.65 else ("SUSPICIOUS" if mean_prob >= 0.40 else "GENUINE_HUMAN")
    confidence = max(mean_prob, 1.0 - mean_prob)

    return AudioAnalysisResponse(
        filename=file.filename or "audio_upload",
        duration_seconds=round(duration, 2),
        synthetic_probability=round(mean_prob, 4),
        verdict=verdict,
        confidence=round(confidence, 4),
        latency_ms=round(latency, 2)
    )

if __name__ == "__main__":
    import uvicorn
    print("[*] Starting True Voice Enterprise API Server on http://127.0.0.1:8000 ...")
    uvicorn.run("api_backend.server:app", host="0.0.0.0", port=8000, reload=False)
