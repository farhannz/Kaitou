package com.farhannz.kaitou.paddle

import android.graphics.Rect
import clipper2.core.Path64
import clipper2.core.Paths64
import clipper2.core.Point64
import clipper2.offset.ClipperOffset
import clipper2.offset.EndType
import clipper2.offset.JoinType
import com.farhannz.kaitou.data.models.DetectionResult
import com.farhannz.kaitou.data.models.GroupedResult
import com.farhannz.kaitou.helpers.Logger
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import org.opencv.utils.Converters
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round




// Reimplementation of PaddleX's DBPostProcess
// https://github.com/PaddlePaddle/PaddleX/blob/30e67135ac05299cd63e0eb389ffecadad042f7f/paddlex/inference/models/text_detection/processors.py#L276
class DBPostProcess(
    private val thresh: Double = 0.3,
    private val boxThresh: Double = 0.7,
    private val maxCandidates: Int = 1000,
    private val unclipRatio: Double = 2.0,
    private val scoreMode: String = "fast",
    private val boxType: String = "quad",
    private val groupedBoxes: Boolean = true
) {
    private val LOG_TAG = DBPostProcess::class.simpleName
    private val logger = Logger(LOG_TAG!!)
    private val minSize: Int = 3

    init {
        require(scoreMode in listOf("slow", "fast")) { "Score mode must be 'slow' or 'fast'" }
        require(boxType in listOf("quad", "poly")) { "Box type must be 'quad' or 'poly'" }
    }


    // Compute the bounding rectangle (AABB) for a list of points
    fun getBoundingRect(points: List<Point>): Rect {
        if (points.isEmpty()) return Rect(0, 0, 0, 0)
        val minX = points.minOf { it.x }.toInt()
        val maxX = points.maxOf { it.x }.toInt()
        val minY = points.minOf { it.y }.toInt()
        val maxY = points.maxOf { it.y }.toInt()
        return Rect(minX, minY, maxX, maxY)
    }
    fun getBoxFromRect(rect: Rect): List<Point> {
        return listOf(
            Point(rect.left.toDouble(), rect.top.toDouble()),   // top-left
            Point(rect.right.toDouble(), rect.top.toDouble()),  // top-right
            Point(rect.right.toDouble(), rect.bottom.toDouble()), // bottom-right
            Point(rect.left.toDouble(), rect.bottom.toDouble())  // bottom-left
        )
    }

    // Check if two rectangles overlap
    fun doRectanglesOverlap(rect1: Rect, rect2: Rect): Boolean {
        return rect1.left < rect2.right &&
                rect2.left < rect1.right &&
                rect1.top < rect2.bottom &&
                rect2.top < rect1.bottom
    }

    // Group overlapping boxes using a graph-based approach
    fun groupOverlappingBoxes(boxes: List<List<Point>>): List<List<Point>> {
        val n = boxes.size
        if (n == 0) return emptyList()

        val adjList = List(n) { mutableListOf<Int>() }
        val boundingRects = boxes.map { getBoundingRect(it) }

        for (i in 0 until n) {
            for (j in i + 1 until n) {
                if (doRectanglesOverlap(boundingRects[i], boundingRects[j])) {
                    adjList[i].add(j)
                    adjList[j].add(i)
                }
            }
        }

        val visited = BooleanArray(n)
        val groups = mutableListOf<List<Point>>()

        fun dfs(node: Int, currentPoints: MutableList<Point>) {
            visited[node] = true
            currentPoints.addAll(boxes[node])
            for (neighbor in adjList[node]) {
                if (!visited[neighbor]) {
                    dfs(neighbor, currentPoints)
                }
            }
        }

        for (i in 0 until n) {
            if (!visited[i]) {
                val currentPoints = mutableListOf<Point>()
                dfs(i, currentPoints)

                val rect = getBoundingRect(currentPoints)
                val mergedBox = getBoxFromRect(rect)
                groups.add(mergedBox)
            }
        }

        return groups
    }

    fun process(pred: Mat, useDilation : Boolean,  imgShape: DoubleArray): GroupedResult {
        val minMax = Core.minMaxLoc(pred)
        logger.DEBUG("Min: ${minMax.minVal}, Max: ${minMax.maxVal}")
        val (srcH, srcW, ratioH, ratioW) = imgShape
        val segmentation = Mat()
        Core.compare(pred, Scalar(thresh), segmentation, Core.CMP_GT)
        val mask = if (useDilation) {
            val dilated = Mat()
            val dilationKernel = Mat.ones(2, 2, CvType.CV_8UC1)
            Imgproc.dilate(segmentation, dilated, dilationKernel)
            dilated
        } else {
            segmentation
        }

//        Uncomment this to visualize the segmentation mask
//        logger.DEBUG("(WxH) ${mask.cols()} x ${mask.rows()}")
//        val file = File(Environment.getExternalStorageDirectory(), "Download/segmentation_debug.png")
//        Imgcodecs.imwrite(file.absolutePath,mask)
        val boxes = when (boxType) {
            "poly" -> polygonsFromBitmap(pred, mask, srcW, srcH)
            "quad" -> boxesFromBitmap(pred, mask, srcW, srcH)
            else -> throw IllegalArgumentException("Invalid box type: $boxType")
        }
        if (groupedBoxes) {
            val grouped = groupOverlappingBoxes(boxes.boxes)
            grouped.forEachIndexed { index, box ->
                logger.DEBUG("Grouped $index")
                logger.DEBUG(box.joinToString(","))
            }
            return GroupedResult(boxes, grouped)
        }
        return GroupedResult(boxes, emptyList())
    }

    private fun polygonsFromBitmap(pred: Mat, bitmap: Mat, destWidth: Double, destHeight: Double): DetectionResult {
        val height = bitmap.rows()
        val width = bitmap.cols()
        val widthScale = destWidth / width
        val heightScale = destHeight / height

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(bitmap, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        val boxes = mutableListOf<List<Point>>()
        val scores = mutableListOf<Double>()

        contours.take(maxCandidates).forEach { contour ->
            val contour2f = MatOfPoint2f(*contour.toArray())

            val epsilon = 0.002 * Imgproc.arcLength(contour2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approx, epsilon, true)

            val points = approx.toArray()
            if (points.size < 4) return@forEach

            val score = boxScoreFast(pred, points)
            if (score < boxThresh) return@forEach

            val unclipped = unclip(points.toList())
            if (unclipped.size != 1) return@forEach

            val (box, sside) = getMiniBoxes(unclipped[0])
            if (sside < minSize + 2) return@forEach

            val scaledBox = box.map { point ->
                Point(
                    max(0.0, min(round(point.x * widthScale), destWidth)),
                    max(0.0, min(round(point.y * heightScale), destHeight))
                )
            }.toList()

            boxes.add(scaledBox)
            scores.add(score)
        }
        return DetectionResult(boxes, scores)
    }

    private fun boxesFromBitmap(pred: Mat, bitmap: Mat, destWidth: Double, destHeight: Double): DetectionResult {
        val height = bitmap.rows()
        val width = bitmap.cols()
        val widthScale = destWidth / width
        val heightScale = destHeight / height

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(bitmap, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        val boxes = mutableListOf<List<Point>>()
        val scores = mutableListOf<Double>()

        contours.take(maxCandidates).forEach { contour ->
            val (points, sside) = getMiniBoxes(contour.toArray().toList())
            if (sside < minSize) return@forEach

            val score = if (scoreMode == "fast") boxScoreFast(pred, points) else boxScoreSlow(pred, contour)
            if (score < boxThresh) return@forEach

            val unclipped = unclip(points.toList())
            if (unclipped.size != 1) return@forEach

            val (box, newSside) = getMiniBoxes(unclipped[0])
            if (newSside < minSize + 2) return@forEach

            val scaledBox = box.map { point ->
                Point(
                    max(0.0, min(round(point.x * widthScale), destWidth)),
                    max(0.0, min(round(point.y * heightScale), destHeight))
                )
            }.toList()

            boxes.add(scaledBox)
            scores.add(score)
        }

        return DetectionResult(boxes, scores)
    }

    private fun unclip(points: List<Point>): List<List<Point>> {
        val contour = Converters.vector_Point2f_to_Mat(points)
        val area = abs(Imgproc.contourArea(contour))
        val contour2f = MatOfPoint2f(contour)
        val length = Imgproc.arcLength(contour2f, true)
        val distance = area * unclipRatio / length

        val path = points.map { Point64(it.x, it.y) }
        val path64 = Path64(path)
        val co = ClipperOffset()
        val solution = Paths64()
        co.AddPath(path64, JoinType.Round, EndType.Polygon)
        co.Execute(distance, solution)
        val solutionList = solution.toList().map {
            it.map { pt ->
                Point(pt.x.toDouble(), pt.y.toDouble())
            }
        }
        return solutionList
    }

    private fun getMiniBoxes(contour: List<Point>): Pair<Array<Point>, Double> {

        val contourMat = Converters.vector_Point2f_to_Mat(contour)
        val contour2f = MatOfPoint2f(contourMat)
        val rect = Imgproc.minAreaRect(contour2f)
        val points = Array(4) { Point() }
        rect.points(points)

        points.sortBy { it.x }
        val (index1, index4) = if (points[1].y > points[0].y) 0 to 1 else 1 to 0
        val (index2, index3) = if (points[3].y > points[2].y) 2 to 3 else 3 to 2

        val sortedBox = arrayOf(
            points[index1], points[index2], points[index3], points[index4]
        )

        return Pair(sortedBox, min(rect.size.width, rect.size.height))
    }

    private fun boxScoreFast(bitmap: Mat, box: Array<Point>): Double {
        val h = bitmap.rows()
        val w = bitmap.cols()
        val xmin = max(0, min(box.minOf { it.x }.toInt(), w - 1))
        val xmax = max(0, min(box.maxOf { it.x }.toInt(), w - 1))
        val ymin = max(0, min(box.minOf { it.y }.toInt(), h - 1))
        val ymax = max(0, min(box.maxOf { it.y }.toInt(), h - 1))

        val mask = Mat.zeros(ymax - ymin + 1, xmax - xmin + 1, CvType.CV_8UC1)
        val adjustedBox = box.map { Point(it.x - xmin, it.y - ymin) }
        val adjustedPoints = adjustedBox.map { Point(it.x.toInt().toDouble(), it.y.toInt().toDouble()) }
        val mat2f = listOf(MatOfPoint(*adjustedPoints.toTypedArray()))
        Imgproc.fillPoly(mask, mat2f, Scalar(1.0))

        var sum = 0.0
        var count = 0
        for (y in ymin..ymax) {
            for (x in xmin..xmax) {
                if (mask.get(y - ymin, x - xmin)[0] > 0) {
                    sum += bitmap.get(y, x)[0]
                    count++
                }
            }
        }
        return sum / count
    }

    private fun boxScoreSlow(bitmap: Mat, contour: MatOfPoint): Double {
        val h = bitmap.rows()
        val w = bitmap.cols()
        val points = contour.toArray()
        val xmin = max(0, min(points.minOf { it.x }.toInt(), w - 1))
        val xmax = max(0, min(points.maxOf { it.x }.toInt(), w - 1))
        val ymin = max(0, min(points.minOf { it.y }.toInt(), h - 1))
        val ymax = max(0, min(points.maxOf { it.y }.toInt(), h - 1))

        val mask = Mat.zeros(ymax - ymin + 1, xmax - xmin + 1, CvType.CV_8UC1)
        val adjustedContour = points.map { Point(it.x - xmin, it.y - ymin) }
        val adjustedPoints = adjustedContour.map { Point(it.x.toInt().toDouble(), it.y.toInt().toDouble()) }
        val mat2f = listOf(MatOfPoint(*adjustedPoints.toTypedArray()))
        Imgproc.fillPoly(mask, mat2f, Scalar(1.0))

        var sum = 0.0
        var count = 0
        for (y in ymin..ymax) {
            for (x in xmin..xmax) {
                if (mask.get(y - ymin, x - xmin)[0] > 0) {
                    sum += bitmap.get(y, x)[0]
                    count++
                }
            }
        }
        return sum / count
    }
}
