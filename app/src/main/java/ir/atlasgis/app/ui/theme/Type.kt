package ir.atlasgis.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ir.atlasgis.app.R

val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private val base = Typography()

val AtlasTypography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = base.titleMedium.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    titleSmall = base.titleSmall.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = base.bodyLarge.copy(fontFamily = Vazirmatn, fontSize = 15.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = Vazirmatn, fontSize = 13.5.sp),
    bodySmall = base.bodySmall.copy(fontFamily = Vazirmatn, fontSize = 12.sp),
    labelLarge = base.labelLarge.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = base.labelMedium.copy(fontFamily = Vazirmatn, fontSize = 12.sp),
    labelSmall = base.labelSmall.copy(fontFamily = Vazirmatn, fontSize = 11.sp),
)

/** Tabular-ish style for live numeric readouts (distance, coordinates, speed). */
val NumericStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    letterSpacing = 0.3.sp,
)
