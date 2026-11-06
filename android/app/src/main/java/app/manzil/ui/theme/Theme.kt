@file:OptIn(ExperimentalTextApi::class)

package app.manzil.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.manzil.R

/** design/tokens.json, plus soft tints / deep teal derived for >= 4.5:1 text contrast. */
object MzColors {
    val Ink = Color(0xFF142D4E)
    val Paper = Color(0xFFF5F7FA)
    val Surface = Color(0xFFFFFFFF)
    val Primary = Color(0xFF2457D6)
    val PrimarySoft = Color(0xFFEAF0FC)
    val Teal = Color(0xFF087E83)
    val TealDeep = Color(0xFF066A6E)
    val TealSoft = Color(0xFFE8F4F4)
    val Warning = Color(0xFF975700)
    val WarningSoft = Color(0xFFFBF1E3)
    val Danger = Color(0xFFC04A32)
    val Muted = Color(0xFF53657A)
    val Border = Color(0xFFD8E0E9)
}

private fun manrope(weight: Int) = Font(
    resId = R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Bundled Manrope variable font. Its default axis value is 200, so every weight is explicit. */
val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

private val ManzilTypography = Typography(
    displaySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp),
)

private val ManzilColorScheme = lightColorScheme(
    primary = MzColors.Primary,
    onPrimary = MzColors.Surface,
    primaryContainer = MzColors.PrimarySoft,
    onPrimaryContainer = MzColors.Primary,
    secondary = MzColors.Teal,
    onSecondary = MzColors.Surface,
    background = MzColors.Paper,
    onBackground = MzColors.Ink,
    surface = MzColors.Surface,
    onSurface = MzColors.Ink,
    surfaceVariant = MzColors.Paper,
    onSurfaceVariant = MzColors.Muted,
    surfaceContainerLow = MzColors.Surface,
    surfaceContainer = MzColors.Surface,
    surfaceContainerHigh = MzColors.Surface,
    outline = MzColors.Border,
    outlineVariant = MzColors.Border,
    error = MzColors.Danger,
)

/** Light-only by design for this slice (tokens define one palette); system bars use dark icons. */
@Composable
fun ManzilTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ManzilColorScheme, typography = ManzilTypography, content = content)
}
