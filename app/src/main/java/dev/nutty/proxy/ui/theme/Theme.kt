package dev.nutty.proxy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle

/**
 * M3 scheme mapped onto the Nutty palette.
 *
 * The app draws its own surfaces from [NuttyColor] directly — this scheme exists
 * so any Material component pulled in later (ripples, text selection, a date
 * picker) lands inside the palette instead of on top of it.
 *
 * Note `primary` is the near-white [NuttyColor.TextPrimary]: in this design the
 * primary action is a white slab on black, and colour is reserved for state.
 */
private val NuttyColorScheme = darkColorScheme(
    primary = NuttyColor.TextPrimary,
    onPrimary = NuttyColor.Bg,
    primaryContainer = NuttyColor.SurfaceRaised,
    onPrimaryContainer = NuttyColor.TextPrimary,

    secondary = NuttyColor.TextMuted,
    onSecondary = NuttyColor.Bg,
    secondaryContainer = NuttyColor.Surface,
    onSecondaryContainer = NuttyColor.TextSecondary,

    tertiary = NuttyColor.Green,
    onTertiary = NuttyColor.Bg,
    tertiaryContainer = NuttyColor.GreenContainer,
    onTertiaryContainer = NuttyColor.GreenText,

    background = NuttyColor.Bg,
    onBackground = NuttyColor.TextPrimary,
    surface = NuttyColor.Surface,
    onSurface = NuttyColor.TextPrimary,
    surfaceVariant = NuttyColor.SurfaceRaised,
    onSurfaceVariant = NuttyColor.TextMuted,
    surfaceContainer = NuttyColor.Surface,
    surfaceContainerHigh = NuttyColor.SurfaceRaised,
    surfaceContainerLow = NuttyColor.SurfaceMuted,
    inverseSurface = NuttyColor.TextPrimary,
    inverseOnSurface = NuttyColor.Bg,

    error = NuttyColor.Red,
    onError = NuttyColor.Bg,
    errorContainer = NuttyColor.RedContainer,
    onErrorContainer = NuttyColor.RedTextSoft,

    outline = NuttyColor.OutlineFocus,
    outlineVariant = NuttyColor.Outline,
    scrim = NuttyColor.Scrim,
)

private val NuttyTypography = Typography(
    displayLarge = NuttyType.Clock,
    headlineLarge = NuttyType.OnboardTitle,
    headlineMedium = NuttyType.ResultTitle,
    headlineSmall = NuttyType.ScreenTitle,
    titleLarge = NuttyType.SheetTitle,
    titleMedium = NuttyType.StatusTitle,
    titleSmall = NuttyType.CardTitle,
    bodyLarge = NuttyType.Body,
    bodyMedium = NuttyType.Item,
    bodySmall = NuttyType.Caption,
    labelLarge = NuttyType.Button,
    labelMedium = NuttyType.Chip,
    labelSmall = NuttyType.Tab,
)

/**
 * Dark-only by design — there is no `darkTheme` parameter to pass, because there
 * is no other mode to switch to.
 */
@Composable
fun NuttyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NuttyColorScheme,
        typography = NuttyTypography,
        content = content,
    )
}

/** Convenience for the many places that draw a value in Mono at row size. */
val ValueStyle: TextStyle get() = NuttyType.Value
