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
    val surfaceTint: Color,
    val background: Color,
    val card: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val inputBg: Color,
    val headerStart: Color,
    val headerEnd: Color,
    val onHeader: Color,
    val onHeaderMuted: Color,
    val headerControlBg: Color,
    val headerControlBorder: Color,
    val heroHighlightStart: Color,
    val heroHighlightEnd: Color,
    val onHeroHighlight: Color,
    val navContainer: Color,
    val navBorder: Color,
    val navIndicator: Color,
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

    val onPrRgb = if (BizzlyCalculator.contrastRatio(prRgb, Triple(255, 255, 255)) >= 3.2) {
        Triple(255, 255, 255)
    } else {
        Triple(10, 22, 42)
    }
    val onAxRgb = if (BizzlyCalculator.contrastRatio(axRgb, Triple(255, 255, 255)) >= 3.2) {
        Triple(255, 255, 255)
    } else {
        Triple(10, 22, 42)
    }

    val bgRgb = if (isDark) {
        BizzlyCalculator.mixRgb(Triple(10, 15, 28), BizzlyCalculator.mixRgb(secRgb, prRgb, 0.34), 0.34)
    } else {
        BizzlyCalculator.mixRgb(Triple(247, 249, 252), prRgb, 0.085)
    }
    val cardRgb = if (isDark) {
        BizzlyCalculator.mixRgb(Triple(20, 27, 45), BizzlyCalculator.mixRgb(secRgb, prRgb, 0.24), 0.30)
    } else {
        BizzlyCalculator.mixRgb(Triple(255, 255, 255), prRgb, 0.025)
    }
    val surfaceTintRgb = if (isDark) {
        BizzlyCalculator.mixRgb(cardRgb, prRgb, 0.16)
    } else {
        BizzlyCalculator.mixRgb(Triple(255, 255, 255), prRgb, 0.115)
    }
    val borderRgb = if (isDark) {
        BizzlyCalculator.mixRgb(Triple(42, 56, 85), prRgb, 0.30)
    } else {
        BizzlyCalculator.mixRgb(Triple(220, 227, 238), prRgb, 0.24)
    }
    val inputBgRgb = if (isDark) {
        BizzlyCalculator.mixRgb(bgRgb, secRgb, 0.24)
    } else {
        BizzlyCalculator.mixRgb(Triple(246, 248, 252), prRgb, 0.065)
    }

    val textRgb = if (isDark) {
        BizzlyCalculator.mixRgb(Triple(248, 250, 252), prRgb, 0.04)
    } else {
        BizzlyCalculator.fitToContrast(
            BizzlyCalculator.mixRgb(secRgb, Triple(12, 18, 32), 0.35),
            cardRgb,
            7.2
        )
    }
    val mutedRgb = if (isDark) {
        BizzlyCalculator.fitToContrast(
            BizzlyCalculator.mixRgb(Triple(152, 168, 192), prRgb, 0.12),
            cardRgb,
            4.5
        )
    } else {
        BizzlyCalculator.fitToContrast(
            BizzlyCalculator.mixRgb(secRgb, Triple(100, 116, 139), 0.72),
            cardRgb,
            4.5
        )
    }

    val headerStartRgb = if (isDark) {
        BizzlyCalculator.mixRgb(secRgb, prRgb, 0.42)
    } else {
        prRgb
    }
    val headerEndRgb = if (isDark) {
        BizzlyCalculator.mixRgb(secRgb, Triple(6, 10, 20), 0.52)
    } else {
        BizzlyCalculator.mixRgb(prRgb, secRgb, 0.56)
    }
    val headerMidRgb = BizzlyCalculator.mixRgb(headerStartRgb, headerEndRgb, 0.5)
    val whiteLightOnHeader = BizzlyCalculator.contrastRatio(headerMidRgb, Triple(255, 255, 255)) >= 3.3
    val onHeaderRgb = if (whiteLightOnHeader) Triple(255, 255, 255) else Triple(10, 22, 42)
    val onHeaderColor = onHeaderRgb.toComposeColor()

    val heroStartRgb = if (isDark) {
        BizzlyCalculator.mixRgb(prRgb, Triple(0, 0, 0), 0.16)
    } else {
        BizzlyCalculator.mixRgb(secRgb, prRgb, 0.32)
    }
    val heroEndRgb = if (isDark) {
        secRgb
    } else {
        BizzlyCalculator.mixRgb(secRgb, Triple(0, 0, 0), 0.25)
    }
    val heroMidRgb = BizzlyCalculator.mixRgb(heroStartRgb, heroEndRgb, 0.5)
    val onHeroRgb = if (BizzlyCalculator.contrastRatio(heroMidRgb, Triple(255, 255, 255)) >= 3.3) {
        Triple(255, 255, 255)
    } else {
        Triple(10, 22, 42)
    }

    val navContainerRgb = if (isDark) {
        BizzlyCalculator.mixRgb(cardRgb, secRgb, 0.40)
    } else {
        BizzlyCalculator.mixRgb(Triple(255, 255, 255), prRgb, 0.07)
    }
    val navIndicatorRgb = if (isDark) {
        BizzlyCalculator.mixRgb(cardRgb, prRgb, 0.34)
    } else {
        BizzlyCalculator.mixRgb(Triple(255, 255, 255), prRgb, 0.18)
    }

    val acReadable = BizzlyCalculator.fitToContrast(prRgb, cardRgb, 4.2)
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
        tint = prColor.copy(alpha = if (isDark) 0.18f else 0.11f),
        surfaceTint = surfaceTintRgb.toComposeColor(),
        background = bgRgb.toComposeColor(),
        card = cardRgb.toComposeColor(),
        text = textRgb.toComposeColor(),
        muted = mutedRgb.toComposeColor(),
        border = borderRgb.toComposeColor(),
        inputBg = inputBgRgb.toComposeColor(),
        headerStart = headerStartRgb.toComposeColor(),
        headerEnd = headerEndRgb.toComposeColor(),
        onHeader = onHeaderColor,
        onHeaderMuted = onHeaderColor.copy(alpha = 0.80f),
        headerControlBg = onHeaderColor.copy(alpha = if (whiteLightOnHeader) 0.16f else 0.10f),
        headerControlBorder = onHeaderColor.copy(alpha = if (whiteLightOnHeader) 0.28f else 0.20f),
        heroHighlightStart = heroStartRgb.toComposeColor(),
        heroHighlightEnd = heroEndRgb.toComposeColor(),
        onHeroHighlight = onHeroRgb.toComposeColor(),
        navContainer = navContainerRgb.toComposeColor(),
        navBorder = borderRgb.toComposeColor(),
        navIndicator = navIndicatorRgb.toComposeColor(),
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
            primaryContainer = tokens.surfaceTint,
            onPrimaryContainer = tokens.text,
            secondary = tokens.accent,
            onSecondary = tokens.onAccent,
            secondaryContainer = tokens.navIndicator,
            onSecondaryContainer = tokens.text,
            tertiary = tokens.accent,
            onTertiary = tokens.onAccent,
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
            primaryContainer = tokens.surfaceTint,
            onPrimaryContainer = tokens.text,
            secondary = tokens.accent,
            onSecondary = tokens.onAccent,
            secondaryContainer = tokens.navIndicator,
            onSecondaryContainer = tokens.text,
            tertiary = tokens.accent,
            onTertiary = tokens.onAccent,
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
