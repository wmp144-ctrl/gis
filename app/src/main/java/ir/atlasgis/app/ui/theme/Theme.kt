package ir.atlasgis.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

private val AtlasScheme = darkColorScheme(
    primary = AtlasColors.Accent,
    onPrimary = AtlasColors.OnAccent,
    primaryContainer = AtlasColors.AccentDim,
    onPrimaryContainer = AtlasColors.TextPrimary,
    secondary = AtlasColors.Info,
    onSecondary = AtlasColors.Background,
    tertiary = AtlasColors.Warning,
    background = AtlasColors.Background,
    onBackground = AtlasColors.TextPrimary,
    surface = AtlasColors.Surface,
    onSurface = AtlasColors.TextPrimary,
    surfaceVariant = AtlasColors.SurfaceElevated,
    onSurfaceVariant = AtlasColors.TextSecondary,
    outline = AtlasColors.Outline,
    outlineVariant = AtlasColors.OutlineSoft,
    error = AtlasColors.Danger,
    onError = AtlasColors.Background,
)

val AtlasShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun AtlasTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(
        colorScheme = AtlasScheme,
        typography = AtlasTypography,
        shapes = AtlasShapes,
        content = content,
    )
}
