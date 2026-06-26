package dji.sampleV5.aircraft.aruco

import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.ArucoDetector
import org.opencv.objdetect.DetectorParameters
import org.opencv.objdetect.Objdetect
import kotlin.math.atan2

class OpenCvArucoDetector(
    private val targetMarkerId: Int = 1,
    dictionaryId: Int = Objdetect.DICT_5X5_50,
    private val fallbackDetector: MarkerDetector = dji.sampleV5.aircraft.aruco.ArucoDetector(targetMarkerId)
) : MarkerDetector {

    private val detector: ArucoDetector? = runCatching {
        if (!OpenCVLoader.initLocal()) return@runCatching null
        val parameters = DetectorParameters()
        parameters.set_detectInvertedMarker(true)
        ArucoDetector(Objdetect.getPredefinedDictionary(dictionaryId), parameters)
    }.getOrNull()

    override fun detectNv21(frameData: ByteArray, offset: Int, length: Int, width: Int, height: Int): ArucoDetection {
        val activeDetector = detector ?: return fallbackDetector.detectNv21(frameData, offset, length, width, height)
        if (width <= 0 || height <= 0 || length < width * height || offset < 0 || offset + length > frameData.size) {
            return ArucoDetection.notFound(width, height)
        }

        return runCatching {
            val nv21 = Mat(height + height / 2, width, CvType.CV_8UC1)
            val gray = Mat()
            val ids = Mat()
            val corners = ArrayList<Mat>()
            val rejected = ArrayList<Mat>()
            try {
                nv21.put(0, 0, frameData, offset, length)
                Imgproc.cvtColor(nv21, gray, Imgproc.COLOR_YUV2GRAY_NV21)
                activeDetector.detectMarkers(gray, corners, ids, rejected)
                decodeBestDetection(width, height, corners, ids)
                    ?: fallbackDetector.detectNv21(frameData, offset, length, width, height)
            } finally {
                nv21.release()
                gray.release()
                ids.release()
                corners.forEach { it.release() }
                rejected.forEach { it.release() }
            }
        }.getOrElse {
            fallbackDetector.detectNv21(frameData, offset, length, width, height)
        }
    }

    private fun decodeBestDetection(
        width: Int,
        height: Int,
        corners: List<Mat>,
        ids: Mat
    ): ArucoDetection? {
        if (ids.empty() || corners.isEmpty()) return null

        var bestIndex = -1
        var bestArea = 0.0
        for (i in 0 until ids.rows()) {
            val id = ids.get(i, 0)?.firstOrNull()?.toInt() ?: continue
            if (id != targetMarkerId || i >= corners.size) continue
            val points = MatOfPoint2f(corners[i]).toArray()
            if (points.size != 4) continue
            val area = polygonArea(points)
            if (area > bestArea) {
                bestArea = area
                bestIndex = i
            }
        }
        if (bestIndex < 0) return null

        val points = MatOfPoint2f(corners[bestIndex]).toArray()
        val centerX = points.map { it.x }.average().toFloat()
        val centerY = points.map { it.y }.average().toFloat()
        val topEdgeX = points[1].x - points[0].x
        val topEdgeY = points[1].y - points[0].y
        val rotationDegrees = normalizeDegrees((atan2(topEdgeY, topEdgeX) * RAD_TO_DEG).toFloat())
        val confidence = (bestArea / (width.toDouble() * height.toDouble()) / 0.08).coerceIn(0.25, 1.0).toFloat()

        return ArucoDetection(
            visible = true,
            markerId = targetMarkerId,
            frameWidth = width,
            frameHeight = height,
            centerX = centerX,
            centerY = centerY,
            normalizedErrorX = ((centerX - width / 2f) / (width / 2f)).coerceIn(-1f, 1f),
            normalizedErrorY = ((centerY - height / 2f) / (height / 2f)).coerceIn(-1f, 1f),
            rotationDegrees = rotationDegrees,
            confidence = confidence,
            corners = points.map { ArucoPoint(it.x.toFloat(), it.y.toFloat()) }
        )
    }

    private fun polygonArea(points: Array<Point>): Double {
        var area = 0.0
        for (i in points.indices) {
            val current = points[i]
            val next = points[(i + 1) % points.size]
            area += current.x * next.y - next.x * current.y
        }
        return kotlin.math.abs(area) * 0.5
    }

    private fun normalizeDegrees(degrees: Float): Float {
        var value = degrees
        while (value > 180f) value -= 360f
        while (value < -180f) value += 360f
        return value
    }

    companion object {
        private const val RAD_TO_DEG = 57.29578
    }
}
