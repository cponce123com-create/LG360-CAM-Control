package pe.lgcam.control.panorama

import kotlin.math.*

/** Pure geometry used for calibration previews and future GPU remapping.
 * Angles are radians. No assumption about the LG R105's unmeasured optics.
 */
data class LensProfile(
    val centerX: Double,
    val centerY: Double,
    val radius: Double,
    val fieldOfViewDegrees: Double,
    val yawDegrees: Double,
    val pitchDegrees: Double = 0.0,
    val rollDegrees: Double = 0.0
) {
    init {
        require(centerX.isFinite() && centerY.isFinite())
        require(radius.isFinite() && radius > 0.0)
        require(fieldOfViewDegrees in 1.0..360.0)
        require(yawDegrees.isFinite() && pitchDegrees.isFinite() && rollDegrees.isFinite())
    }
}

data class PixelPoint(val x: Double, val y: Double)

/** Equidistant fisheye projection. Real lens distortion must be calibrated separately. */
object FisheyeProjection {
    fun project(longitude: Double, latitude: Double, lens: LensProfile): PixelPoint? {
        require(longitude.isFinite() && latitude.isFinite())
        require(latitude in -PI / 2..PI / 2)
        val x = cos(latitude) * sin(longitude)
        val y = sin(latitude)
        val z = cos(latitude) * cos(longitude)
        // Inverse yaw / pitch / roll: rotate world ray into lens coordinates.
        val yaw = Math.toRadians(lens.yawDegrees)
        val pitch = Math.toRadians(lens.pitchDegrees)
        val roll = Math.toRadians(lens.rollDegrees)
        val x1 = cos(yaw) * x - sin(yaw) * z
        val z1 = sin(yaw) * x + cos(yaw) * z
        val y2 = cos(pitch) * y + sin(pitch) * z1
        val z2 = -sin(pitch) * y + cos(pitch) * z1
        val x3 = cos(roll) * x1 + sin(roll) * y2
        val y3 = -sin(roll) * x1 + cos(roll) * y2
        val angle = acos(z2.coerceIn(-1.0, 1.0))
        val maxAngle = Math.toRadians(lens.fieldOfViewDegrees) / 2.0
        if (angle > maxAngle) return null
        val distance = lens.radius * angle / maxAngle
        val planar = hypot(x3, y3)
        if (planar < 1e-12) return PixelPoint(lens.centerX, lens.centerY)
        return PixelPoint(lens.centerX + distance * x3 / planar,
            lens.centerY - distance * y3 / planar)
    }

    fun equirectangularCoordinates(column: Int, row: Int, width: Int, height: Int): Pair<Double, Double> {
        require(width > 0 && height > 0 && column in 0 until width && row in 0 until height)
        val lon = (column + 0.5) / width * (2 * PI) - PI
        val lat = PI / 2 - (row + 0.5) / height * PI
        return lon to lat
    }
}
