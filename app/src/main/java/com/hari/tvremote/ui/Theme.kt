package com.hari.tvremote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** High-contrast colours and larger type, for easy reading on any phone. */
private val Light = lightColorScheme(
    primary = Color(0xFF1946C8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE3FF),
    onPrimaryContainer = Color(0xFF0A1F66),
    background = Color(0xFFF4F5F9),
    onBackground = Color(0xFF15171C),
    surface = Color.White,
    onSurface = Color(0xFF15171C),
    surfaceVariant = Color(0xFFE4E7EF),
    onSurfaceVariant = Color(0xFF242833),
    error = Color(0xFFC62828),
    onError = Color.White,
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9DB2FF),
    onPrimary = Color(0xFF0A1A52),
    primaryContainer = Color(0xFF263B85),
    onPrimaryContainer = Color(0xFFDCE3FF),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFEDEFF4),
    surface = Color(0xFF171A20),
    onSurface = Color(0xFFEDEFF4),
    surfaceVariant = Color(0xFF262A33),
    onSurfaceVariant = Color(0xFFE6E9F0),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0000),
)

private val BigType = Typography().run {
    copy(
        titleLarge = titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontSize = 20.sp),
        bodyMedium = bodyMedium.copy(fontSize = 17.sp),
        labelLarge = labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
        labelMedium = labelMedium.copy(fontSize = 15.sp),
    )
}

@Composable
fun TvRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = BigType,
        content = content,
    )
}

/** Power-button red. */
val PowerRed = Color(0xFFD32F2F)
