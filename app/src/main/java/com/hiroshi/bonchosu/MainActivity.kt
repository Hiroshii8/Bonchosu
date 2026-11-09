package com.hiroshi.bonchosu

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import com.hiroshi.bonchosu.data.settings.AppSettings
import com.hiroshi.bonchosu.ui.AppRoot
import com.hiroshi.bonchosu.ui.theme.Brand
import com.hiroshi.bonchosu.ui.theme.BonchosuTheme
import com.hiroshi.bonchosu.ui.theme.ThemeSpec
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

// AppCompatActivity (not ComponentActivity) so the in-app language switch works on every Android version.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The theme is drawn by Compose from the app's own settings, so Android's night mode just follows the phone.
        // (Forcing it would make "System" wrong and restart the screen on every change.)
        if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        val settings = (application as BonchosuApplication).container.settings
        // A tiny settings read before the first frame, so the app never flashes in the wrong theme.
        val initial = runBlocking { settings.current() }.themeSpec()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val spec by remember { settings.settings.map { it.themeSpec() } }.collectAsState(initial)
            BonchosuTheme(spec) {
                val brand = Brand
                // Status/navigation bar icons and the window behind the app follow the app's theme, not the phone's.
                LaunchedEffect(brand.isDark, brand.Bg) {
                    val clear = AndroidColor.TRANSPARENT
                    enableEdgeToEdge(
                        statusBarStyle = if (brand.isDark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear),
                        navigationBarStyle = if (brand.isDark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear),
                    )
                    window.setBackgroundDrawable(ColorDrawable(brand.Bg.toArgb()))
                }
                AppRoot()
            }
        }
    }
}

fun AppSettings.themeSpec() = ThemeSpec(mode = theme, amoled = amoled, accent = accent, dynamic = dynamicColor, scale = uiSize.scale)
