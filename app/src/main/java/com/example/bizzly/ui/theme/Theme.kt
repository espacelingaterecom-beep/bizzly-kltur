package com.example.bizzly.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.R
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.model.BrandColors

val SoraFontFamily = FontFamily(
    Font(R.font.sora, FontWeight.Normal),
    Font(R.font.sora, FontWeight.Medium),
    Font(R.font.sora, FontWeight.SemiBold),
    Font(R.font.sora, FontWeight.Bold)
)

val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold)
)

@Immutable
data class BizzlyBrandTokens(
    val isDark: Boolean,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val onAccent: Color,
    val secondary: Color,
    val accentReadable: Color,
    val accentOnLight: Color,
    val tint: Color,
    val background: Color,
    val card: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val inputBg: Color,
    val ok: Color,
    val ko: Color,
    val warn: Color
)

private fun Triple<Int, Int, Int>.toComposeColor(): Color =
    Color(red = first, green = second, blue = third)

fun buildBrandTokens(brand: BrandColors, isDark: Boolean): BizzlyBrandTokens {
    val prRgb = BizzlyCalculator.hexToRgb(brand.pr, Triple(37, 99, 235))
    val secRgb = BizzlyCalculator.hexToRgb(brand.sec, Triple(10, 31, 68))
    val axRgb = BizzlyCalculator.hexToRgb(brand.ax, Triple(255, 107, 107))

    val onPrRgb = if (BizzlyCalculator.contrastRatio(prRgb, Triple(255, 255, 255)) >= 3.0) {
        Triple(255, 255, 255)
    } else {
        Triple(10, 31, 68)
    }
    val onAxRgb = if (BizzlyCalculator.contrastRatio(axRgb, Triple(255, 255, 255)) >= 3.0) {
        Triple(255, 255, 255)
    } else {
        Triple(10, 31, 68)
    }

    val cardBgRgb = if (isDark) Triple(24, 35, 58) else Triple(255, 255, 255)
    val acReadable = BizzlyCalculator.fitToContrast(prRgb, cardBgRgb, 4.2)
    val acLight = BizzlyCalculator.fitToContrast(prRgb, Triple(255, 255, 255), 4.2)
    val prColor = prRgb.toComposeColor()

    return BizzlyBrandTokens(
        isDark = isDark,
        primary = prColor,
        onPrimary = onPrRgb.toComposeColor(),
        accent = axRgb.toComposeColor(),
        onAccent = onAxRgb.toComposeColor(),
        secondary = secRgb.toComposeColor(),
        accentReadable = acReadable.toComposeColor(),
        accentOnLight = acLight.toComposeColor(),
        tint = prColor.copy(alpha = if (isDark) 0.16f else 0.09f),
        background = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
        card = if (isDark) Color(0xFF18233A) else Color(0xFFFFFFFF),
        text = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0A1F44),
        muted = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
        border = if (isDark) Color(0xFF2A3855) else Color(0xFFE2E8F0),
        inputBg = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC),
        ok = if (isDark) Color(0xFF34D399) else Color(0xFF16A34A),
        ko = if (isDark) Color(0xFFFF7C7C) else Color(0xFFE5484D),
        warn = Color(0xFFF59E0B)
    )
}

val LocalBizzlyTokens = staticCompositionLocalOf {
    buildBrandTokens(BrandColors(), isDark = false)
}

private val BizzlyTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = SoraFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = SoraFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = SoraFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.5.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

private val BizzlyShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

@Composable
fun BizzlyTheme(
    brand: BrandColors,
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    val tokens = buildBrandTokens(brand, isDark)
    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = tokens.primary,
            onPrimary = tokens.onPrimary,
            secondary = tokens.accent,
            onSecondary = tokens.onAccent,
            background = tokens.background,
            onBackground = tokens.text,
            surface = tokens.card,
            onSurface = tokens.text,
            surfaceVariant = tokens.inputBg,
            onSurfaceVariant = tokens.muted,
            outline = tokens.border,
            error = tokens.ko
        )
    } else {
        lightColorScheme(
            primary = tokens.primary,
            onPrimary = tokens.onPrimary,
            secondary = tokens.accent,
            onSecondary = tokens.onAccent,
            background = tokens.background,
            onBackground = tokens.text,
            surface = tokens.card,
            onSurface = tokens.text,
            surfaceVariant = tokens.inputBg,
            onSurfaceVariant = tokens.muted,
            outline = tokens.border,
            error = tokens.ko
        )
    }

    CompositionLocalProvider(LocalBizzlyTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BizzlyTypography,
            shapes = BizzlyShapes,
            content = content
        )
    }
}
