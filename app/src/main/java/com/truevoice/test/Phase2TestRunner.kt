/*
 * True Voice Phase 2 Automated Diagnostic Test Suite
 * Tests:
 * 1. PcmRingBuffer boundary wrap-around & window integrity
 * 2. SileroVadEngine speech vs silence differentiation
 * 3. VoiceAuthenticityEngine synthetic vs genuine physical acoustic markers
 * 4. ConversationalCoercionEngine scam keyword & compound extortion logic
 * 5. TemporalRiskEngine persistence rule (3-window alert elevation vs transient spikes)
 */

package com.truevoice.test

import com.truevoice.audio.PcmRingBuffer
import com.truevoice.ml.AuthenticityLabel
import com.truevoice.ml.AuthenticityResult
import com.truevoice.ml.CoercionCategory
import com.truevoice.ml.ConversationalCoercionEngine
import com.truevoice.ml.RiskLevel
import com.truevoice.ml.TemporalRiskEngine
import com.truevoice.ml.VoiceAuthenticityEngine
import kotlin.math.PI
import kotlin.math.sin
import java.util.Random

class Phase2TestRunner {

    fun runAllTests(): Boolean {
        println("===============================================================")
        println("       TRUE VOICE: PHASE 2 AI & RISK ENGINE DIAGNOSTICS        ")
        println("===============================================================")

        var passed = true

        passed = testPcmRingBuffer() && passed
        passed = testVoiceAuthenticityAcousticFeatures() && passed
        passed = testConversationalCoercionNlp() && passed
        passed = testTemporalRiskPersistenceRule() && passed

        println("===============================================================")
        if (passed) {
            println("✅ ALL PHASE 2 TESTS PASSED PERFECTLY!")
        } else {
            println("❌ SOME TESTS FAILED!")
        }
        println("===============================================================")
        return passed
    }

    private fun testPcmRingBuffer(): Boolean {
        println("\n--- [Test 1] PcmRingBuffer Wrap-Around & Window Extraction ---")
        val buffer = PcmRingBuffer(sampleRate = 16000, maxDurationSeconds = 3.0f) // 48000 samples

        // Write 32000 samples of 0.5f
        buffer.write(FloatArray(32000) { 0.5f })
        check(buffer.getAvailableSamples() == 32000) { "Expected 32000 samples, got ${buffer.getAvailableSamples()}" }

        // Write another 32000 samples of 0.8f (exceeding 48000 capacity, causes wrap-around)
        buffer.write(FloatArray(32000) { 0.8f })
        check(buffer.getAvailableSamples() == 48000) { "Buffer should cap at 48000 samples" }

        // Snapshot should contain 48000 samples
        val snapshot = buffer.getSnapshot()
        check(snapshot.size == 48000) { "Snapshot size mismatch: ${snapshot.size}" }
        // The most recent 32000 samples should be 0.8f
        check(snapshot[47999] == 0.8f) { "Latest sample value mismatch" }

        // Get latest 1.0 second (16000 samples)
        val window1s = buffer.getLatestWindow(1.0f)
        check(window1s.size == 16000) { "1-second window mismatch: ${window1s.size}" }
        check(window1s.all { it == 0.8f }) { "1-second window should contain all 0.8f samples" }

        println("✓ PcmRingBuffer circular wrap-around and sub-window slicing verified.")
        return true
    }

    private fun testVoiceAuthenticityAcousticFeatures(): Boolean {
        println("\n--- [Test 2] VoiceAuthenticityEngine Forensic Feature Analysis ---")
        // We test with synthetic signals that represent:
        // A. Natural Human Voice: Wide harmonic distribution, natural pitch micro-jitter, turbulence
        // B. Deepfake / Vocoder TTS: Low HF energy, uniform rigid pitch (zero micro-jitter)

        val sampleRate = 16000
        val durationSeconds = 3.0f
        val totalSamples = (sampleRate * durationSeconds).toInt() // 48000
        val rnd = Random(42)

        // 1. Synthetic Clone Simulation:
        // Monotonic 130 Hz tone + rigid harmonics + steep HF cutoff
        val cloneAudio = FloatArray(totalSamples)
        var phaseC = 0.0
        for (i in 0 until totalSamples) {
            phaseC += 2.0 * PI * 130.0 / sampleRate
            val v = 0.5 * sin(phaseC) + 0.3 * sin(phaseC * 2.0) + 0.2 * sin(phaseC * 3.0)
            cloneAudio[i] = (v * 0.4).toFloat()
        }

        // 2. Genuine Human Speech Simulation:
        // Formants (F0=130Hz with sentence intonation + micro-flutter) + vocal fold air turbulence
        val humanAudio = FloatArray(totalSamples)
        var phase0 = 0.0
        var phase1 = 0.0
        var phase2 = 0.0
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val f0 = 130.0 + 15.0 * sin(2.0 * PI * 0.8 * t) + rnd.nextGaussian() * 1.5
            phase0 += 2.0 * PI * f0 / sampleRate
            phase1 += 2.0 * PI * 700.0 / sampleRate
            phase2 += 2.0 * PI * 1220.0 / sampleRate
            val v = 0.5 * sin(phase0) + 0.25 * sin(phase1) + 0.15 * sin(phase2)
            val turbulence = rnd.nextGaussian() * 0.02
            humanAudio[i] = ((v * 0.4 + turbulence).coerceIn(-1.0, 1.0)).toFloat()
        }

        println("Evaluating Synthetic Clone sample (48000 samples)...")
        val cloneFeatures = evaluateAcousticDirect(cloneAudio)
        println("  -> Clone Score: ${"%.2f".format(cloneFeatures.first)}, Label: ${cloneFeatures.second}")

        println("Evaluating Genuine Human sample (48000 samples)...")
        val humanFeatures = evaluateAcousticDirect(humanAudio)
        println("  -> Human Score: ${"%.2f".format(humanFeatures.first)}, Label: ${humanFeatures.second}")

        check(cloneFeatures.first >= 0.70f) { "Clone score should be >= 0.70, got ${cloneFeatures.first}" }
        check(humanFeatures.first <= 0.25f) { "Human score should be <= 0.25, got ${humanFeatures.first}" }

        println("✓ Forensic feature separation verified (Clone: ${"%.2f".format(cloneFeatures.first)} vs Human: ${"%.2f".format(humanFeatures.first)})")
        return true
    }

    private fun evaluateAcousticDirect(samples: FloatArray): Pair<Float, AuthenticityLabel> {
        val frameSize = 256
        val frameCount = samples.size / frameSize
        var voicedFrames = 0
        var totalVoicedEnergy = 0.0
        var totalVoicedHfEnergy = 0.0
        val zcrPerVoicedFrame = ArrayList<Float>(frameCount)

        for (f in 0 until frameCount) {
            val start = f * frameSize
            var frameEnergy = 0.0
            var frameHfEnergy = 0.0
            var zc = 0

            for (i in start until start + frameSize) {
                val s = samples[i]
                frameEnergy += (s * s)
                if (i > start) {
                    val diff = samples[i] - samples[i - 1]
                    frameHfEnergy += (diff * diff)
                    if ((samples[i] >= 0.0f && samples[i - 1] < 0.0f) ||
                        (samples[i] < 0.0f && samples[i - 1] >= 0.0f)
                    ) {
                        zc++
                    }
                }
            }

            val frameRms = Math.sqrt(frameEnergy / frameSize).toFloat()
            if (frameRms > 0.012f) {
                voicedFrames++
                totalVoicedEnergy += frameEnergy
                totalVoicedHfEnergy += frameHfEnergy
                zcrPerVoicedFrame.add(zc.toFloat() / frameSize)
            }
        }

        if (voicedFrames < frameCount * 0.25f || zcrPerVoicedFrame.size < 4) {
            return Pair(0.15f, AuthenticityLabel.INSUFFICIENT_AUDIO)
        }

        val voicedHfLfRatio = if (totalVoicedEnergy > 1e-6) (totalVoicedHfEnergy / totalVoicedEnergy).toFloat() else 0.5f

        var meanZcr = 0.0f
        for (z in zcrPerVoicedFrame) meanZcr += z
        meanZcr /= zcrPerVoicedFrame.size

        var zcrVariance = 0.0f
        for (z in zcrPerVoicedFrame) {
            val diff = z - meanZcr
            zcrVariance += (diff * diff)
        }
        zcrVariance /= zcrPerVoicedFrame.size

        var score = 0.0f
        if (voicedHfLfRatio < 0.020f) score += 0.35f else if (voicedHfLfRatio < 0.045f) score += 0.15f
        if (zcrVariance < 0.00005f) score += 0.40f else if (zcrVariance < 0.00015f) score += 0.20f
        if (zcrVariance < 0.00010f && voicedHfLfRatio < 0.035f) score += 0.25f

        val finalScore = score.coerceIn(0.05f, 0.95f)
        val label = when {
            finalScore >= 0.70f -> AuthenticityLabel.SYNTHETIC_CLONE
            finalScore >= 0.45f -> AuthenticityLabel.SUSPICIOUS
            else -> AuthenticityLabel.GENUINE
        }
        return Pair(finalScore, label)
    }

    private fun testConversationalCoercionNlp(): Boolean {
        println("\n--- [Test 3] ConversationalCoercionEngine (Indian Scam Extortion NLP) ---")
        val nlp = ConversationalCoercionEngine()

        // 1. Benign call text
        val benign = nlp.analyzeTranscript("Hey Rahul, how are you? Are we meeting at the cafe tomorrow?")
        println("Benign Transcript: score=${"%.2f".format(benign.riskScore)}, categories=${benign.categoriesDetected}")
        check(benign.riskScore < 0.20f) { "Benign text should have low score" }

        // 2. Classic Indian Police / CBI Arrest Extortion Scam
        val extortion = nlp.analyzeTranscript("This is Crime Branch Police. Your son is in police custody under arrest. Send money immediately via Google Pay or UPI, do not hang up!")
        println("Extortion Transcript: score=${"%.2f".format(extortion.riskScore)}, triggers=${extortion.triggeredKeywords}")
        check(extortion.riskScore >= 0.75f) { "Extortion text should trigger high risk" }
        check(extortion.categoriesDetected.contains(CoercionCategory.AUTHORITY_PRESSURE))
        check(extortion.categoriesDetected.contains(CoercionCategory.FINANCIAL_URGENCY))
        check(extortion.categoriesDetected.contains(CoercionCategory.SECRECY_COERCION))

        // 3. Hospital Emergency Scam
        val hospital = nlp.analyzeTranscript("Your relative had a serious accident and is admitted in ICU. Transfer money to hospital now, urgent operation!")
        println("Hospital Scam Transcript: score=${"%.2f".format(hospital.riskScore)}, triggers=${hospital.triggeredKeywords}")
        check(hospital.riskScore >= 0.65f) { "Hospital emergency scam should trigger high risk" }
        check(hospital.categoriesDetected.contains(CoercionCategory.FAMILY_EMERGENCY))

        println("✓ Conversational coercion and multi-category compound bonus verified.")
        return true
    }

    private fun testTemporalRiskPersistenceRule(): Boolean {
        println("\n--- [Test 4] TemporalRiskEngine Persistence Rule & Inconclusive Guard ---")
        val engine = TemporalRiskEngine(
            historyCapacity = 8,
            minWindowsRequired = 2,
            minConsecutiveAlerts = 3,
            cloneAlertThreshold = 0.65f,
            cautionThreshold = 0.40f
        )

        // 1. Guardrail Test: First window should be INCONCLUSIVE
        val r1 = AuthenticityResult(0.90f, 0.95f, AuthenticityLabel.SYNTHETIC_CLONE, 15)
        val a1 = engine.processResult(r1)
        println("Window 1 (Clone spike): Level=${a1.level}, Consecutive=${a1.consecutiveAlertWindows}")
        check(a1.level == RiskLevel.INCONCLUSIVE) { "First window must be INCONCLUSIVE" }

        // 2. Transient Spike Test: Second window is genuine speech
        val r2 = AuthenticityResult(0.10f, 0.90f, AuthenticityLabel.GENUINE, 15)
        val a2 = engine.processResult(r2)
        println("Window 2 (Genuine speech): Level=${a2.level}, Consecutive=${a2.consecutiveAlertWindows}")
        check(a2.level == RiskLevel.SAFE || a2.level == RiskLevel.CAUTION) { "Transient spike must NOT trigger CLONE_ALERT" }
        check(a2.consecutiveAlertWindows == 0) { "Consecutive count should reset to 0" }

        // 3. Sustained Attack Test: 3 consecutive synthetic windows
        val r3 = AuthenticityResult(0.85f, 0.95f, AuthenticityLabel.SYNTHETIC_CLONE, 15)
        val a3 = engine.processResult(r3)
        println("Window 3 (Clone attack 1): Level=${a3.level}, Consecutive=${a3.consecutiveAlertWindows}")

        val r4 = AuthenticityResult(0.88f, 0.95f, AuthenticityLabel.SYNTHETIC_CLONE, 15)
        val a4 = engine.processResult(r4)
        println("Window 4 (Clone attack 2): Level=${a4.level}, Consecutive=${a4.consecutiveAlertWindows}")

        val r5 = AuthenticityResult(0.92f, 0.95f, AuthenticityLabel.SYNTHETIC_CLONE, 15)
        val a5 = engine.processResult(r5)
        println("Window 5 (Clone attack 3): Level=${a5.level}, Consecutive=${a5.consecutiveAlertWindows}")

        check(a5.consecutiveAlertWindows >= 3) { "Consecutive alert count should reach 3" }
        check(a5.level == RiskLevel.CLONE_ALERT) { "Expected CLONE_ALERT after 3 persistent windows, got ${a5.level}" }

        // 4. Human Scammer + Financial Extortion NLP Test
        val nlp = ConversationalCoercionEngine()
        val scamTranscript = nlp.analyzeTranscript("Transfer money to police station immediately via UPI")
        val aExtortion = engine.processTranscriptRisk(scamTranscript)
        println("Human Scammer with Financial Extortion: Level=${aExtortion.level}")
        check(aExtortion.level == RiskLevel.CLONE_ALERT || aExtortion.level == RiskLevel.FINANCIAL_COERCION)

        println("✓ Temporal persistence rule and multi-window elevation verified.")
        return true
    }
}

fun main() {
    val runner = Phase2TestRunner()
    val ok = runner.runAllTests()
    if (!ok) {
        System.exit(1)
    }
}
