package com.farhannz.kaitou.paddle

//import org.opencv.core.*
import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.Tensor
import com.farhannz.kaitou.data.models.GroupedResult
import com.farhannz.kaitou.domain.OcrResult
import com.farhannz.kaitou.domain.RawImage
import com.farhannz.kaitou.helpers.Logger
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

typealias CvSize = org.opencv.core.Size

abstract class BasePredictor {
    open lateinit var modelName: String
    open lateinit var folderPath: String
    open lateinit var predictor: PaddlePredictor
    open lateinit var config: MobileConfig

    init {
        System.loadLibrary("paddle_lite_jni")
    }

    open fun initialize(context: Context, dirPath: String, fileName: String) {
        // Add this before using any Paddle Lite functions
        folderPath = dirPath
        modelName = fileName
        val absolutePath = copyAssetToCache(context, folderPath, modelName)
        config = MobileConfig().apply {
            modelFromFile = absolutePath
            threads = 1
        }
        predictor = PaddlePredictor.createPaddlePredictor(config)
    }

    fun copyAssetToCache(context: Context, assetFolder: String = "", assetName: String): String {
        val assetSubPath = "${assetFolder}/$assetName" // use relative asset path
        val outDir = File(context.cacheDir, "paddle")
        if (!outDir.exists()) outDir.mkdirs()

        val outFile = File(outDir, assetName)
        if (!outFile.exists()) {
            context.assets.open(assetSubPath).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outFile.absolutePath
    }

    abstract fun infer(inputImage: RawImage): OcrResult
}


class RecognitionPredictor : BasePredictor() {
    private val LOG_TAG = RecognitionPredictor::class.simpleName
    private val logger = Logger(LOG_TAG!!)
    private lateinit var labelDecoder: CTCLabelDecoder
    private val input_shape = longArrayOf(3, 48, 320)
    private var ratioHW: FloatArray = floatArrayOf(1f, 1f)
    private val maxImageWidth = 3200

    override fun infer(inputImage: RawImage): OcrResult {
        return OcrResult.Error("NOT YET IMPLEMENTED")
    }

    override fun initialize(context: Context, dirPath: String, fileName: String) {
        super.initialize(context, dirPath, fileName)
        labelDecoder = CTCLabelDecoder(context.assets.open("dict.txt").bufferedReader().readLines())
    }

    fun runBatchInference(inputImages: List<Bitmap>): List<String> {
        if (inputImages.isEmpty()) return emptyList()

        val e2e = Date()
        var start = Date()

        // Preprocess all images
        val preprocessedImages = inputImages.map { preprocess(it) }
        val batchSize = inputImages.size
        val height = preprocessedImages[0].rows()
        val width = preprocessedImages[0].cols()
        val channels = preprocessedImages[0].channels()

        // Setup batch input tensor
        val inputTensor = predictor.getInput(0)
        inputTensor.resize(longArrayOf(batchSize.toLong(), 3, height.toLong(), width.toLong()))

        // Convert all images to CHW format
        val batchData = FloatArray(batchSize * 3 * height * width)
        val tempData = FloatArray(channels)

        for (batchIdx in preprocessedImages.indices) {
            val resized = preprocessedImages[batchIdx]
            val batchOffset = batchIdx * 3 * height * width

            for (h in 0 until height) {
                for (w in 0 until width) {
                    resized.get(h, w, tempData)
                    for (c in 0 until channels) {
                        batchData[batchOffset + c * height * width + h * width + w] = tempData[c]
                    }
                }
            }
            resized.release()
        }

        inputTensor.setData(batchData)

        // Run batch inference
        start = Date()
        val success = predictor.run()
        if (!success) return List(batchSize) { "" }

        // Postprocess batch results
        start = Date()
        val outputTensor = predictor.getOutput(0)
        val texts = postprocessBatch(outputTensor, batchSize)
        return texts
    }

    fun runInference(inputImage: Bitmap): String {
        return runBatchInference(listOf(inputImage))[0]
    }

    private fun postprocessBatch(preds: Tensor, batchSize: Int): List<String> {
        val shape = preds.shape()
        val timeSteps = shape[1].toInt()
        val numClasses = shape[2].toInt()
        val rawOutput = preds.floatData

        val results = mutableListOf<String>()

        // Pre-allocate arrays for better memory performance
        val predIndices = IntArray(timeSteps)
        val predProbs = FloatArray(timeSteps)

        for (batchIdx in 0 until batchSize) {
            val batchOffset = batchIdx * timeSteps * numClasses

            // Optimized argmax - single pass through time steps
            for (t in 0 until timeSteps) {
                val timeOffset = batchOffset + t * numClasses
                var maxIndex = 0
                var maxProb = rawOutput[timeOffset]

                // Unrolled loop for better performance
                for (c in 1 until numClasses) {
                    val prob = rawOutput[timeOffset + c]
                    if (prob > maxProb) {
                        maxProb = prob
                        maxIndex = c
                    }
                }

                predIndices[t] = maxIndex
                predProbs[t] = maxProb
            }

            val decoded = labelDecoder.decode(listOf(predIndices), listOf(predProbs))
            results.add(decoded.joinToString("") { it.first })
        }

        return results
    }

    fun preprocess(bitmap: Bitmap): Mat {
        val inputMat = Mat()
        try {
            Utils.bitmapToMat(bitmap, inputMat)

            // Convert to RGB as Paddle expects
            Imgproc.cvtColor(inputMat, inputMat, Imgproc.COLOR_BGR2RGB)

            val maxRatio = max(
                bitmap.width.toFloat() / bitmap.height.toFloat(),
                input_shape[1].toFloat() / input_shape[2].toFloat()
            )
            return resizeAndNormalizeImage(inputMat, maxRatio.toDouble())
        } finally {
            inputMat.release()
            bitmap.recycle()
        }
    }

    fun postprocess(preds: Tensor): String {
        val outputData = preds.floatData
        val shape = preds.shape()
        val timeSteps = shape[1].toInt()
        val numClasses = shape[2].toInt()
        val rawOutput = preds.floatData

        val predIndices = IntArray(timeSteps)
        val predProbs = FloatArray(timeSteps)

        for (t in 0 until timeSteps) {
            var maxIndex = 0
            var maxProb = rawOutput[t * numClasses]

            for (c in 1 until numClasses) {
                val prob = rawOutput[t * numClasses + c]
                if (prob > maxProb) {
                    maxProb = prob
                    maxIndex = c
                }
            }

            predIndices[t] = maxIndex
            predProbs[t] = maxProb
        }
        val results = labelDecoder.decode(listOf(predIndices), listOf(predProbs))
        return results.joinToString("") { it.first }
    }

    fun resizeAndNormalizeImage(img: Mat, maxWhRatio: Double): Mat {
        val resizedImage = Mat()
        val normalizedImage = Mat()
        try {
            val imgC = input_shape[0].toInt()
            val imgH = input_shape[1].toInt()
            val imgW = input_shape[2].toInt()

            // Resize image to fit height
            val h = img.rows()
            val w = img.cols()
            val ratio = w.toDouble() / h
            val resizedW = min(ceil(imgH * ratio).toInt(), imgW)

            Imgproc.resize(img, resizedImage, CvSize(resizedW.toDouble(), imgH.toDouble()))

            // Normalize
            resizedImage.convertTo(normalizedImage, CvType.CV_32FC3, 1.0 / 255)
            Core.subtract(normalizedImage, Scalar(0.5, 0.5, 0.5), normalizedImage)
            Core.divide(normalizedImage, Scalar(0.5, 0.5, 0.5), normalizedImage)

            // Pad to model width
            val paddingIm = Mat.zeros(imgH, imgW, CvType.CV_32FC3)
            val roi = paddingIm.submat(Rect(0, 0, resizedW, imgH))
            normalizedImage.copyTo(roi)

            return paddingIm
        } finally {
            resizedImage.release()
            normalizedImage.release()
        }
    }
}

// Reimplementation of c++ from
// https://github.com/PaddlePaddle/Paddle-Lite-Demo/blob/develop/ocr/android/app/cxx/ppocr_demo/app/src/main/cpp/det_process.cc

class DetectionPredictor : BasePredictor() {
    private val LOG_TAG = DetectionPredictor::class.simpleName
    private val logger = Logger(LOG_TAG!!)

    private var inputShape = longArrayOf(3, 960, 960)
    private val postprocessor = DBPostProcess(boxThresh = 0.6, thresh = 0.25, unclipRatio = 2.0)
    private var resizedInfo: FloatArray = floatArrayOf(1f, 1f, 1f)
    override fun infer(inputImage: RawImage): OcrResult {
        return OcrResult.Error("NOT YET IMPLEMENTED")
    }

    fun runInference(inputImage: Bitmap): GroupedResult {
        val end2end = Date()
        logger.INFO("Running inference")
        var start = Date()
        val (preprocessed, resized) = preprocess(inputImage, inputShape)
        var end = Date()
        var inferenceTime = (end.time - start.time)
//        logger.INFO("[stat] Preprocessing time $inferenceTime ms");

        val inputTensor = predictor.getInput(0)
        inputTensor.resize(longArrayOf(1, 3, resized.width.toLong(), resized.height.toLong()))
        val inputShape = inputTensor.shape()  // Returns LongArray
//        logger.DEBUG("Input shape: ${inputShape.joinToString(",")}")

        val requiredSize = inputShape.fold(1L, Long::times).toInt()
        if (preprocessed.size != requiredSize) {
            logger.ERROR("Data size mismatch! Expected $requiredSize, got ${preprocessed.size}")
            throw IllegalStateException("Data size mismatch")
        }
        try {
            if (preprocessed.isEmpty()) throw IllegalArgumentException("Empty input data")
            inputTensor.setData(preprocessed)
        } catch (e: Throwable) {
            logger.ERROR("Tensor data setting failed: ${e.stackTraceToString()}")
            throw e
        }

        start = Date()
        val success = predictor.run()
        if (!success) {
            logger.ERROR("Failed while doing an inference")
            throw Throwable("Failed while doing an inference")
        }
        end = Date()
        inferenceTime = (end.time - start.time)
        logger.INFO("[stat] Inference time $inferenceTime ms");

        val detConfig = mapOf(
            "det_db_thresh" to 0.3
        )
        start = Date()
        val postprocessed = postprocess(inputImage, detConfig, true)
        end = Date()
        inferenceTime = (end.time - start.time)
//        logger.INFO("[stat] Postprocessing time $inferenceTime ms");
//        logger.INFO("[stat] End to end time ${Date().time - end2end.time} ms");

//        ONLY FOR DEBUGGING
//        Uncomment this to visualize the cropped polygons result
//        if (BuildConfig.DEBUG) {
//            postprocessed.boxes.forEachIndexed { index,  box ->
//                val mat = Mat()
//                Utils.bitmapToMat(inputImage, mat)
//                val cropped = cropFromBox(mat, box, true)
//                val isVertical = (cropped.height().toFloat() / cropped.width().toFloat()) > 1.5f
//                if (isVertical) {
//                    Core.rotate(cropped, cropped, Core.ROTATE_90_COUNTERCLOCKWISE)
//                }
//                val filePath = File(Environment.getExternalStorageDirectory(), "Download/PPOCR/cropped_$index.png").absolutePath
//                Imgcodecs.imwrite(filePath, cropped)
//            }
//        }
        return postprocessed
    }

    fun preprocess(bitmap: Bitmap, inputShape: LongArray): Pair<FloatArray, Size> {
//        var file = File(Environment.getExternalStorageDirectory(), "Download/ocr_input_preview.png")
//        saveBitmapToFileDirectly(bitmap, file.absolutePath)
//        logger.DEBUG("Before rezie Bitmap WxH: ${bitmap.width}x${bitmap.height}")
        val (C, H, W) = inputShape
        val (resized, info) = letterboxBitmap(bitmap, 960, 960)
        resizedInfo = info
        logger.DEBUG("ScaleRatio: ${resizedInfo.joinToString(",")} → Resized WxH: ${resized.width} x ${resized.height}")

        // Convert to float array with normalization
        val floatArray = bitmapToFloatArray(resized, normalize = true)


        return Pair(floatArray, Size(resized.width, resized.height))
    }

    fun postprocess(
        originalBitmap: Bitmap,
        config: Map<String, Double>,
        useDilate: Boolean = false
    ): GroupedResult {
        val outputTensor = predictor.getOutput(0)
        val width = outputTensor.shape()[2]
        val height = outputTensor.shape()[3]
        val outputMat = Mat(height.toInt(), width.toInt(), CvType.CV_32F).apply {
            put(0, 0, outputTensor.floatData)
        }
        logger.DEBUG("outputTensor : (HxW) $height x $width")
        logger.DEBUG("outputMat : (HxW) ${outputMat.height()} x ${outputMat.width()}")
        val (scale, padX, padY) = resizedInfo
        val result = postprocessor.process(
            outputMat,
            useDilate,
            doubleArrayOf(
                originalBitmap.width.toDouble(),
                originalBitmap.height.toDouble(),
                scale.toDouble(),
                padX.toDouble(),
                padY.toDouble()
            )
        )

        logger.DEBUG("Post process finished")
        logger.DEBUG("Results : $result.size")
        return result
    }
}


