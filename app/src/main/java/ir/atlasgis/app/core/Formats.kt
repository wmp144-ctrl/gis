package ir.atlasgis.app.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/** Number/text formatting for the Persian UI. */

private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

fun String.toPersianDigits(): String {
    val sb = StringBuilder(length)
    for (ch in this) {
        sb.append(if (ch in '0'..'9') PERSIAN_DIGITS[ch - '0'] else ch)
    }
    return sb.toString()
}

private fun Double.format(decimals: Int): String {
    val raw = String.format(Locale.US, "%,.${decimals}f", this)
    return raw
        .toPersianDigits()
        .replace(",", "٬")
        .replace('.', '٫')
}

/**
 * Distance with an automatically chosen unit. The units stay in their latin
 * symbols (m / km) as used on measuring lines and every stat in the app.
 */
fun formatDistance(meters: Double): String {
    if (!meters.isFinite()) return "—"
    return when {
        meters < 1000.0 -> "${meters.roundToLong().toString().toPersianDigits()} m"
        meters < 100_000.0 -> "${(meters / 1000.0).format(2)} km"
        else -> "${(meters / 1000.0).format(1)} km"
    }
}

/** Compact distance for dense on-map chips. */
fun formatDistanceShort(meters: Double): String {
    if (!meters.isFinite()) return "—"
    return if (meters < 1000.0) {
        "${meters.roundToLong().toString().toPersianDigits()} m"
    } else {
        "${(meters / 1000.0).format(1)} km"
    }
}

/** Area with squared units: m² below one square kilometre, km² above. */
fun formatArea(squareMeters: Double): String {
    if (!squareMeters.isFinite()) return "—"
    return if (squareMeters < 1_000_000.0) {
        "${squareMeters.roundToLong().toString().toPersianDigits()} m²"
    } else {
        "${(squareMeters / 1_000_000.0).format(2)} km²"
    }
}

fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val h = safe / 3600
    val m = (safe % 3600) / 60
    val s = safe % 60
    return if (h > 0) {
        "${h.toString().toPersianDigits()}:${m.toString().padStart(2, '0').toPersianDigits()}:${s.toString().padStart(2, '0').toPersianDigits()}"
    } else {
        "${m.toString().padStart(2, '0').toPersianDigits()}:${s.toString().padStart(2, '0').toPersianDigits()}"
    }
}

fun formatSpeed(metersPerSecond: Double): String {
    if (!metersPerSecond.isFinite() || metersPerSecond < 0) return "۰ km/h"
    return "${(metersPerSecond * 3.6).format(1)} km/h"
}

fun formatElevation(meters: Double?): String {
    if (meters == null || !meters.isFinite()) return "—"
    return "${meters.roundToLong().toString().toPersianDigits()} m"
}

fun formatOpacity(alpha: Float): String = "${(alpha * 100).roundToLong().toString().toPersianDigits()}٪"

/** Coordinate display formats. */
enum class CoordinateFormat(val label: String) {
    DECIMAL("اعشاری"),
    DMS("درجه/دقیقه/ثانیه"),
    UTM("UTM"),
}

fun formatCoordinate(lat: Double, lon: Double, format: CoordinateFormat): String =
    when (format) {
        CoordinateFormat.DECIMAL -> formatDecimal(lat, lon)
        CoordinateFormat.DMS -> "${formatDms(lat, true)}  ${formatDms(lon, false)}"
        CoordinateFormat.UTM -> utmString(lat, lon)
    }

private fun formatDecimal(lat: Double, lon: Double): String {
    val latText = String.format(Locale.US, "%.6f", abs(lat)).toPersianDigits().replace('.', '٫')
    val lonText = String.format(Locale.US, "%.6f", abs(lon)).toPersianDigits().replace('.', '٫')
    return "$latText°${if (lat >= 0) "N" else "S"}  $lonText°${if (lon >= 0) "E" else "W"}"
}

private fun formatDms(value: Double, isLat: Boolean): String {
    val absolute = abs(value)
    var degrees = floor(absolute).toInt()
    // Round to one decimal second first, then carry 60.0″ into a minute and 60′ into
    // a degree. Adjusting minutes/degrees while leaving the rounded seconds at 60
    // used to print impossible values such as 13°60′0.0″ near a whole degree.
    val totalSeconds = (((absolute - degrees) * 3600.0) * 10).roundToLong() / 10.0
    var minutes = (totalSeconds / 60.0).toInt()
    var seconds = totalSeconds - minutes * 60.0
    if (minutes >= 60) {
        degrees += minutes / 60
        minutes %= 60
    }
    if (seconds >= 60.0) {
        minutes += 1
        seconds = 0.0
    }
    if (minutes >= 60) {
        degrees += 1
        minutes = 0
    }
    val hemisphere = when {
        isLat -> if (value >= 0) "N" else "S"
        else -> if (value >= 0) "E" else "W"
    }
    val secondsText = String.format(Locale.US, "%.1f", seconds)
        .toPersianDigits().replace('.', '٫')
    return "${degrees.toString().toPersianDigits()}°${minutes.toString().padStart(2, '0').toPersianDigits()}′$secondsText″$hemisphere"
}
