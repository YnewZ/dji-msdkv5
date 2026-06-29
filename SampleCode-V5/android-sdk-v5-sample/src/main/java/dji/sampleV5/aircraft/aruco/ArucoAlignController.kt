package dji.sampleV5.aircraft.aruco

import kotlin.math.abs

data class ArucoAlignCommand(
    val pitchVelocity: Double,
    val rollVelocity: Double,
    val verticalVelocity: Double = 0.0,
    val yawRate: Double = 0.0,
    val aligned: Boolean,
    val positionAligned: Boolean,
    val yawAligned: Boolean,
    val reason: String
)

class ArucoAlignController(
    private val maxHorizontalVelocity: Double = 0.10,
    private val minHorizontalVelocity: Double = 0.025,
    private val kp: Double = 0.18,
    private val alignThreshold: Float = 0.14f,
    private val deadBand: Float = 0.08f,
    private val maxYawRateDegreesPerSecond: Double = 15.0,
    private val minYawRateDegreesPerSecond: Double = 3.0,
    private val yawKp: Double = 0.35,
    private val yawAlignThresholdDegrees: Float = 8f,
    private val yawDeadBandDegrees: Float = 4f,
    private val targetYawDegrees: Float = 0f,
    private val yawDirectionSign: Double = 1.0
) {

    fun calculate(detection: ArucoDetection?): ArucoAlignCommand {
        if (detection == null || !detection.visible) {
            return ArucoAlignCommand(
                0.0,
                0.0,
                aligned = false,
                positionAligned = false,
                yawAligned = false,
                reason = "marker lost"
            )
        }

        val errorX = detection.normalizedErrorX
        val errorY = detection.normalizedErrorY
        val yawError = normalizeDegrees(detection.rotationDegrees - targetYawDegrees)
        val positionAligned = abs(errorX) < alignThreshold && abs(errorY) < alignThreshold
        val yawAligned = abs(yawError) < yawAlignThresholdDegrees

        // Control only the dominant image axis at a time. This avoids diagonal
        // fly-away while the exact aircraft/camera/virtual-stick axis mapping is
        // being calibrated in real flights.
        val controlHorizontalAxis = abs(errorX) >= abs(errorY)
        val pitch = if (!positionAligned && controlHorizontalAxis) velocityFromError(errorX) else 0.0
        val roll = if (!positionAligned && !controlHorizontalAxis) velocityFromError(-errorY) else 0.0
        val yawRate = if (yawAligned) 0.0 else yawRateFromError(yawError)
        val aligned = positionAligned && yawAligned
        if (aligned) {
            return ArucoAlignCommand(
                0.0,
                0.0,
                yawRate = 0.0,
                aligned = true,
                positionAligned = true,
                yawAligned = true,
                reason = "position and yaw aligned"
            )
        }
        return ArucoAlignCommand(
            pitchVelocity = pitch,
            rollVelocity = roll,
            yawRate = yawRate,
            aligned = false,
            positionAligned = positionAligned,
            yawAligned = yawAligned,
            reason = "aligning errX=${"%.2f".format(errorX)}, errY=${"%.2f".format(errorY)}, yawErr=${"%.1f".format(yawError)}"
        )
    }

    private fun velocityFromError(error: Float): Double {
        if (abs(error) < deadBand) return 0.0
        val raw = error * kp
        val absVelocity = abs(raw).coerceIn(minHorizontalVelocity, maxHorizontalVelocity)
        return if (raw >= 0) absVelocity else -absVelocity
    }

    private fun yawRateFromError(errorDegrees: Float): Double {
        if (abs(errorDegrees) < yawDeadBandDegrees) return 0.0
        val raw = errorDegrees * yawKp * yawDirectionSign
        val absYawRate = abs(raw).coerceIn(minYawRateDegreesPerSecond, maxYawRateDegreesPerSecond)
        return if (raw >= 0) absYawRate else -absYawRate
    }

    private fun normalizeDegrees(degrees: Float): Float {
        var value = degrees
        while (value > 180f) value -= 360f
        while (value < -180f) value += 360f
        return value
    }
}
