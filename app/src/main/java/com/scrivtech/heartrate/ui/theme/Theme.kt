package com.scrivtech.heartrate.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Action,
    onPrimary = OnAction,
    secondary = TextMuted,
    background = Ground,
    onBackground = TextPrimary,
    surface = Ground,
    onSurface = TextPrimary,
    onSurfaceVariant = TextMuted,
    surfaceContainerHigh = SurfaceDialog,
    outline = Outline,
    outlineVariant = Hairline,
    error = TextPrimary
)

/** Spacing and size tokens from DESIGN.md. */
object Dimens {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val ScreenEdge = 20.dp
    val Section = 28.dp
    val MinTouch = 48.dp
    val PrimaryButton = 56.dp
    val ListRow = 64.dp
}

val ShapeMd = RoundedCornerShape(4.dp)
val ShapeSm = RoundedCornerShape(2.dp)

private val HrmShapes = Shapes(
    extraSmall = ShapeMd,
    small = ShapeMd,
    medium = ShapeMd,
    large = ShapeMd,
    extraLarge = ShapeMd
)

@Composable
fun HeartRateMirrorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = HrmTypography,
        shapes = HrmShapes,
        content = content
    )
}
