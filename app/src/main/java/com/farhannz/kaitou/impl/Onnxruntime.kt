package com.farhannz.kaitou.impl

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.farhannz.kaitou.domain.InferenceResult
import com.farhannz.kaitou.domain.ModelInput
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import kotlin.math.exp

class OnnxModel(private val modelPath: String, private val useSentenceEmbedding: Boolean = true) {
    private var session: OrtSession? = null
    private var env: OrtEnvironment? = null

    init {
        initialize()
    }

    private fun initialize() {
        try {
            env = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions()
            try {
                sessionOptions.addNnapi()
            } catch (e: Throwable) {
                println("Warning NNAPI isn't available, fallingback to CPU Only, ${e.message}")
            }
            session = env!!.createSession(modelPath, sessionOptions)
        } catch (e: Exception) {
            throw RuntimeException("Failed to load model: $modelPath", e)
        }
    }

    fun run(input: ModelInput, batchSize: Long = 1): InferenceResult {
        val env = env ?: error("env is null")
        val session = session ?: error("session is null")
//        val inputOffsets = longArrayOf(0L)
        // 1. Both tensors must be int64 (LongBuffer)
        val inputIds = LongBuffer.wrap(input.data)
        val masks = LongBuffer.wrap(input.mask)
        val seqLen = input.data.size.toLong() / batchSize
        require(input.data.size.toLong() == batchSize * seqLen) { "Input data size does not match batchSize * seqLen" }
        require(input.mask.size == input.data.size) { "Mask size does not match input data size" }
        // 2. Correct shapes
        val idsShape = longArrayOf(batchSize, seqLen)
        val masksShape = longArrayOf(batchSize, seqLen)

        // 3. Create two tensors
        val idsTensor = OnnxTensor.createTensor(env, inputIds, idsShape)
        val masksTensor = OnnxTensor.createTensor(env, masks, masksShape)

        try {
            val requestOutput: Set<String> = if (useSentenceEmbedding) {
                setOf("sentence_embedding")
            } else {
                setOf("logits")
            }
            val results = session.run(
                mapOf(
                    "input_ids" to idsTensor,
                    "attention_mask" to masksTensor
                ),
                requestOutput
            )


            println(results.joinToString("\n"))
            val output = results.use { it[0].value as Array<FloatArray> }
            val flattened = FloatArray(output.size * output[0].size)
            output.forEachIndexed { index, logits ->
                flattened[index] = logits[0]
            }
            results.close()
            return InferenceResult(flattened)   // batch=1
        } finally {
            idsTensor.close()
            masksTensor.close()
        }
    }

    fun sigmoid(logits: Float): Float {
        return 1.0f / (1.0f + exp(-logits))
    }

    fun close() {
        session?.close()
        env?.close()
    }

    protected fun finalize() {
        close()
    }
}