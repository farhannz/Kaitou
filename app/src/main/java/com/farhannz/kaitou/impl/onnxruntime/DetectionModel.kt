package com.farhannz.kaitou.impl.onnxruntime

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.farhannz.kaitou.domain.DetectionResult
import com.farhannz.kaitou.domain.Group
import com.farhannz.kaitou.domain.GroupedResult
import com.farhannz.kaitou.domain.OnnxModel
import com.farhannz.kaitou.domain.Point as DomainPoint
import org.opencv.core.Point as CvPoint
import com.farhannz.kaitou.impl.utils.DBPostProcess
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.FloatBuffer
import kotlin.math.min

class DetectionModel(private val modelPath: String) : OnnxModel<Mat, GroupedResult> {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val postprocess = DBPostProcess(boxThresh = 0.6, thresh = 0.25, unclipRatio = 2.0)
    private val inputShape = intArrayOf(3, 960, 960)

    init {
        val sessionOptions = OrtSession.SessionOptions()
        println(OrtEnvironment.getAvailableProviders())
        applyHardwareAcceleration(sessionOptions)
        session = env.createSession(modelPath, sessionOptions)
    }

    fun letterboxMat(
        mat: Mat,
        targetWidth: Int = 960,
        targetHeight: Int = 960,
        padColor: Scalar = Scalar(255.0, 255.0, 255.0)  // White in BGR
    ): Pair<Mat, DoubleArray> {
        val start = System.nanoTime()

        val scale = min(
            targetWidth.toDouble() / mat.cols(),
            targetHeight.toDouble() / mat.rows()
        )

        val newW = (mat.cols() * scale).toInt()
        val newH = (mat.rows() * scale).toInt()

        // Resize
        val resized = Mat()
        Imgproc.resize(
            mat, resized, Size(newW.toDouble(), newH.toDouble()),
            0.0, 0.0, Imgproc.INTER_LINEAR
        )

        // Create padded mat with target size
        val padded = Mat(targetHeight, targetWidth, mat.type(), padColor)

        // Copy resized image to center using ROI
        val padX = (targetWidth - newW) / 2
        val padY = (targetHeight - newH) / 2

        val roi = Rect(padX, padY, newW, newH)
        resized.copyTo(padded.submat(roi))

        // Cleanup
        resized.release()

        val padXf = padX.toDouble()
        val padYf = padY.toDouble()

        val elapsed = (System.nanoTime() - start) / 1_000_000.0
        println(
            "[latency] letterboxMat: %.2f ms, ${mat.cols()}x${mat.rows()} -> ${targetWidth}x${targetHeight}".format(
                elapsed
            )
        )
        Imgproc.cvtColor(padded, padded, Imgproc.COLOR_BGR2RGB)
        // Scale + pad info, useful for restoring box coords later
        return padded to doubleArrayOf(
            mat.width().toDouble(),
            mat.height().toDouble(),
            scale,
            padXf,
            padYf
        )
    }

    fun preprocess(
        image: Mat,
    ): Pair<Mat, DoubleArray> {
        return letterboxMat(image, inputShape[1], inputShape[2])
    }

    override fun predict(input: Mat): GroupedResult {
        val start = System.nanoTime()
        val (preprocessed, resizedInfo) = preprocess(input)
        val c = inputShape[0]
        val h = inputShape[1]
        val w = inputShape[2]

        val floatInput = if (preprocessed.type() != CvType.CV_32FC3) {
            val converted = Mat()
            preprocessed.convertTo(converted, CvType.CV_32F, 1.0 / 255.0)
            converted
        } else {
            preprocessed
        }

        val channels = mutableListOf<Mat>()
        Core.split(floatInput, channels)

        val floatBuffer = FloatBuffer.allocate(1 * c * h * w)
        for (channel in channels) {
            val channelData = FloatArray(h * w)
            channel.get(0, 0, channelData)
            floatBuffer.put(channelData)
        }

        if (floatInput !== preprocessed) {
            floatInput.release()
        }
        channels.forEach { it.release() }

        floatBuffer.rewind()

        val shape = longArrayOf(1, c.toLong(), h.toLong(), w.toLong())
        val inputTensor = OnnxTensor.createTensor(env, floatBuffer, shape)

        val inputName = session.inputNames.first()
        val results = session.run(mapOf(inputName to inputTensor))
        val resultMat = onnxTensorToMat(results.get(0) as OnnxTensor)
        val postprocessed = postprocess.process(resultMat, true, resizedInfo)

        // Convert from data.models.GroupedResult to domain.GroupedResult
        val domainDetections = DetectionResult(
            boxes = postprocessed.detections.boxes.map { box ->
                box.map { pt -> DomainPoint(pt.x.toFloat(), pt.y.toFloat()) }
            },
            scores = postprocessed.detections.scores.map { it.toFloat() }
        )

        val domainGrouped = postprocessed.grouped.map { (region, indices) ->
            Group(
                region = region.map { pt -> DomainPoint(pt.x.toFloat(), pt.y.toFloat()) },
                memberBoxIndices = indices
            )
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000.0
        println(
            "[latency] end2end: %.2f ms".format(
                elapsed
            )
        )
        return GroupedResult(domainDetections, domainGrouped)
    }

    override fun close() {
        session.close()
        env.close()
    }
}