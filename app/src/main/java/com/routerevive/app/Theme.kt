package com.routerevive.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** One palette for the app shell, forms, dialogs, and feature screens. */
private val RouteColors = darkColorScheme(
    primary = Color(0xFF9CDDAB),
    onPrimary = Color(0xFF103B27),
    primaryContainer = Color(0xFF214B36),
    onPrimaryContainer = Color(0xFFD1F5D9),
    secondary = Color(0xFFB0C8BF),
    onSecondary = Color(0xFF19382D),
    secondaryContainer = Color(0xFF294238),
    onSecondaryContainer = Color(0xFFD8EADF),
    tertiary = Color(0xFFE7C88E),
    onTertiary = Color(0xFF3B2D12),
    tertiaryContainer = Color(0xFF514023),
    onTertiaryContainer = Color(0xFFFFE3AF),
    background = Color(0xFF0D1715),
    onBackground = Color(0xFFEDF3EF),
    surface = Color(0xFF16231F),
    onSurface = Color(0xFFEDF3EF),
    surfaceVariant = Color(0xFF24352E),
    surfaceDim = Color(0xFF0D1715),
    surfaceBright = Color(0xFF2F4038),
    surfaceContainerLowest = Color(0xFF09120F),
    surfaceContainerLow = Color(0xFF13201B),
    surfaceContainer = Color(0xFF1A2A23),
    surfaceContainerHigh = Color(0xFF22322B),
    surfaceContainerHighest = Color(0xFF2C3C33),
    onSurfaceVariant = Color(0xFFB4C5BA),
    outline = Color(0xFF73877B),
    outlineVariant = Color(0xFF34483D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C2523),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE0EAE2),
    inverseOnSurface = Color(0xFF24352B),
    inversePrimary = Color(0xFF356B45),
    surfaceTint = Color(0xFF9CDDAB)
)

@Composable
fun RouteReviveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RouteColors,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(28.dp)
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}
