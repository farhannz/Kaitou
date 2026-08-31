package com.farhannz.kaitou.domain

import ai.onnxruntime.OnnxTensor
import org.opencv.core.CvType
import org.opencv.core.Mat

interface OnnxModel<TInput, TOutput> {
    fun predict(input: TInput): TOutput
    fun close()

    @Throws(Exception::class)
    fun onnxTensorToMat(tensor: OnnxTensor): Mat {
        val info = tensor.getInfo()
        val shape = info.getShape()

        val height = shape[2].toInt()
        val width = shape[3].toInt()


        // Get raw float buffer
        val floatBuffer = tensor.getFloatBuffer()
        val array = FloatArray(floatBuffer.remaining())
        floatBuffer.get(array)


        // Create Mat
        val mat = Mat(height, width, CvType.CV_32FC1)
        mat.put(0, 0, array)

        return mat
    }
}

data class EmbeddingResult(val embedding: FloatArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmbeddingResult

        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        return embedding.contentHashCode()
    }
}

data class ModelInputBatch(val data: List<LongArray>, val mask: List<LongArray>)
data class ModelInput(val data: LongArray, val mask: LongArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ModelInput

        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }
}