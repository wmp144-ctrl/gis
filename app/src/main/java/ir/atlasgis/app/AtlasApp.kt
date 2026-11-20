package ir.atlasgis.app

import android.app.Application
import org.maplibre.android.MapLibre

class AtlasApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // MapLibre must be initialised once, before any MapView is created.
        MapLibre.getInstance(this)
    }
}
