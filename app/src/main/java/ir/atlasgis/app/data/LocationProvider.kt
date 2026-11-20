package ir.atlasgis.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plain Android LocationManager (no Google Play Services) so the app works on any
 * device and fully offline. Emits GNSS fixes plus the satellite count from GnssStatus.
 */
class LocationProvider(private val context: Context) {

    private val manager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _fix = MutableStateFlow<GpsFix?>(null)
    val fix: StateFlow<GpsFix?> = _fix.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private var satellites = 0
    private var running = false
    private var gnssCallback: GnssStatus.Callback? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!hasPermission()) return
            val accuracy = if (location.hasAccuracy()) location.accuracy else 999f
            _fix.value = GpsFix(
                lat = location.latitude,
                lon = location.longitude,
                accuracyMeters = accuracy,
                altitude = if (location.hasAltitude()) location.altitude else null,
                speedMps = if (location.hasSpeed()) location.speed else null,
                bearing = if (location.hasBearing()) location.bearing.toDouble() else null,
                satellites = satellites,
                timestamp = if (location.time > 0) location.time else System.currentTimeMillis(),
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Whether any usable provider is currently enabled in system settings. */
    fun locationServicesEnabled(): Boolean = runCatching {
        manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
            manager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        val manager = manager ?: return
        if (!hasPermission()) {
            _enabled.value = false
            return
        }
        running = true
        _enabled.value = locationServicesEnabled()
        val minTime = 1000L
        val minDistance = 0f
        runCatching {
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, minTime, minDistance, listener, Looper.getMainLooper(),
                )
            }
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, minTime * 5, 5f, listener, Looper.getMainLooper(),
                )
            }
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { last ->
                if (_fix.value == null && last.time > System.currentTimeMillis() - 15 * 60 * 1000) {
                    listener.onLocationChanged(last)
                }
            }
        }
        registerGnss()
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!running) return
        running = false
        val manager = manager ?: return
        runCatching { manager.removeUpdates(listener) }
        unregisterGnss()
        _enabled.value = false
    }

    @SuppressLint("MissingPermission")
    private fun registerGnss() {
        val manager = manager ?: return
        if (gnssCallback != null) return
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var used = 0
                for (i in 0 until status.satelliteCount) {
                    if (status.usedInFix(i)) used++
                }
                satellites = used
                _fix.value = _fix.value?.copy(satellites = used)
            }
        }
        gnssCallback = callback
        runCatching { manager.registerGnssStatusCallback(callback, null) }
    }

    @SuppressLint("MissingPermission")
    private fun unregisterGnss() {
        val callback = gnssCallback ?: return
        gnssCallback = null
        runCatching { manager?.unregisterGnssStatusCallback(callback) }
    }
}
