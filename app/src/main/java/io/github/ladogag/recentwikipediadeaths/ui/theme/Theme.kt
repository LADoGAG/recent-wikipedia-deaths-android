package io.github.ladogag.recentwikipediadeaths.ui.theme

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable

@Composable
fun RecentWikipediaDeathsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val activity = LocalActivity.current!!
            if (darkTheme) dynamicDarkColorScheme(activity) else dynamicLightColorScheme(activity)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    val window = LocalActivity.current?.window
    if (window != null) {
        SideEffect {
            window.setBackgroundDrawable(colorScheme.background.toArgb().toDrawable())
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = Shapes,
        content = content
    )
}