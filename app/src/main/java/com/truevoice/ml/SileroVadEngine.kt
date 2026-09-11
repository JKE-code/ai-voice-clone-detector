/*
 * True Voice: Real-Time On-Device AI Voice Clone & Scam Defense
 * Silero Voice Activity Detection (VAD) Engine
 */

package com.truevoice.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.kitsumed.shizucallrecorder.utils.AppLogger
import java.io.Closeable
import java.nio.FloatBuffer

/**
 * Silero VAD v5 on-device inference engine.
 *
 * Evaluates 16kHz mono audio chunks (512 samples / 32ms) to detect human voice presence.
 * Acts as an energy/compute gate: if no speech is present (silence, ringtones, hold music),
 * downstream synthetic voice anti-spoofing is skipped to conserve CPU and battery.
 *
 * Retains internal recurrent state tensors (h and c) across chunks for temporal continuity.
 */
class SileroVadEngine(
    private val context: Context,
    private val modelAssetPath: String = "models/silero_vad.onnx",
    var speechThreshold: Float = 0.50f
) : Closeable {

    companion object {
        const val SAMPLE_RATE = 16000L
        const val WINDOW_SIZE_SAMPLES = 512 // 32ms at 16kHz
        private const val STATE_SHAPE_0 = 2L
        private const val STATE_SHAPE_1 = 1L
        private const val STATE_SHAPE_2 = 128L
    }

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    // Hidden states for Silero v5 LSTM
    private var hState: Array<Array<FloatArray>> = Array(2) { Array(1) { FloatArray(128) } }
    private var cState: Array<Array<FloatArray>> = Array(2) { Array(1) { FloatArray(128) } }

    private var isInitialized = false

    /**
     * Initializes the ONNX Runtime session with the bundled Silero VAD model.
     */
    @Synchronized
    fun initialize(): Boolean {
        if (isInitialized) return true
        return try {
            ortEnv = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(1)
                setInterOpNumThreads(1)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }

            val modelBytes = context.assets.open(modelAssetPath).use { it.readBytes() }
            ortSession = ortEnv?.createSession(modelBytes, sessionOptions)
            resetState()
            isInitialized = true
            AppLogger.i("[TrueVoice VAD] SileroVadEngine successfully initialized from assets: $modelAssetPath")
            true
        } catch (e: Exception) {
            AppLogger.w("[TrueVoice VAD] Could not load ONNX model ($modelAssetPath): ${e.message}. Falling back to acoustic energy VAD.")
            isInitialized = false
            false
        }
    }

    /**
     * Evaluates a 512-sample (32ms at 16kHz) mono float array.
     * Returns speech probability between 0.0f and 1.0f.
     */
    @Synchronized
    fun predictSpeechProbability(samples: FloatArray): Float {
        if (!isInitialized || ortSession == null || ortEnv == null) {
            // Fallback: estimate speech via short-term RMS energy
            return fallbackEnergyVad(samples)
        }

        // Must be exactly 512 samples
        val inputSamples = if (samples.size == WINDOW_SIZE_SAMPLES) {
            samples
        } else if (samples.size > WINDOW_SIZE_SAMPLES) {
            samples.copyOfRange(0, WINDOW_SIZE_SAMPLES)
        } else {
            FloatArray(WINDOW_SIZE_SAMPLES).also { System.arraycopy(samples, 0, it, 0, samples.size) }
        }

        val env = ortEnv ?: return fallbackEnergyVad(samples)
        val session = ortSession ?: return fallbackEnergyVad(samples)

        return try {
            // Input tensor shape: [1, 512]
            val inputTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(inputSamples),
                longArrayOf(1, WINDOW_SIZE_SAMPLES.toLong())
            )

            // Sample rate tensor shape: [1]
            val srTensor = OnnxTensor.createTensor(
                env,
                longArrayOf(SAMPLE_RATE)
            )

            // Recurrent state tensors: h and c shape [2, 1, 128]
            val hTensor = OnnxTensor.createTensor(env, hState)
            val cTensor = OnnxTensor.createTensor(env, cState)

            val inputs = mapOf(
                "input" to inputTensor,
                "sr" to srTensor,
                "h" to hTensor,
                "c" to cTensor
            )

            val results = session.run(inputs)

            // Output 0: speech probability [1, 1]
            @Suppress("UNCHECKED_CAST")
            val outputVal = results.get(0).value as Array<FloatArray>
            val probability = outputVal[0][0]

            // Update recurrent state if output tensors provided by model
            if (results.size() >= 3) {
                @Suppress("UNCHECKED_CAST")
                val newH = results.get(1).value as? Array<Array<FloatArray>>
                @Suppress("UNCHECKED_CAST")
                val newC = results.get(2).value as? Array<Array<FloatArray>>
                if (newH != null && newC != null) {
                    hState = newH
                    cState = newC
                }
            }

            // Cleanup OnnxTensors
            inputTensor.close()
            srTensor.close()
            hTensor.close()
            cTensor.close()
            results.close()

            probability
        } catch (e: Exception) {
            AppLogger.w("[TrueVoice VAD] Inference error: ${e.message}")
            fallbackEnergyVad(samples)
        }
    }

    /**
     * Checks if speech is present in the audio window.
     */
    fun isSpeechPresent(samples: FloatArray): Boolean {
        return predictSpeechProbability(samples) >= speechThreshold
    }

    /**
     * Resets the LSTM hidden states (e.g. at the start of a call).
     */
    @Synchronized
    fun resetState() {
        for (i in 0 until 2) {
            for (j in 0 until 1) {
                hState[i][j].fill(0.0f)
                cState[i][j].fill(0.0f)
            }
        }
    }

    /**
     * Energy-based fallback in case the ONNX asset is absent or loading.
     */
    private fun fallbackEnergyVad(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0.0f
        var sumSquares = 0.0
        for (s in samples) {
            sumSquares += (s * s)
        }
        val rms = Math.sqrt(sumSquares / samples.size).toFloat()
        // Approximate voice mapping: RMS 0.01 -> ~0.2, RMS 0.05 -> ~0.7, RMS > 0.1 -> 1.0
        return (rms * 10.0f).coerceIn(0.0f, 1.0f)
    }

    @Synchronized
    override fun close() {
        try {
            ortSession?.close()
            ortSession = null
            ortEnv?.close()
            ortEnv = null
        } catch (ignored: Exception) {}
        isInitialized = false
        AppLogger.d("[TrueVoice VAD] SileroVadEngine closed")
    }
}
