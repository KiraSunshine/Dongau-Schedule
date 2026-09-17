package com.jetbrains.kmpapp

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jetbrains.kmpapp.data.model.ThemeMode
import com.jetbrains.kmpapp.theme.ThemeOverlay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (isDark, overlay) = readPersistedTheme()
        window.setBackgroundDrawable(ColorDrawable(resolveWindowBackground(isDark, overlay)))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark },
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark }
        )
        setContent {
            App()
        }
    }

    private fun readPersistedTheme(): Pair<Boolean, ThemeOverlay> {
        val prefs = getSharedPreferences("dongau_schedule_cache", MODE_PRIVATE)
        val mode = prefs.getString("dongau_app_theme", null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val isDark = when (mode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        val overlay = prefs.getString("dongau_theme_overlay", null)
            ?.let { runCatching { ThemeOverlay.valueOf(it) }.getOrNull() }
            ?: when {
                prefs.getString("dongau_matrix_theme_secret", null)?.toBoolean() == true -> ThemeOverlay.MATRIX
                prefs.getString("dongau_cyberpunk_theme_secret", null)?.toBoolean() == true -> ThemeOverlay.CYBERPUNK
                prefs.getString("dongau_sakura_theme_secret", null)?.toBoolean() == true -> ThemeOverlay.SAKURA
                else -> ThemeOverlay.NONE
            }
        return isDark to overlay
    }

    private fun resolveWindowBackground(isDark: Boolean, overlay: ThemeOverlay): Int =
        when (overlay) {
            ThemeOverlay.SAKURA -> if (isDark) 0xFF1B1114 else 0xFFFFF7F9
            ThemeOverlay.CYBERPUNK -> if (isDark) 0xFF090B18 else 0xFFFFF7FF
            ThemeOverlay.MATRIX -> if (isDark) 0xFF030505 else 0xFFF7FCF3
            ThemeOverlay.NONE -> if (isDark) 0xFF0B0B12 else 0xFFFFFFFF
        }.toInt()
}
