package com.shaderstudio.app.ui.theme

import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val BrandDarkColors = darkColorScheme(
    primary = Color(0xFFBCC2FF),
    onPrimary = Color(0xFF1A2280),
    primaryContainer = Color(0xFF3A47C4),
    onPrimaryContainer = Color(0xFFE0E0FF),
    secondary = Color(0xFFFFB68A),
    onSecondary = Color(0xFF54250A),
    secondaryContainer = Color(0xFF9A4E1E),
    onSecondaryContainer = Color(0xFFFFDBC7),
    tertiary = Color(0xFFD9B9FF),
    onTertiary = Color(0xFF421A70),
    tertiaryContainer = Color(0xFF7C4DBE),
    onTertiaryContainer = Color(0xFFF0DBFF),
    background = Color(0xFF0E0F14),
    onBackground = Color(0xFFE4E1EA),
    surface = Color(0xFF0E0F14),
    onSurface = Color(0xFFE4E1EA),
    surfaceVariant = Color(0xFF23242E),
    onSurfaceVariant = Color(0xFFC6C4D2),
    surfaceContainer = Color(0xFF191A21),
    surfaceContainerHigh = Color(0xFF20222B),
    surfaceContainerHighest = Color(0xFF2A2C37),
    outline = Color(0xFF8F8D9C),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShaderStudioTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        BrandDarkColors
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = ShaderStudioTypography,
        content = content,
    )
}
