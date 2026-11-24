package ir.atlasgis.app.data

import android.content.Context
import android.content.SharedPreferences
import ir.atlasgis.app.core.CoordinateFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DECIMAL,
    val keepScreenOn: Boolean = true,
    val showScaleBar: Boolean = true,
    val showCoordinates: Boolean = true,
)

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("atlasgis_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings = AppSettings(
        coordinateFormat = runCatching {
            CoordinateFormat.valueOf(prefs.getString(KEY_COORD, CoordinateFormat.DECIMAL.name)!!)
        }.getOrDefault(CoordinateFormat.DECIMAL),
        keepScreenOn = prefs.getBoolean(KEY_KEEP_AWAKE, true),
        showScaleBar = prefs.getBoolean(KEY_SCALE, true),
        showCoordinates = prefs.getBoolean(KEY_COORDINATES, true),
    )

    fun updateCoordinateFormat(format: CoordinateFormat) {
        val current = _settings.value
        if (current.coordinateFormat == format) return
        prefs.edit().putString(KEY_COORD, format.name).apply()
        _settings.value = current.copy(coordinateFormat = format)
    }

    fun cycleCoordinateFormat() {
        val formats = CoordinateFormat.entries
        val next = formats[(formats.indexOf(_settings.value.coordinateFormat) + 1) % formats.size]
        updateCoordinateFormat(next)
    }

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_AWAKE, enabled).apply()
        _settings.value = _settings.value.copy(keepScreenOn = enabled)
    }

    fun setShowScaleBar(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCALE, enabled).apply()
        _settings.value = _settings.value.copy(showScaleBar = enabled)
    }

    fun setShowCoordinates(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_COORDINATES, enabled).apply()
        _settings.value = _settings.value.copy(showCoordinates = enabled)
    }

    fun saveCamera(lat: Double, lon: Double, zoom: Double, bearing: Double) {
        prefs.edit()
            .putString(KEY_CAMERA, "$lat,$lon,$zoom,$bearing")
            .apply()
    }

    data class SavedCamera(val lat: Double, val lon: Double, val zoom: Double, val bearing: Double)

    fun loadCamera(): SavedCamera? = parseSavedCamera(prefs.getString(KEY_CAMERA, null))

    companion object {
        private const val KEY_COORD = "coord_format"
        private const val KEY_KEEP_AWAKE = "keep_awake"
        private const val KEY_SCALE = "scale_bar"
        private const val KEY_COORDINATES = "coordinates"
        private const val KEY_CAMERA = "last_camera"

        /**
         * Rejects corrupt or out-of-range saved cameras. A bad value would otherwise
         * be handed straight to MapLibre on the first frame, where an impossible
         * latitude can abort the map instead of simply starting at the default view.
         */
        internal fun parseSavedCamera(raw: String?): SavedCamera? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split(',')
            if (parts.size != 4) return null
            val values = parts.map { it.trim().toDoubleOrNull() ?: return null }
            val lat = values[0]
            val lon = values[1]
            val zoom = values[2]
            val bearing = values[3]
            if (!lat.isFinite() || lat !in -90.0..90.0) return null
            if (!lon.isFinite() || lon !in -180.0..180.0) return null
            if (!zoom.isFinite() || zoom !in 1.0..22.0) return null
            if (!bearing.isFinite()) return null
            return SavedCamera(lat, lon, zoom, ((bearing % 360.0) + 360.0) % 360.0)
        }
    }
}
