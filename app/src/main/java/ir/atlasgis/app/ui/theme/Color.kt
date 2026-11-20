package ir.atlasgis.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Night-navigation palette: deep navy base, mint accent, amber warnings. */
object AtlasColors {
    val Background = Color(0xFF0B1220)
    val Surface = Color(0xFF121C2C)
    val SurfaceElevated = Color(0xFF182437)
    val SurfaceGlass = Color(0xE6121C2C)
    val Outline = Color(0xFF243349)
    val OutlineSoft = Color(0x332C3E58)

    val Accent = Color(0xFF3ED598)
    val AccentDim = Color(0xFF219A6C)
    val AccentSoft = Color(0x333ED598)

    val OnAccent = Color(0xFF04231A)
    val TextPrimary = Color(0xFFE9F1FA)
    val TextSecondary = Color(0xFF93A4BC)
    // Lightened from #64758D: the old value sat at ~4.4:1 on the panel colour, just
    // under the 4.5:1 WCAG AA minimum for the 11sp labels it is used for.
    val TextMuted = Color(0xFF8497AF)

    val Danger = Color(0xFFFF6B6B)
    val Warning = Color(0xFFFFC15E)
    val Info = Color(0xFF5FB8FF)
    val Track = Color(0xFFFF9F45)
    val Measure = Color(0xFF7C8CFF)

    val Water = Color(0xFF2C6E9B)
    val Land = Color(0xFF1D3B2E)
}
