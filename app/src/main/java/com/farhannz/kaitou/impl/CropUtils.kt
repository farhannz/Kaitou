package com.farhannz.kaitou.impl

import com.farhannz.kaitou.helpers.Logger
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size as CvSize
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.sqrt

private val LOG_TAG = "CropUtils"
private val logger = Logger(LOG_TAG)

fun norm(p1: Point, p2: Point): Double {
    val dx = p1.x - p2.x
    val dy = p1.y - p2.y
    return sqrt(dx * dx + dy * dy)
}

fun cropFromBox(image: Mat, box: List<Point>): Mat {
    val start = System.nanoTime()

    if (box.size != 4) throw IllegalArgumentException("Box must have 4 points")

    val widthA = norm(box[2], box[3])
    val widthB = norm(box[1], box[0])
    val maxWidth = max(widthA, widthB).toInt()

    val heightA = norm(box[1], box[2])
    val heightB = norm(box[0], box[3])
    val maxHeight = max(heightA, heightB).toInt()

    val dst = listOf(
        Point(0.0, 0.0),
        Point(maxWidth - 1.0, 0.0),
        Point(maxWidth - 1.0, maxHeight - 1.0),
        Point(0.0, maxHeight - 1.0)
    )

    val srcMat = MatOfPoint2f(*box.toTypedArray())
    val dstMat = MatOfPoint2f(*dst.toTypedArray())

    val transform = Imgproc.getPerspectiveTransform(srcMat, dstMat)
    val warped = Mat()
    Imgproc.warpPerspective(
        image,
        warped,
        transform,
        CvSize(maxWidth.toDouble(), maxHeight.toDouble())
    )
    srcMat.release()
    dstMat.release()
    transform.release()

    val elapsed = (System.nanoTime() - start) / 1_000_000.0
    logger.INFO("[latency] cropFromBox: %.2f ms, output=${maxWidth}x${maxHeight}".format(elapsed))

    return warped
}
