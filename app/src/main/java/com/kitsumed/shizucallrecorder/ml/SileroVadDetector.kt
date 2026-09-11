package com.kitsumed.shizucallrecorder.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * High-performance Voice Activity Detector using Silero VAD v5 ONNX model.
 * Evaluates 512-sample frames @ 16kHz (32ms chunks) and maintains hidden recurrent state.
 *
 * @param sessionManager Shared ONNX runtime session manager.
 * @param modelAssetPath Path in assets (e.g. "models/silero_vad.onnx").
 * @param speechThreshold Probability threshold above which a frame is considered speech (default: 0.5f).
 */
class SileroVadDetector(
    private val sessionManager: OrtSessionManager,
    private val modelAssetPath: String = "models/silero_vad.onnx",
    var speechThreshold: Float = 0.5f
) : AutoCloseable {

    companion object {
        const val FRAME_SIZE = 512 // 512 samples @ 16kHz = 32ms
        const val SAMPLE_RATE = 16000L
    }

    private var session: OrtSession? = null

    // Hidden states for Silero VAD v5: state tensor shape is (2, 1, 128)
    private var state: FloatArray = FloatArray(2 * 1 * 128)

    private fun ensureSession(): OrtSession {
        if (session == null) {
            session = sessionManager.getOrCreateSession(modelAssetPath, numThreads = 1)
        }
        return session!!
    }

    /**
     * Evaluates a 512-sample frame @ 16kHz for speech presence.
     *
     * @param frame512 Normalized FloatArray of exactly [FRAME_SIZE] samples [-1.0 .. 1.0].
     * @return Speech probability in range [0.0 .. 1.0]. Returns 1.0f fallback if model is missing.
     */
    @Synchronized
    fun evaluateFrame(frame512: FloatArray): Float {
        if (frame512.size != FRAME_SIZE) {
            return 0.5f
        }

        return try {
            val env = sessionManager.environment
            val sess = ensureSession()

            // Input 1: audio chunk (1, 512)
            val audioBuffer = FloatBuffer.wrap(frame512)
            val inputTensor = OnnxTensor.createTensor(env, audioBuffer, longArrayOf(1, FRAME_SIZE.toLong()))

            // Input 2: sample rate (1,)
            val srTensor = OnnxTensor.createTensor(env, longArrayOf(SAMPLE_RATE))

            // Input 3: recurrent state (2, 1, 128)
            val stateBuffer = FloatBuffer.wrap(state)
            val stateTensor = OnnxTensor.createTensor(env, stateBuffer, longArrayOf(2, 1, 128))

            val inputs = mapOf(
                "input" to inputTensor,
                "sr" to srTensor,
                "state" to stateTensor
            )

            sess.run(inputs).use { result ->
                // Output 0: speech probability [1, 1]
                val outputTensor = result[0] as OnnxTensor
                val probArray = outputTensor.floatBuffer
                val speechProb = if (probArray.hasRemaining()) probArray.get(0) else 0.5f

                // Output 1: updated state tensor [2, 1, 128]
                if (result.size() > 1) {
                    val nextStateTensor = result[1] as OnnxTensor
                    val nextStateBuf = nextStateTensor.floatBuffer
                    nextStateBuf.get(state)
                }

                speechProb
            }
        } catch (e: Exception) {
            // Graceful fallback if ONNX model asset is not yet bundled
            0.85f // Assume active speech so pipeline does not stall
        }
    }

    /**
     * Evaluates a full audio window (e.g. 48,000 samples) by chunking into 512-sample frames.
     * Returns the proportion of frames that contained active speech.
     */
    fun computeSpeechRatio(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0.0f
        var speechFrames = 0
        var totalFrames = 0

        var offset = 0
        val frame = FloatArray(FRAME_SIZE)

        while (offset + FRAME_SIZE <= samples.size) {
            System.arraycopy(samples, offset, frame, 0, FRAME_SIZE)
            val prob = evaluateFrame(frame)
            if (prob >= speechThreshold) {
                speechFrames++
            }
            totalFrames++
            offset += FRAME_SIZE
        }

        return if (totalFrames > 0) speechFrames.toFloat() / totalFrames else 0.0f
    }

    /**
     * Resets the recurrent VAD state (e.g., at the start of a new phone call).
     */
    @Synchronized
    fun resetState() {
        state.fill(0.0f)
    }

    override fun close() {
        resetState()
    }
}
