package com.farhannz.kaitou.paddle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap
import com.farhannz.kaitou.ui.components.logger
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.RotatedRect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

data class PreprocessedImage(
    val chwFloatArray: FloatArray,
    val shape: IntArray // original height and width
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PreprocessedImage

        if (!chwFloatArray.contentEquals(other.chwFloatArray)) return false
        if (!shape.contentEquals(other.shape)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = chwFloatArray.contentHashCode()
        result = 31 * result + shape.contentHashCode()
        return result
    }
}


fun bitmapToFloatArray(bitmap: Bitmap,
                       normalize: Boolean,
                       mean: FloatArray = floatArrayOf(0.485f, 0.456f, 0.406f),
                       std: FloatArray = floatArrayOf(0.229f, 0.224f, 0.225f)
                       ): FloatArray {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

    return FloatArray(3 * bitmap.width * bitmap.height).apply {
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel) / 255f
            val g = Color.green(pixel) / 255f
            val b = Color.blue(pixel) / 255f

            if (normalize) {
                this[i] = ((r - mean[0]) / std[0])
                this[i + bitmap.width * bitmap.height] = ((g - mean[1]) / std[1])
                this[i + 2 * bitmap.width * bitmap.height] = ((b - mean[2]) / std[2])
            } else {
                this[i] = r
                this[i + bitmap.width * bitmap.height] = g
                this[i + 2 * bitmap.width * bitmap.height] = b
            }
        }
    }
}

fun orderPointsClockwise(pts: List<Point>): List<Point> {
    if (pts.size != 4) return emptyList()
//    require(pts.size == 4) { "Exactly 4 points required for quadrilateral" }

    // 1. Sort by x-coordinate
    val sortedByX = pts.sortedBy { it.x }

    // 2. Split into left and right points
    val leftMost = listOf(sortedByX[0], sortedByX[1])
    val rightMost = listOf(sortedByX[2], sortedByX[3])

    // 3. Sort left points by y-coordinate (top to bottom)
    val sortedLeft = leftMost.sortedBy { it.y }

    // 4. Sort right points by y-coordinate (top to bottom)
    val sortedRight = rightMost.sortedBy { it.y }

    // 5. Return in clockwise order: top-left, top-right, bottom-right, bottom-left
    return listOf(
        sortedLeft[0],   // Top-left
        sortedRight[0],  // Top-right
        sortedRight[1],  // Bottom-right
        sortedLeft[1]    // Bottom-left
    )
}

data class Quad(
    val p0: Point,
    val p1: Point,
    val p2: Point,
    val p3: Point
)

// Helper functions would be implemented separately:
fun getMiniBoxes(rotatedRect: RotatedRect): Pair<Double, List<Point>> {
    val ssid = min(rotatedRect.size.width, rotatedRect.size.height)

    var points = MatOfPoint2f()
    Imgproc.boxPoints(rotatedRect, points)
    val pointsArray = points.toArray()

    val sortedPoints = pointsArray
        .map { Point(it.x, it.y)}
        .sortedWith(compareBy({it.x},{it.y}))
    val (p0, p1, p2, p3) = when {
        // When right points are inverted
        sortedPoints[3].y <= sortedPoints[2].y ->
            Quad(sortedPoints[0], sortedPoints[3], sortedPoints[2], sortedPoints[1])
        // When left points are inverted
        sortedPoints[1].y <= sortedPoints[0].y ->
            Quad(sortedPoints[1], sortedPoints[2], sortedPoints[3], sortedPoints[0])
        // Normal case
        else ->
            Quad(sortedPoints[0], sortedPoints[2], sortedPoints[3], sortedPoints[1])
    }

    return Pair(ssid, listOf(p0, p1, p2, p3))
}

fun polygonScoreAcc(contour: MatOfPoint, pred: Mat): Float {
    val width = pred.cols()
    val height = pred.rows()
    val points = contour.toArray()

    // 1. Get bounding rectangle coordinates
    val xCoords = points.map { it.x.toFloat() }
    val yCoords = points.map { it.y.toFloat() }

    val xmin = floor(xCoords.minOrNull()!!).toInt().coerceIn(0, width - 1)
    val xmax = ceil(xCoords.maxOrNull()!!).toInt().coerceIn(0, width - 1)
    val ymin = floor(yCoords.minOrNull()!!).toInt().coerceIn(0, height - 1)
    val ymax = ceil(yCoords.maxOrNull()!!).toInt().coerceIn(0, height - 1)

    // 2. Create mask
    val mask = Mat.zeros(ymax - ymin + 1, xmax - xmin + 1, CvType.CV_8UC1)

    // 3. Convert contour points to mask coordinates
    val adjustedPoints = points.map { point ->
        org.opencv.core.Point(
            point.x - xmin.toDouble(),
            point.y - ymin.toDouble()
        )
    }.toTypedArray()

    // 4. Fill polygon
    Imgproc.fillPoly(
        mask,
        listOf(MatOfPoint(*adjustedPoints)),
        Scalar(1.0)
    )

    // 5. Crop prediction and calculate mean
    val cropped = Mat(pred, Rect(xmin, ymin, xmax - xmin + 1, ymax - ymin + 1))
    return Core.mean(cropped, mask).`val`[0].toFloat()
}

fun boxScoreFast(box: List<Point>, pred: Mat): Float {
    require(box.size == 4) { "Box must have exactly 4 points" }

    val width = pred.cols()
    val height = pred.rows()

    // 1. Get bounding rectangle coordinates
    val xCoords = box.map { it.x }
    val yCoords = box.map { it.y }

    val xmin = floor(xCoords.minOrNull()!!).toInt().coerceIn(0, width - 1)
    val xmax = ceil(xCoords.maxOrNull()!!).toInt().coerceIn(0, width - 1)
    val ymin = floor(yCoords.minOrNull()!!).toInt().coerceIn(0, height - 1)
    val ymax = ceil(yCoords.maxOrNull()!!).toInt().coerceIn(0, height - 1)

    // 2. Create mask
    val mask = Mat.zeros(ymax - ymin + 1, xmax - xmin + 1, CvType.CV_8UC1)

    // 3. Convert box points to mask coordinates
    val contour = box.map { point ->
        org.opencv.core.Point(
            (point.x - xmin).toDouble(),
            (point.y - ymin).toDouble()
        )
    }.toTypedArray()

    // 4. Fill polygon
    Imgproc.fillPoly(
        mask,
        listOf(MatOfPoint(*contour)),
        Scalar(1.0)
    )

    // 5. Crop prediction and calculate mean
    val cropped = Mat(pred, Rect(xmin, ymin, xmax - xmin + 1, ymax - ymin + 1))

    return Core.mean(cropped, mask).`val`[0].toFloat()
}


fun filterDetectedBoxes(
    boxes: List<TextBox>,
    ratioH: Float,
    ratioW: Float,
    srcImg: Bitmap
): List<TextBox> {
    val oriImgH = srcImg.height
    val oriImgW = srcImg.width

    return boxes.map { box ->
        // 1. Order points clockwise (assuming already ordered from previous step)
        val orderedPoints = orderPointsClockwise(box.points)

        // 2. Scale points back to original image coordinates
        val scaledPoints = orderedPoints.map { point ->
            Point((point.x / ratioW).coerceIn(0.0, (oriImgW - 1).toDouble()),
                (point.y / ratioH).coerceIn(0.0, (oriImgH - 1).toDouble())
            )
        }
        // topLeft, topRight, bottomLeft, bottomRight
        val topLeft = scaledPoints[0]
        val topRight = scaledPoints[1]
        val bottomLeft = scaledPoints[2]
        val bottomRight = scaledPoints[3]

        val rectWidth = sqrt(
            (topLeft.x - topRight.x) * (topLeft.x - topRight.x)
                + (topLeft.y - topRight.y) * (topLeft.y - topRight.y)
        )

        val rectHeight = sqrt(
            (bottomLeft.x - bottomRight.x) * (bottomLeft.x - bottomRight.x)
                    + (bottomLeft.y - bottomRight.y) * (bottomLeft.y - bottomRight.y)
        )

        logger.DEBUG("$rectWidth - $rectHeight")
//        if (rectWidth > 4 && rectHeight > 4) {
        TextBox(scaledPoints, 1.0f)
//        } else TextBox(emptyList(),0.0f)
    }
}

//
//FilterTagDetRes(std::vector<std::vector<std::vector<int>>> boxes, float ratio_h,
//float ratio_w, cv::Mat srcimg) {
//    int oriimg_h = srcimg.rows;
//    int oriimg_w = srcimg.cols;
//
//    std::vector<std::vector<std::vector<int>>> root_points;
//    for (int n = 0; n < static_cast<int>(boxes.size()); n++) {
//        boxes[n] = OrderPointsClockwise(boxes[n]);
//        for (int m = 0; m < static_cast<int>(boxes[0].size()); m++) {
//        boxes[n][m][0] /= ratio_w;
//        boxes[n][m][1] /= ratio_h;
//
//        boxes[n][m][0] =
//            static_cast<int>(std::min(std::max(boxes[n][m][0], 0), oriimg_w - 1));
//        boxes[n][m][1] =
//            static_cast<int>(std::min(std::max(boxes[n][m][1], 0), oriimg_h - 1));
//    }
//    }
//
//    for (int n = 0; n < boxes.size(); n++) {
//        int rect_width, rect_height;
//        rect_width =
//            static_cast<int>(sqrt(pow(boxes[n][0][0] - boxes[n][1][0], 2) +
//                    pow(boxes[n][0][1] - boxes[n][1][1], 2)));
//        rect_height =
//            static_cast<int>(sqrt(pow(boxes[n][0][0] - boxes[n][3][0], 2) +
//                    pow(boxes[n][0][1] - boxes[n][3][1], 2)));
//        if (rect_width <= 4 || rect_height <= 4)
//            continue;
//        root_points.push_back(boxes[n]);
//    }
//    return root_points;
//}