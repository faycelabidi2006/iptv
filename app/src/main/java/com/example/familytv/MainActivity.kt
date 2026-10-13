package com.example.familytv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Pal.violet,
                    onPrimary = Color.White,
                    secondary = Pal.blue,
                    background = Pal.bgBottom,
                    surface = Color(0xFF12162A),
                    onSurface = Color.White,
                    surfaceVariant = Color(0xFF1C2140),
                    onSurfaceVariant = Color(0xCCFFFFFF),
                    surfaceContainerHigh = Color(0xFF181D36),
                    error = Color(0xFFFF6B81)
                )
            ) { AppRoot() }
        }
    }
}
