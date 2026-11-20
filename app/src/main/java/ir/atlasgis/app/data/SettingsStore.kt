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
    val showCoordinateBar: Boolean = true,
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
        showCoordinateBar = prefs.getBoolean(KEY_COORD_BAR, true),
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

    fun setShowCoordinateBar(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_COORD_BAR, enabled).apply()
        _settings.value = _settings.value.copy(showCoordinateBar = enabled)
    }

    fun saveCamera(lat: Double, lon: Double, zoom: Double, bearing: Double) {
        prefs.edit()
            .putString(KEY_CAMERA, "$lat,$lon,$zoom,$bearing")
            .apply()
    }

    data class SavedCamera(val lat: Double, val lon: Double, val zoom: Double, val bearing: Double)

    fun loadCamera(): SavedCamera? {
        val raw = prefs.getString(KEY_CAMERA, null) ?: return null
        val parts = raw.split(',')
        if (parts.size != 4) return null
        return runCatching {
            SavedCamera(
                lat = parts[0].toDouble(),
                lon = parts[1].toDouble(),
                zoom = parts[2].toDouble(),
                bearing = parts[3].toDouble(),
            )
        }.getOrNull()
    }

    private companion object {
        const val KEY_COORD = "coord_format"
        const val KEY_KEEP_AWAKE = "keep_awake"
        const val KEY_SCALE = "scale_bar"
        const val KEY_COORD_BAR = "coord_bar"
        const val KEY_CAMERA = "last_camera"
    }
}
