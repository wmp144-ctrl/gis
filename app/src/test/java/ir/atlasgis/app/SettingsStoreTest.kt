package ir.atlasgis.app

import ir.atlasgis.app.data.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** A saved camera from corrupted preferences must never reach MapLibre unchecked. */
class SettingsStoreTest {

    @Test
    fun readsAValidSavedCameraAndNormalisesBearing() {
        val camera = SettingsStore.parseSavedCamera("35.6892,51.3890,12.5,450")
        assertNotNull(camera)
        assertEquals(35.6892, camera!!.lat, 1e-9)
        assertEquals(51.3890, camera.lon, 1e-9)
        assertEquals(12.5, camera.zoom, 1e-9)
        assertEquals(90.0, camera.bearing, 1e-9)
    }

    @Test
    fun rejectsOutOfRangeAndMalformedCameras() {
        assertNull(SettingsStore.parseSavedCamera(null))
        assertNull(SettingsStore.parseSavedCamera(""))
        assertNull(SettingsStore.parseSavedCamera("a,b,c,d"))
        assertNull(SettingsStore.parseSavedCamera("35.7,51.4,12"))
        assertNull(SettingsStore.parseSavedCamera("95,51.4,12,0"))
        assertNull(SettingsStore.parseSavedCamera("35.7,200,12,0"))
        assertNull(SettingsStore.parseSavedCamera("35.7,51.4,0,0"))
        assertNull(SettingsStore.parseSavedCamera("35.7,51.4,12,NaN"))
    }
}
