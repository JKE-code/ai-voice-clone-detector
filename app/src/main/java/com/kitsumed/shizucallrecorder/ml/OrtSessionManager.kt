package com.kitsumed.shizucallrecorder.ml

import android.content.Context
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.InputStream

/**
 * Manages ONNX Runtime mobile environment, thread allocation, and model session caching.
 */
class OrtSessionManager(private val context: Context) : AutoCloseable {

    val environment: OrtEnvironment by lazy {
        OrtEnvironment.getEnvironment()
    }

    private val sessionCache = mutableMapOf<String, OrtSession>()

    /**
     * Loads an ONNX model from the Android application assets directory.
     *
     * @param assetPath Relative path in assets, e.g. "models/voice_clone_detector.onnx".
     * @param numThreads Number of CPU execution threads (recommended: 2 for mobile).
     */
    @Synchronized
    fun getOrCreateSession(assetPath: String, numThreads: Int = 2): OrtSession {
        return sessionCache.getOrPut(assetPath) {
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(numThreads)
                setInterOpNumThreads(1)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }

            val modelBytes = loadAssetBytes(assetPath)
            environment.createSession(modelBytes, sessionOptions)
        }
    }

    private fun loadAssetBytes(assetPath: String): ByteArray {
        val inputStream: InputStream = context.assets.open(assetPath)
        return inputStream.use { it.readBytes() }
    }

    override fun close() {
        synchronized(this) {
            sessionCache.values.forEach { session ->
                runCatching { session.close() }
            }
            sessionCache.clear()
            runCatching { environment.close() }
        }
    }
}
