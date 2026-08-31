package com.farhannz.kaitou.impl.onnxruntime

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.farhannz.kaitou.domain.OnnxModel
import com.farhannz.kaitou.helpers.Logger
import com.farhannz.kaitou.impl.utils.CTCLabelDecoder
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.min

class RecognitionModel(
    private val modelPath: String, private val characterList: List<String>
) : OnnxModel<Mat, String> {
    private val logger = Logger(RecognitionModel::class.simpleName!!)
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val labelDecoder: CTCLabelDecoder
    private val inputShape = longArrayOf(1, 3, 48, 320)

    init {
        val sessionOptions = OrtSession.SessionOptions()
        applyHardwareAcceleration(sessionOptions)
        session = env.createSession(modelPath, sessionOptions)
        labelDecoder = CTCLabelDecoder(characterList)
    }

    override fun predict(input: Mat): String {
        fun ms(from: Long) = (System.nanoTime() - from) / 1_000_000.0

        val start = System.nanoTime()
        val height = inputShape[2].toInt()
        val width = inputShape[3].toInt()
        val channels = inputShape[1].toInt()

        var t = System.nanoTime()
        val preprocessed = preprocess(input)
        val preprocessMs = ms(t)

        t = System.nanoTime()
        val batchData = FloatArray(channels * height * width)
        val tempData = FloatArray(channels)

        for (h in 0 until height) {
            for (w in 0 until width) {
                preprocessed.get(h, w, tempData)
                for (c in 0 until channels) {
                    batchData[c * height * width + h * width + w] = tempData[c]
                }
            }
        }
        preprocessed.release()
        val toChwMs = ms(t)

        t = System.nanoTime()
        val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(batchData), inputShape)
        val tensorMs = ms(t)

        val inputName = session.inputNames.first()
        t = System.nanoTime()
        val results = session.run(mapOf(inputName to inputTensor))
        val inferMs = ms(t)

        t = System.nanoTime()
        val output = results[0].value as Array<Array<FloatArray>>
        results.close()
        val outputMs = ms(t)

        t = System.nanoTime()
        val text = decode(output[0])
        val decodeMs = ms(t)
        val total = ms(start)
        logger.INFO(
            "[latency] rec: preprocess=$preprocessMs toCHW=$toChwMs tensor=$tensorMs infer=$inferMs " +
                    "output=$outputMs decode=$decodeMs | total=$total ms"
        )
        return text
    }

    fun predictBatch(inputs: List<Mat>): List<String> {
        return inputs.map { predict(it) }
    }

    private fun preprocess(image: Mat): Mat {
        val maxRatio = min(
            image.width().toFloat() / image.height().toFloat(),
            inputShape[3].toFloat() / inputShape[2].toFloat()
        )
        return resizeAndNormalize(image, maxRatio.toDouble())
    }

    private fun resizeAndNormalize(img: Mat, maxWhRatio: Double): Mat {
        val h = img.rows()
        val w = img.cols()
        val ratio = w.toDouble() / h
        val imgH = inputShape[2].toInt()
        val imgW = min(ceil(imgH * ratio).toInt(), inputShape[3].toInt())

        val resized = Mat()
        val normalized = Mat()

        try {
            Imgproc.resize(img, resized, Size(imgW.toDouble(), imgH.toDouble()))
            resized.convertTo(normalized, CvType.CV_32FC3, 1.0 / 255)
            Core.subtract(normalized, Scalar(0.5, 0.5, 0.5), normalized)
            Core.divide(normalized, Scalar(0.5, 0.5, 0.5), normalized)

            val paddingIm = Mat.zeros(imgH, inputShape[3].toInt(), CvType.CV_32FC3)
            val roi = Rect(0, 0, imgW, imgH)
            normalized.copyTo(paddingIm.submat(roi))

            return paddingIm
        } finally {
            resized.release()
            normalized.release()
        }
    }

    private fun decode(output: Array<FloatArray>): String {
        val timeSteps = output.size
        val numClasses = if (timeSteps > 0) output[0].size else 0

        if (timeSteps == 0 || numClasses == 0) {
            return ""
        }

        val predIndices = IntArray(timeSteps)
        val predProbs = FloatArray(timeSteps)

        for (t in 0 until timeSteps) {
            val row = output[t]
            var maxIndex = 0
            var maxProb = row[0]

            for (c in 1 until numClasses) {
                val prob = row[c]
                if (prob > maxProb) {
                    maxProb = prob
                    maxIndex = c
                }
            }
            predIndices[t] = maxIndex
            predProbs[t] = maxProb
        }

        val decoded = labelDecoder.decode(listOf(predIndices), listOf(predProbs))
        return decoded.joinToString("") { it.first }
    }

    override fun close() {
        session.close()
        env.close()
    }
}