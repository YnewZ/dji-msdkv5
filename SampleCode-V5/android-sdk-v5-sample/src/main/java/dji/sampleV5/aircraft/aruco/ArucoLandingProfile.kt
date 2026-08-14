package dji.sampleV5.aircraft.aruco

data class ArucoLandingProfile(
    val displayName: String,
    val maxHorizontalVelocity: Double,
    val minHorizontalVelocity: Double,
    val kp: Double,
    val maxYawRateDegreesPerSecond: Double,
    val minYawRateDegreesPerSecond: Double,
    val yawKp: Double,
    val descentVelocityMps: Double,
    val alignStableTimeMs: Long = 1200L,
    val finalAutoLandingHeightM: Double = 0.25
) {
    companion object {
        val MAVIC_3E = ArucoLandingProfile(
            displayName = "Mavic 3E",
            maxHorizontalVelocity = 0.10,
            minHorizontalVelocity = 0.025,
            kp = 0.18,
            maxYawRateDegreesPerSecond = 15.0,
            minYawRateDegreesPerSecond = 3.0,
            yawKp = 0.35,
            descentVelocityMps = -0.12
        )

        val MATRICE_4T = ArucoLandingProfile(
            displayName = "Matrice 4T",
            maxHorizontalVelocity = 0.22,
            minHorizontalVelocity = 0.05,
            kp = 0.32,
            maxYawRateDegreesPerSecond = 25.0,
            minYawRateDegreesPerSecond = 5.0,
            yawKp = 0.55,
            descentVelocityMps = -0.16
        )

        val ALL = listOf(MAVIC_3E, MATRICE_4T)
    }
}
