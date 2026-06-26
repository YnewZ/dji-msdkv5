package dji.sampleV5.aircraft.aruco

data class ArucoDetection(
    val visible: Boolean,
    val markerId: Int = -1,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val normalizedErrorX: Float = 0f,
    val normalizedErrorY: Float = 0f,
    val corners: List<ArucoPoint> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun notFound(frameWidth: Int, frameHeight: Int): ArucoDetection {
            return ArucoDetection(
                visible = false,
                frameWidth = frameWidth,
                frameHeight = frameHeight
            )
        }
    }
}

data class ArucoPoint(
    val x: Float,
    val y: Float
)
