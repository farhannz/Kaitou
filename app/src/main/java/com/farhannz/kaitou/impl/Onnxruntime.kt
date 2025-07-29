package com.farhannz.kaitou.impl

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.farhannz.kaitou.domain.InferenceResult
import com.farhannz.kaitou.domain.ModelInput
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer

class OnnxModel(private val modelPath: String) {
    private var session: OrtSession? = null
    private var env: OrtEnvironment? = null

    init {
        initialize()
    }

    private fun initialize() {
        try {
            env = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions()
            session = env!!.createSession(modelPath, sessionOptions)
        } catch (e: Exception) {
            throw RuntimeException("Failed to load model: $modelPath", e)
        }
    }

    fun run(input: ModelInput): InferenceResult {
        val env = env ?: error("env is null")
        val session = session ?: error("session is null")
        val inputOffsets = longArrayOf(0L)
        // 1. Both tensors must be int64 (LongBuffer)
        val inputIds = LongBuffer.wrap(input.data)            // [num_tokens]
        val offsets = LongBuffer.wrap(inputOffsets)         // [batch_size]

        // 2. Correct shapes
        val idsShape = longArrayOf(input.data.size.toLong())
        val offShape = longArrayOf(inputOffsets.size.toLong())

        // 3. Create two tensors
        val idsTensor = OnnxTensor.createTensor(env, inputIds, idsShape)
        val offTensor = OnnxTensor.createTensor(env, offsets, offShape)

        try {
            val results = session.run(
                mapOf(
                    "input_ids" to idsTensor,
                    "offsets" to offTensor
                ),
                setOf("embeddings")
            )

            val output = results.use { it[0].value as Array<*> }
            return InferenceResult(output[0] as FloatArray)   // batch=1
        } finally {
            idsTensor.close()
            offTensor.close()
        }
    }

    fun close() {
        session?.close()
        env?.close()
    }

    protected fun finalize() {
        close()
    }
}