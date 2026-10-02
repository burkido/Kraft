package com.burkido.kraft

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is always dark, so the system bars always carry light icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // `adb shell am start -n com.burkido.kraft/.MainActivity --es library beam`
        val startLibrary = intent?.getStringExtra("library")
        setContent {
            App(startLibrary = startLibrary)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
