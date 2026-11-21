package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val VoxDarkColorScheme = darkColorScheme(
    primary = ElectricIndigoBright,
    onPrimary = Color(0xFF0E0B29),
    primaryContainer = ElectricIndigoContainer,
    onPrimaryContainer = Color(0xFFE3E0FF),
    secondary = VoiceCyan,
    onSecondary = Color(0xFF002620),
    secondaryContainer = VoiceCyanContainer,
    onSecondaryContainer = Color(0xFFB5FFF4),
    tertiary = ModAmber,
    onTertiary = Color(0xFF2B1B00),
    tertiaryContainer = ModAmberContainer,
    onTertiaryContainer = Color(0xFFFFE2A8),
    error = LiveCoral,
    onError = Color.White,
    errorContainer = LiveCoralContainer,
    onErrorContainer = Color(0xFFFFD9DF),
    background = StudioObsidian,
    onBackground = TextPrimaryDark,
    surface = StudioMidnight,
    onSurface = TextPrimaryDark,
    surfaceVariant = StudioSlate,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = StudioObsidian,
    surfaceContainerLow = StudioMidnight,
    surfaceContainer = StudioSlate,
    surfaceContainerHigh = StudioElevated,
    surfaceContainerHighest = Color(0xFF2B3755),
    outline = StudioOutline,
    outlineVariant = StudioOutlineSubtle
)

private val VoxLightColorScheme = lightColorScheme(
    primary = ElectricIndigoLight,
    onPrimary = Color.White,
    primaryContainer = ElectricIndigoContainerLight,
    onPrimaryContainer = Color(0xFF1D166B),
    secondary = VoiceTealLight,
    onSecondary = Color.White,
    secondaryContainer = VoiceTealContainerLight,
    onSecondaryContainer = Color(0xFF00372E),
    tertiary = Color(0xFFC47A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEBC2),
    onTertiaryContainer = Color(0xFF3E2500),
    error = LiveCoralLight,
    onError = Color.White,
    errorContainer = LiveCoralContainerLight,
    onErrorContainer = Color(0xFF5A0016),
    background = StudioPaper,
    onBackground = TextPrimaryLight,
    surface = StudioSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = StudioSurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = StudioOutlineLight,
    outlineVariant = Color(0xFFE1E7F5)
)

val VoxStageShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VoxStageTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VoxDarkColorScheme else VoxLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = VoxStageShapes,
        content = content
    )
}
