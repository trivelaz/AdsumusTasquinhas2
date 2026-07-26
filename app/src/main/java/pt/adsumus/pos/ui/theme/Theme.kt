package pt.adsumus.pos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val Colors = darkColorScheme(
    primary = Gold,
    onPrimary = Black,
    primaryContainer = GoldDeep,
    onPrimaryContainer = White,

    secondary = GoldLight,
    onSecondary = Black,
    secondaryContainer = CharcoalLight,
    onSecondaryContainer = Cream,

    tertiary = Success,
    onTertiary = Black,

    error = Danger,
    onError = White,

    background = Black,
    onBackground = White,

    surface = CharcoalDark,
    onSurface = White,
    surfaceVariant = Charcoal,
    onSurfaceVariant = Cream,

    outline = GoldDeep
)

@Composable
fun ADSUMUSTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = Colors,
        shapes = ADSUMUSShapes,
        typography = ADSUMUSTypography,
        content = content
    )
}
