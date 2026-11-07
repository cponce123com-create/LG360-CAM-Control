package pe.lgcam.control.panorama

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class FisheyeProjectionTest {
    private val front = LensProfile(640.0, 640.0, 600.0, 190.0, 0.0)
    private val back = LensProfile(1920.0, 640.0, 600.0, 190.0, 180.0)

    @Test fun centerRayHitsLensCenter() {
        val point = FisheyeProjection.project(0.0, 0.0, front)!!
        assertEquals(640.0, point.x, 1e-6)
        assertEquals(640.0, point.y, 1e-6)
    }

    @Test fun rearLensCenter() {
        val point = FisheyeProjection.project(PI, 0.0, back)!!
        assertEquals(1920.0, point.x, 1e-6)
        assertEquals(640.0, point.y, 1e-6)
    }

    @Test fun outsideFieldOfViewIsNotProjected() {
        assertNull(FisheyeProjection.project(PI, 0.0, front))
    }

    @Test fun equirectangularPixelCoordinatesAreBounded() {
        for (y in listOf(0, 639, 1279)) {
            for (x in listOf(0, 1279, 2559)) {
                val (lon, lat) = FisheyeProjection.equirectangularCoordinates(x, y, 2560, 1280)
                assertTrue(lon in -PI..PI)
                assertTrue(lat in -PI / 2..PI / 2)
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidRadius() {
        LensProfile(0.0, 0.0, 0.0, 190.0, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfRangePixel() {
        FisheyeProjection.equirectangularCoordinates(2560, 0, 2560, 1280)
    }
}
