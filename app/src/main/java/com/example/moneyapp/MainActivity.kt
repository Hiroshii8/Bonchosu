package com.hiroshi.moneyapp

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.hiroshi.moneyapp.data.settings.ThemeMode
import com.hiroshi.moneyapp.ui.AppRoot
import com.hiroshi.moneyapp.ui.theme.MoneyTheme
import kotlinx.coroutines.runBlocking

// AppCompatActivity (not ComponentActivity) so the in-app language switch works on every Android version.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // A tiny settings read, done before the first frame so the app never flashes in the wrong theme.
        applyTheme(runBlocking { (application as MoneyApplication).container.settings.current().theme })
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MoneyTheme { AppRoot() }
        }
    }
}

/**
 * Light/dark goes through AppCompat's night mode, so the window background, system bars and
 * Compose's isSystemInDarkTheme() all agree. AppCompat recreates the screen when it changes.
 */
fun applyTheme(mode: ThemeMode) {
    val night = when (mode) {
        ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
    }
    if (AppCompatDelegate.getDefaultNightMode() != night) AppCompatDelegate.setDefaultNightMode(night)
}
