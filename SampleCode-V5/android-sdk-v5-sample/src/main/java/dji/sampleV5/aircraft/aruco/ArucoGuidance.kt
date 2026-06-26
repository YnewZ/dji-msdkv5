package dji.sampleV5.aircraft.aruco

import kotlin.math.abs

data class ArucoGuidance(
    val instruction: String,
    val detail: String,
    val aligned: Boolean,
    val canDescend: Boolean
)

class ArucoGuidanceController(
    private val alignThreshold: Float = 0.12f,
    private val deadBand: Float = 0.06f
) {

    fun calculate(detection: ArucoDetection?): ArucoGuidance {
        if (detection == null || !detection.visible) {
            return ArucoGuidance(
                instruction = "SEARCH MARKER",
                detail = "未检测到 ArUco ID 1，保持悬停/缓慢搜索",
                aligned = false,
                canDescend = false
            )
        }

        val errorX = detection.normalizedErrorX
        val errorY = detection.normalizedErrorY
        val alignedX = abs(errorX) < alignThreshold
        val alignedY = abs(errorY) < alignThreshold
        val aligned = alignedX && alignedY

        if (aligned) {
            return ArucoGuidance(
                instruction = "ALIGNED - DESCEND SLOWLY",
                detail = "二维码接近画面中心，可低速下降；errX=${"%.2f".format(errorX)}, errY=${"%.2f".format(errorY)}",
                aligned = true,
                canDescend = true
            )
        }

        val commands = ArrayList<String>()
        // 当前仅作为半自动提示：X 方向通常对应左右，Y 方向需按实际相机安装方向校准。
        if (errorX > deadBand) {
            commands.add("RIGHT")
        } else if (errorX < -deadBand) {
            commands.add("LEFT")
        }

        if (errorY > deadBand) {
            commands.add("BACK / DOWN IN IMAGE")
        } else if (errorY < -deadBand) {
            commands.add("FORWARD / UP IN IMAGE")
        }

        return ArucoGuidance(
            instruction = if (commands.isEmpty()) "HOLD" else commands.joinToString(" + "),
            detail = "先水平对准，暂不下降；errX=${"%.2f".format(errorX)}, errY=${"%.2f".format(errorY)}",
            aligned = false,
            canDescend = false
        )
    }
}
