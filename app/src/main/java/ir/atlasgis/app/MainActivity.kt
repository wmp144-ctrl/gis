package ir.atlasgis.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.atlasgis.app.ui.MapScreen
import ir.atlasgis.app.ui.MapViewModel
import ir.atlasgis.app.ui.theme.AtlasTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
                AtlasTheme {
                    val viewModel: MapViewModel = viewModel()
                    MapScreen(viewModel)
                }
            }
        }
    }
}
