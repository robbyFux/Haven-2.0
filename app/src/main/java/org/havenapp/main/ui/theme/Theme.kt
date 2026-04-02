package org.havenapp.main.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val HavenDarkColorScheme = darkColorScheme(
    primary = HavenTeal200,
    onPrimary = HavenOnPrimary,
    primaryContainer = HavenTeal900,
    onPrimaryContainer = HavenTeal200,
    secondary = HavenBlueGrey400,
    onSecondary = HavenBackground,
    background = HavenBackground,
    surface = HavenSurface,
    surfaceVariant = HavenSurfaceVariant,
    outline = HavenOutline,
    onBackground = HavenOnSurface,
    onSurface = HavenOnSurface,
    onSurfaceVariant = HavenOnSurfaceVariant,
    error = HavenError,
    errorContainer = HavenErrorContainer,
    onError = HavenOnError,
)

private val HavenLightColorScheme = lightColorScheme(
    primary = HavenTeal600,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = HavenTeal900,
    secondary = HavenBlueGrey600,
    onSecondary = Color.White,
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    surfaceVariant = Color(0xFFECEFF1),
    onBackground = Color(0xFF1C1C1C),
    onSurface = Color(0xFF1C1C1C),
    error = Color(0xFFB00020),
    errorContainer = Color(0xFFFFDAD6),
)

@Composable
fun HavenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> HavenDarkColorScheme
        else -> HavenLightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
