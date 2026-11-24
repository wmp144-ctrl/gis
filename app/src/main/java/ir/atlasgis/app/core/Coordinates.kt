package ir.atlasgis.app.core

/**
 * Parses the coordinates a user types or pastes into the "go to" field.
 *
 * Decimal degrees and degrees/minutes/seconds are both accepted, with Latin or
 * Persian/Arabic-Indic digits and the usual separators:
 *
 *     `35.6892, 51.3890`
 *     `35 41 21 N 51 23 20 E`
 *     `۳۵٫۶۸۹۲، ۵۱٫۳۸۹۰`
 *
 * N/S/E/W suffixes are honoured. Dropping them would silently move a southern or
 * western coordinate into the opposite hemisphere, which is worse than rejecting it.
 */
fun parseCoordinateInput(input: String): LatLon? {
    val latin = buildString(input.length) {
        input.forEach { ch ->
            append(
                when {
                    ch in '۰'..'۹' -> '0' + (ch - '۰')
                    ch in '٠'..'٩' -> '0' + (ch - '٠')
                    else -> ch
                },
            )
        }
    }
        .replace('٫', '.') // Arabic decimal separator
        .replace('٬', ' ') // Arabic thousands separator
        .replace('،', ' ')
        .replace('°', ' ')
        .replace('′', ' ')
        .replace('″', ' ')
        .replace('’', ' ')
        .replace('‘', ' ')

    val directions = Regex("[NSEWnsew]").findAll(latin)
        .map { it.value.single().uppercaseChar() }
        .toList()
    val numeric = latin.replace(Regex("[NSEWnsew]"), " ")
    val tokens = Regex("""[-+]?\d+(?:\.\d+)?""").findAll(numeric)
        .mapNotNull { it.value.toDoubleOrNull() }
        .toList()

    val latDirection = directions.firstOrNull { it == 'N' || it == 'S' }
    val lonDirection = directions.firstOrNull { it == 'E' || it == 'W' }

    val position = when {
        tokens.size >= 6 -> {
            // Minutes and seconds must be below 60; accepting 35°99′ silently
            // moved the pin by more than a degree instead of reporting bad input.
            val invalidMinuteOrSecond = listOf(tokens[1], tokens[2], tokens[4], tokens[5])
                .any { it < 0.0 || it >= 60.0 }
            if (invalidMinuteOrSecond) {
                return null
            }
            LatLon(
                applyHemisphere(dmsToDecimal(tokens[0], tokens[1], tokens[2]), latDirection),
                applyHemisphere(dmsToDecimal(tokens[3], tokens[4], tokens[5]), lonDirection),
            )
        }
        tokens.size >= 2 -> LatLon(
            applyHemisphere(tokens[0], latDirection),
            applyHemisphere(tokens[1], lonDirection),
        )
        else -> return null
    }
    return position.takeIf { it.isValid() }
}

private fun applyHemisphere(value: Double, direction: Char?): Double = when (direction) {
    'S', 'W' -> -kotlin.math.abs(value)
    'N', 'E' -> kotlin.math.abs(value)
    else -> value
}

private fun dmsToDecimal(degrees: Double, minutes: Double, seconds: Double): Double {
    val sign = if (degrees < 0) -1.0 else 1.0
    return sign * (kotlin.math.abs(degrees) + minutes / 60.0 + seconds / 3600.0)
}
