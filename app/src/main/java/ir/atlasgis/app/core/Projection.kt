package ir.atlasgis.app.core

import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan
import kotlin.math.pow
import kotlin.math.PI

/** WGS84 -> UTM (Krüger series), accurate to a few centimetres inside a zone. */

private const val A = 6378137.0                  // semi-major axis
private const val F = 1.0 / 298.257223563        // flattening
private const val K0 = 0.9996

private val E2 = F * (2 - F)
private val EP2 = E2 / (1 - E2)

fun utmZoneNumber(lon: Double): Int {
    val normalized = ((lon + 180.0) % 360.0 + 360.0) % 360.0
    return (floor(normalized / 6.0).toInt() + 1).coerceIn(1, 60)
}

fun utmZoneLetter(lat: Double): Char {
    if (lat < -80.0) return 'C'
    if (lat > 84.0) return 'X'
    val letters = "CDEFGHJKLMNPQRSTUVWXX"
    val index = ((lat + 80.0) / 8.0).toInt().coerceIn(0, letters.length - 1)
    return letters[index]
}

data class UtmCoordinate(
    val easting: Double,
    val northing: Double,
    val zone: Int,
    val hemisphere: Char,
    val band: Char,
)

fun toUtm(lat: Double, lon: Double): UtmCoordinate {
    val zone = utmZoneNumber(lon)
    val lonOrigin = (zone - 1) * 6 - 180 + 3
    val latRad = lat * PI / 180.0
    val lonRad = lon * PI / 180.0
    val lonOriginRad = lonOrigin * PI / 180.0

    val sinLat = sin(latRad)
    val cosLat = cos(latRad)
    val tanLat = tan(latRad)

    val n = A / kotlin.math.sqrt(1 - E2 * sinLat * sinLat)
    val t = tanLat * tanLat
    val c = EP2 * cosLat * cosLat
    val a = cosLat * (lonRad - lonOriginRad)

    val m = A * (
        (1 - E2 / 4 - 3 * E2 * E2 / 64 - 5 * E2 * E2 * E2 / 256) * latRad -
            (3 * E2 / 8 + 3 * E2 * E2 / 32 + 45 * E2 * E2 * E2 / 1024) * sin(2 * latRad) +
            (15 * E2 * E2 / 256 + 45 * E2 * E2 * E2 / 1024) * sin(4 * latRad) -
            (35 * E2 * E2 * E2 / 3072) * sin(6 * latRad)
        )

    val easting = K0 * n * (
        a + (1 - t + c) * a.pow(3) / 6 +
            (5 - 18 * t + t * t + 72 * c - 58 * EP2) * a.pow(5) / 120
        ) + 500_000.0

    var northing = K0 * (
        m + n * tanLat * (
            a * a / 2 +
                (5 - t + 9 * c + 4 * c * c) * a.pow(4) / 24 +
                (61 - 58 * t + t * t + 600 * c - 330 * EP2) * a.pow(6) / 720
            )
        )
    if (lat < 0) northing += 10_000_000.0

    return UtmCoordinate(
        easting = easting,
        northing = northing,
        zone = zone,
        hemisphere = if (lat >= 0) 'N' else 'S',
        band = utmZoneLetter(lat),
    )
}

fun utmString(lat: Double, lon: Double): String {
    val utm = toUtm(lat, lon)
    val easting = String.format(Locale.US, "%.0f", utm.easting).toPersianDigits()
    val northing = String.format(Locale.US, "%.0f", utm.northing).toPersianDigits()
    return "${utm.zone}${utm.band}  E $easting  N $northing"
}

/** Distance in degrees of the current camera span, used for the scale bar. */
fun niceScaleDistance(maxMeters: Double): Double {
    if (maxMeters <= 0 || !maxMeters.isFinite()) return 1.0
    val exponent = floor(kotlin.math.log10(maxMeters))
    val base = 10.0.pow(exponent)
    val candidates = doubleArrayOf(1.0, 2.0, 5.0, 10.0)
    var best = base
    for (c in candidates) {
        if (c * base <= maxMeters) best = c * base
    }
    return best
}

/** Bearing in degrees converted to a compass label. */
fun compassLabel(bearing: Double): String {
    val dirs = arrayOf("شمال", "شمال‌شرق", "شرق", "جنوب‌شرق", "جنوب", "جنوب‌غرب", "غرب", "شمال‌غرب")
    val index = (((bearing % 360) + 360) % 360 / 45.0).roundToInt() % 8
    return dirs[index]
}
