package dji.sampleV5.aircraft.aruco

interface MarkerDetector {
    fun detectNv21(frameData: ByteArray, offset: Int, length: Int, width: Int, height: Int): ArucoDetection
}
