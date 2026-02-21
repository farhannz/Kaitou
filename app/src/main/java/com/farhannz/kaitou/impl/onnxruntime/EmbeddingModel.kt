package com.farhannz.kaitou.impl.onnxruntime

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.farhannz.kaitou.domain.EmbeddingResult
import com.farhannz.kaitou.domain.ModelInput
import com.farhannz.kaitou.domain.OnnxModel
import java.nio.LongBuffer

class EmbeddingModel(
    private val modelPath: String,
    private val useSentenceEmbedding: Boolean = true
) : OnnxModel<ModelInput, EmbeddingResult> {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val sessionOptions = OrtSession.SessionOptions()
        try {
            sessionOptions.addNnapi()
        } catch (e: Exception) {
            // NNAPI not available
        }
        session = env.createSession(modelPath, sessionOptions)
    }

    override fun predict(input: ModelInput): EmbeddingResult = predict(input, 1L)

    fun predict(input: ModelInput, batchSize: Long): EmbeddingResult {
        val inputIds = LongBuffer.wrap(input.data)
        val masks = LongBuffer.wrap(input.mask)
        val seqLen = input.data.size.toLong() / batchSize
        require(input.data.size.toLong() == batchSize * seqLen) { "Input data size does not match batchSize * seqLen" }
        require(input.mask.size == input.data.size) { "Mask size does not match input data size" }

        val idsShape = longArrayOf(batchSize, seqLen)
        val masksShape = longArrayOf(batchSize, seqLen)

        val idsTensor = OnnxTensor.createTensor(env, inputIds, idsShape)
        val masksTensor = OnnxTensor.createTensor(env, masks, masksShape)

        return try {
            val requestOutput = if (useSentenceEmbedding) {
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

            val output = results.use { it[0].value as Array<FloatArray> }
            val flattened = FloatArray(output.size * output[0].size)
            output.forEachIndexed { index, logits ->
                flattened[index] = logits[0]
            }
            
            EmbeddingResult(flattened)
        } finally {
            idsTensor.close()
            masksTensor.close()
        }
    }

    override fun close() {
        session.close()
        env.close()
    }
}