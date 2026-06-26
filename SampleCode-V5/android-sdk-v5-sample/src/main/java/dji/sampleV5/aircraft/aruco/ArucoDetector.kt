package dji.sampleV5.aircraft.aruco

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

class ArucoDetector(
    private val targetMarkerId: Int = 1
) : MarkerDetector {

    companion object {
        private const val GRID_SIZE = 7
        private const val INNER_GRID_SIZE = 5
        private const val MIN_QUAD_AREA_RATIO = 0.0025f
        private const val MAX_CANDIDATES = 36
        private const val MAX_ACCEPTED_BIT_ERRORS = 2
        private const val RAD_TO_DEG = 57.29578f

        private val MARKER_ID_1_5X5_50 = arrayOf(
            intArrayOf(1, 1, 1, 1, 1, 1, 1),
            intArrayOf(1, 1, 1, 1, 1, 0, 1),
            intArrayOf(1, 0, 0, 1, 1, 1, 1),
            intArrayOf(1, 1, 1, 1, 1, 0, 1),
            intArrayOf(1, 0, 1, 0, 0, 0, 1),
            intArrayOf(1, 1, 1, 0, 0, 1, 1),
            intArrayOf(1, 1, 1, 1, 1, 1, 1)
        )
    }

    override fun detectNv21(frameData: ByteArray, offset: Int, length: Int, width: Int, height: Int): ArucoDetection {
        if (targetMarkerId != 1 || width <= 0 || height <= 0 || length <= 0) {
            return ArucoDetection.notFound(width, height)
        }
        val yPlaneSize = width * height
        if (offset < 0 || offset + min(length, yPlaneSize) > frameData.size) {
            return ArucoDetection.notFound(width, height)
        }
        val threshold = computeOtsuThreshold(frameData, offset, yPlaneSize)
        val components = findDarkComponents(frameData, offset, width, height, threshold)
        val candidates = components
            .filter { it.area > width * height * MIN_QUAD_AREA_RATIO }
            .sortedByDescending { it.area }
            .take(MAX_CANDIDATES)

        for (component in candidates) {
            val detection = decodeCandidate(frameData, offset, width, height, component, threshold)
            if (detection != null) return detection
        }
        return ArucoDetection.notFound(width, height)
    }

    private fun computeOtsuThreshold(data: ByteArray, offset: Int, yPlaneSize: Int): Int {
        val histogram = IntArray(256)
        val step = max(1, yPlaneSize / 240_000)
        var index = 0
        var count = 0
        while (index < yPlaneSize) {
            histogram[data[offset + index].toInt() and 0xFF]++
            count++
            index += step
        }

        var sum = 0L
        for (i in histogram.indices) {
            sum += i.toLong() * histogram[i]
        }

        var sumBackground = 0L
        var weightBackground = 0
        var maxVariance = 0.0
        var threshold = 100
        for (i in histogram.indices) {
            weightBackground += histogram[i]
            if (weightBackground == 0) continue
            val weightForeground = count - weightBackground
            if (weightForeground == 0) break
            sumBackground += i.toLong() * histogram[i]
            val meanBackground = sumBackground.toDouble() / weightBackground
            val meanForeground = (sum - sumBackground).toDouble() / weightForeground
            val variance = weightBackground.toDouble() * weightForeground *
                    (meanBackground - meanForeground) * (meanBackground - meanForeground)
            if (variance > maxVariance) {
                maxVariance = variance
                threshold = i
            }
        }
        return threshold.coerceIn(60, 180)
    }

    private fun findDarkComponents(data: ByteArray, offset: Int, width: Int, height: Int, threshold: Int): List<Component> {
        val visited = BooleanArray(width * height)
        val queue = IntArray(width * height)
        val components = ArrayList<Component>()
        val darkThreshold = (threshold - 8).coerceAtLeast(35)

        for (y in 0 until height) {
            val rowBase = y * width
            for (x in 0 until width) {
                val start = rowBase + x
                if (visited[start]) continue
                visited[start] = true
                if ((data[offset + start].toInt() and 0xFF) > darkThreshold) continue

                var head = 0
                var tail = 0
                queue[tail++] = start
                var minX = x
                var maxX = x
                var minY = y
                var maxY = y
                var area = 0

                while (head < tail) {
                    val pos = queue[head++]
                    val px = pos % width
                    val py = pos / width
                    area++
                    if (px < minX) minX = px
                    if (px > maxX) maxX = px
                    if (py < minY) minY = py
                    if (py > maxY) maxY = py

                    if (px > 0) tail = visitNeighbor(data, offset, visited, queue, tail, pos - 1, darkThreshold)
                    if (px + 1 < width) tail = visitNeighbor(data, offset, visited, queue, tail, pos + 1, darkThreshold)
                    if (py > 0) tail = visitNeighbor(data, offset, visited, queue, tail, pos - width, darkThreshold)
                    if (py + 1 < height) tail = visitNeighbor(data, offset, visited, queue, tail, pos + width, darkThreshold)
                }
                val boxWidth = maxX - minX + 1
                val boxHeight = maxY - minY + 1
                val aspect = boxWidth.toFloat() / max(1, boxHeight).toFloat()
                if (area > 100 && aspect in 0.55f..1.8f) {
                    components.add(Component(minX, minY, maxX, maxY, area))
                }
            }
        }
        return components
    }

    private fun visitNeighbor(
        data: ByteArray,
        offset: Int,
        visited: BooleanArray,
        queue: IntArray,
        tail: Int,
        pos: Int,
        threshold: Int
    ): Int {
        if (visited[pos]) return tail
        visited[pos] = true
        if ((data[offset + pos].toInt() and 0xFF) > threshold) return tail
        queue[tail] = pos
        return tail + 1
    }

    private fun decodeCandidate(
        data: ByteArray,
        offset: Int,
        width: Int,
        height: Int,
        component: Component,
        threshold: Int
    ): ArucoDetection? {
        val marginX = ((component.maxX - component.minX + 1) * 0.02f).toInt()
        val marginY = ((component.maxY - component.minY + 1) * 0.02f).toInt()
        val left = (component.minX + marginX).coerceIn(0, width - 1)
        val top = (component.minY + marginY).coerceIn(0, height - 1)
        val right = (component.maxX - marginX).coerceIn(left + 1, width - 1)
        val bottom = (component.maxY - marginY).coerceIn(top + 1, height - 1)
        val boxWidth = right - left + 1
        val boxHeight = bottom - top + 1
        if (boxWidth < 35 || boxHeight < 35) return null

        val rawCorners = arrayOf(
            PointF(left.toFloat(), top.toFloat()),
            PointF(right.toFloat(), top.toFloat()),
            PointF(right.toFloat(), bottom.toFloat()),
            PointF(left.toFloat(), bottom.toFloat())
        )
        val refinedCorners = refineCorners(data, offset, width, height, rawCorners, threshold)
        val bits = sampleMarkerBits(data, offset, width, height, refinedCorners, threshold)

        var bestErrors = Int.MAX_VALUE
        var bestRotation = 0
        for (rotation in 0 until 4) {
            val rotated = rotate(bits, rotation)
            if (!hasDarkBorder(rotated)) continue
            val errors = countInnerErrors(rotated, MARKER_ID_1_5X5_50)
            if (errors < bestErrors) {
                bestErrors = errors
                bestRotation = rotation
            }
        }

        if (bestErrors > MAX_ACCEPTED_BIT_ERRORS) return null
        val orientedCorners = orientCorners(refinedCorners, bestRotation)
        val centerX = orientedCorners.map { it.x }.average().toFloat()
        val centerY = orientedCorners.map { it.y }.average().toFloat()
        val topEdgeX = orientedCorners[1].x - orientedCorners[0].x
        val topEdgeY = orientedCorners[1].y - orientedCorners[0].y
        val rotationDegrees = atan2(topEdgeY, topEdgeX) * RAD_TO_DEG
        val confidence = ((INNER_GRID_SIZE * INNER_GRID_SIZE - bestErrors).toFloat() / (INNER_GRID_SIZE * INNER_GRID_SIZE)).coerceIn(0f, 1f)
        return ArucoDetection(
            visible = true,
            markerId = targetMarkerId,
            frameWidth = width,
            frameHeight = height,
            centerX = centerX,
            centerY = centerY,
            normalizedErrorX = ((centerX - width / 2f) / (width / 2f)).coerceIn(-1f, 1f),
            normalizedErrorY = ((centerY - height / 2f) / (height / 2f)).coerceIn(-1f, 1f),
            rotationDegrees = normalizeDegrees(rotationDegrees),
            confidence = confidence,
            corners = orientedCorners.map { ArucoPoint(it.x, it.y) }
        )
    }

    private fun sampleMarkerBits(
        data: ByteArray,
        offset: Int,
        width: Int,
        height: Int,
        corners: Array<PointF>,
        threshold: Int
    ): Array<IntArray> {
        val bits = Array(GRID_SIZE) { IntArray(GRID_SIZE) }
        for (gy in 0 until GRID_SIZE) {
            for (gx in 0 until GRID_SIZE) {
                val mean = samplePerspectiveCellMean(data, offset, width, height, corners, gx, gy)
                bits[gy][gx] = if (mean < threshold) 1 else 0
            }
        }
        return bits
    }

    private fun refineCorners(
        data: ByteArray,
        offset: Int,
        width: Int,
        height: Int,
        rawCorners: Array<PointF>,
        threshold: Int
    ): Array<PointF> {
        val refined = Array(rawCorners.size) { index -> rawCorners[index] }
        val searchRadius = max(3, min(width, height) / 160)
        val darkThreshold = (threshold - 4).coerceAtLeast(35)
        for (i in rawCorners.indices) {
            val corner = rawCorners[i]
            val x0 = (corner.x.toInt() - searchRadius).coerceIn(0, width - 1)
            val x1 = (corner.x.toInt() + searchRadius).coerceIn(0, width - 1)
            val y0 = (corner.y.toInt() - searchRadius).coerceIn(0, height - 1)
            val y1 = (corner.y.toInt() + searchRadius).coerceIn(0, height - 1)
            var sumX = 0f
            var sumY = 0f
            var count = 0
            for (y in y0..y1) {
                val row = y * width
                for (x in x0..x1) {
                    val luminance = data[offset + row + x].toInt() and 0xFF
                    if (luminance <= darkThreshold) {
                        sumX += x.toFloat()
                        sumY += y.toFloat()
                        count++
                    }
                }
            }
            if (count > 0) {
                refined[i] = PointF(sumX / count, sumY / count)
            }
        }
        return refined
    }

    private fun samplePerspectiveCellMean(
        data: ByteArray,
        offset: Int,
        width: Int,
        height: Int,
        corners: Array<PointF>,
        gx: Int,
        gy: Int
    ): Int {
        var sum = 0L
        var count = 0
        val samples = floatArrayOf(0.35f, 0.5f, 0.65f)
        for (sy in samples) {
            for (sx in samples) {
                val u = (gx + sx) / GRID_SIZE.toFloat()
                val v = (gy + sy) / GRID_SIZE.toFloat()
                val point = interpolate(corners, u, v)
                val x = point.x.toInt().coerceIn(0, width - 1)
                val y = point.y.toInt().coerceIn(0, height - 1)
                sum += data[offset + y * width + x].toInt() and 0xFF
                count++
            }
        }
        return if (count == 0) 255 else (sum / count).toInt()
    }

    private fun hasDarkBorder(bits: Array<IntArray>): Boolean {
        for (i in 0 until GRID_SIZE) {
            if (bits[0][i] != 1 || bits[GRID_SIZE - 1][i] != 1) return false
            if (bits[i][0] != 1 || bits[i][GRID_SIZE - 1] != 1) return false
        }
        return true
    }

    private fun countInnerErrors(bits: Array<IntArray>, expected: Array<IntArray>): Int {
        var errors = 0
        for (y in 0 until INNER_GRID_SIZE) {
            for (x in 0 until INNER_GRID_SIZE) {
                if (bits[y + 1][x + 1] != expected[y + 1][x + 1]) {
                    errors++
                }
            }
        }
        return errors
    }

    private fun rotate(bits: Array<IntArray>, rotation: Int): Array<IntArray> {
        var result = bits
        repeat(rotation) {
            val rotated = Array(GRID_SIZE) { IntArray(GRID_SIZE) }
            for (y in 0 until GRID_SIZE) {
                for (x in 0 until GRID_SIZE) {
                    rotated[x][GRID_SIZE - 1 - y] = result[y][x]
                }
            }
            result = rotated
        }
        return result
    }

    private fun orientCorners(corners: Array<PointF>, rotation: Int): Array<PointF> {
        return Array(corners.size) { index -> corners[(index + rotation) % corners.size] }
    }

    private fun interpolate(corners: Array<PointF>, u: Float, v: Float): PointF {
        val topX = corners[0].x + (corners[1].x - corners[0].x) * u
        val topY = corners[0].y + (corners[1].y - corners[0].y) * u
        val bottomX = corners[3].x + (corners[2].x - corners[3].x) * u
        val bottomY = corners[3].y + (corners[2].y - corners[3].y) * u
        return PointF(
            topX + (bottomX - topX) * v,
            topY + (bottomY - topY) * v
        )
    }

    private fun normalizeDegrees(degrees: Float): Float {
        var value = degrees
        while (value > 180f) value -= 360f
        while (value < -180f) value += 360f
        return value
    }

    private data class PointF(
        val x: Float,
        val y: Float
    )

    private data class Component(
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int,
        val area: Int
    )
}
