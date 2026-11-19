package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

data class KashirakExtendedColors(
    val headerBg: Color,
    val heroBg: Color,
    val heroFg: Color,
    val gold: Color,
    val goldDarkText: Color,
    val cardBg: Color,
    val navBg: Color,
    val chipBg: Color,
    val mutedText: Color,
    val dividerLine: Color,
    val okBg: Color,
    val okFg: Color,
    val badFg: Color,
    val warnBg: Color,
    val warnFg: Color,
    val bannerBg: Color,
    val bannerBorder: Color
)

val LocalKashirakColors = staticCompositionLocalOf {
    KashirakExtendedColors(
        headerBg = NavyPrimary,
        heroBg = NavyHero,
        heroFg = HeroFg,
        gold = GoldAccent,
        goldDarkText = GoldDarkText,
        cardBg = CardLight,
        navBg = CardLight,
        chipBg = ChipLight,
        mutedText = MutedLight,
        dividerLine = LineLight,
        okBg = OkBgLight,
        okFg = OkFgLight,
        badFg = BadLight,
        warnBg = WarnBgLight,
        warnFg = WarnFgLight,
        bannerBg = TrialBgLight,
        bannerBorder = TrialLineLight
    )
}

private val LightColors = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = Color.White,
    primaryContainer = GoldAccent,
    onPrimaryContainer = NavyPrimary,
    secondary = GoldDarkText,
    onSecondary = Color.White,
    secondaryContainer = ChipLight,
    onSecondaryContainer = FgLight,
    background = BgLight,
    onBackground = FgLight,
    surface = CardLight,
    onSurface = FgLight,
    surfaceVariant = ChipLight,
    onSurfaceVariant = MutedLight,
    outline = LineLight,
    error = BadLight,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = GoldAccent,
    onPrimary = NavyPrimary,
    primaryContainer = NavyHeroDark,
    onPrimaryContainer = Color.White,
    secondary = GoldAccent,
    onSecondary = NavyPrimary,
    secondaryContainer = ChipDark,
    onSecondaryContainer = FgDark,
    background = BgDark,
    onBackground = FgDark,
    surface = CardDark,
    onSurface = FgDark,
    surfaceVariant = ChipDark,
    onSurfaceVariant = MutedDark,
    outline = LineDark,
    error = BadDark,
    onError = NavyPrimary
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KashirakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extendedColors = if (darkTheme) {
        KashirakExtendedColors(
            headerBg = NavyHeadDark,
            heroBg = NavyHeroDark,
            heroFg = HeroFg,
            gold = GoldAccent,
            goldDarkText = GoldAccent,
            cardBg = CardDark,
            navBg = NavDark,
            chipBg = ChipDark,
            mutedText = MutedDark,
            dividerLine = LineDark,
            okBg = OkBgDark,
            okFg = OkFgDark,
            badFg = BadDark,
            warnBg = TrialBgDark,
            warnFg = GoldAccent,
            bannerBg = TrialBgDark,
            bannerBorder = TrialLineDark
        )
    } else {
        KashirakExtendedColors(
            headerBg = NavyPrimary,
            heroBg = NavyHero,
            heroFg = HeroFg,
            gold = GoldAccent,
            goldDarkText = GoldDarkText,
            cardBg = CardLight,
            navBg = CardLight,
            chipBg = ChipLight,
            mutedText = MutedLight,
            dividerLine = LineLight,
            okBg = OkBgLight,
            okFg = OkFgLight,
            badFg = BadLight,
            warnBg = WarnBgLight,
            warnFg = WarnFgLight,
            bannerBg = TrialBgLight,
            bannerBorder = TrialLineLight
        )
    }

    CompositionLocalProvider(
        LocalKashirakColors provides extendedColors,
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
