package dji.sampleV5.aircraft.aruco

import kotlin.math.abs

data class ArucoAlignCommand(
    val pitchVelocity: Double,
    val rollVelocity: Double,
    val verticalVelocity: Double = 0.0,
    val yawRate: Double = 0.0,
    val aligned: Boolean,
    val reason: String
)

class ArucoAlignController(
    private val maxHorizontalVelocity: Double = 0.05,
    private val minHorizontalVelocity: Double = 0.015,
    private val kp: Double = 0.10,
    private val alignThreshold: Float = 0.14f,
    private val deadBand: Float = 0.08f
) {

    fun calculate(detection: ArucoDetection?): ArucoAlignCommand {
        if (detection == null || !detection.visible) {
            return ArucoAlignCommand(0.0, 0.0, aligned = false, reason = "marker lost")
        }

        val errorX = detection.normalizedErrorX
        val errorY = detection.normalizedErrorY
        val aligned = abs(errorX) < alignThreshold && abs(errorY) < alignThreshold
        if (aligned) {
            return ArucoAlignCommand(0.0, 0.0, aligned = true, reason = "aligned")
        }

        // Control only the dominant image axis at a time. This avoids diagonal
        // fly-away while the exact aircraft/camera/virtual-stick axis mapping is
        // being calibrated in real flights.
        val controlHorizontalAxis = abs(errorX) >= abs(errorY)
        val pitch = if (controlHorizontalAxis) velocityFromError(errorX) else 0.0
        val roll = if (controlHorizontalAxis) 0.0 else velocityFromError(-errorY)
        return ArucoAlignCommand(
            pitchVelocity = pitch,
            rollVelocity = roll,
            aligned = false,
            reason = "aligning errX=${"%.2f".format(errorX)}, errY=${"%.2f".format(errorY)}"
        )
    }

    private fun velocityFromError(error: Float): Double {
        if (abs(error) < deadBand) return 0.0
        val raw = error * kp
        val absVelocity = abs(raw).coerceIn(minHorizontalVelocity, maxHorizontalVelocity)
        return if (raw >= 0) absVelocity else -absVelocity
    }
}
