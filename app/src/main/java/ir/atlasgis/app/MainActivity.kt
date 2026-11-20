package ir.atlasgis.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.atlasgis.app.ui.MapScreen
import ir.atlasgis.app.ui.MapViewModel
import ir.atlasgis.app.ui.screens.LocalWaypointCategoryStyles
import ir.atlasgis.app.ui.theme.AtlasTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MapViewModel = viewModel()
            val categoryStyles by viewModel.categoryStyles.collectAsStateWithLifecycle()
            CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl,
                // Category colours/icons are persisted; one provider keeps every
                // chip, list row and map popup in sync.
                LocalWaypointCategoryStyles provides categoryStyles,
            ) {
                AtlasTheme {
                    MapScreen(viewModel)
                }
            }
        }
    }
}
