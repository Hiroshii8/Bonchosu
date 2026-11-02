package com.example.moneyapp

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.moneyapp.ui.AppRoot
import com.example.moneyapp.ui.theme.MoneyTheme

// AppCompatActivity (not ComponentActivity) so the in-app language switch works on every Android version.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MoneyTheme { AppRoot() }
        }
    }
}
