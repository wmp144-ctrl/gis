package ir.atlasgis.app

import ir.atlasgis.app.core.parseCoordinateInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinateInputTest {

    @Test
    fun decimalDegreesAreParsed() {
        val point = parseCoordinateInput("35.6892, 51.3890")!!
        assertEquals(35.6892, point.lat, 1e-6)
        assertEquals(51.3890, point.lon, 1e-6)
    }

    @Test
    fun persianDigitsAndDecimalSeparatorAreParsed() {
        val point = parseCoordinateInput("۳۵٫۶۸۹۲، ۵۱٫۳۸۹۰")!!
        assertEquals(35.6892, point.lat, 1e-6)
        assertEquals(51.3890, point.lon, 1e-6)
    }

    @Test
    fun degreesMinutesSecondsAreParsed() {
        val point = parseCoordinateInput("35 41 21 N 51 23 20 E")!!
        assertEquals(35.6892, point.lat, 1e-4)
        assertEquals(51.3889, point.lon, 1e-4)
    }

    @Test
    fun southernAndWesternHemispheresKeepTheirSign() {
        // Dropping the letters used to put Sydney north-east of Greenwich and New
        // York east of it, with no error shown.
        val sydney = parseCoordinateInput("33.8688 S, 151.2093 E")!!
        assertEquals(-33.8688, sydney.lat, 1e-6)
        assertEquals(151.2093, sydney.lon, 1e-6)

        val newYork = parseCoordinateInput("40.7128 N, 74.0060 W")!!
        assertEquals(40.7128, newYork.lat, 1e-6)
        assertEquals(-74.0060, newYork.lon, 1e-6)
    }

    @Test
    fun dmsWithSouthernHemisphereIsNegative() {
        val point = parseCoordinateInput("33 52 8 S 151 12 33 E")!!
        assertEquals(-33.8689, point.lat, 1e-4)
        assertEquals(151.2092, point.lon, 1e-4)
    }

    @Test
    fun rejectsMinutesAndSecondsAtOrAboveSixty() {
        assertNull(parseCoordinateInput("35 99 0 N 51 23 20 E"))
        assertNull(parseCoordinateInput("35 41 61 N 51 23 20 E"))
        assertNull(parseCoordinateInput("35 41 21 N 51 60 0 E"))
    }

    @Test
    fun invalidInputIsRejected() {
        assertNull(parseCoordinateInput(""))
        assertNull(parseCoordinateInput("تهران"))
        assertNull(parseCoordinateInput("95.0, 51.0"))
        assertNull(parseCoordinateInput("35.0"))
    }
}
