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

        // `adb shell am start -n com.burkido.kraft/.MainActivity --es library beam`, plus
        // `--ez blurFallback true` and `--ef frozenTime 1.5` to compare the Android < 12 blur.
        val startLibrary = intent?.getStringExtra("library")
        val forceBlurFallback = intent?.getBooleanExtra("blurFallback", false) ?: false
        val frozenTime = intent?.takeIf { it.hasExtra("frozenTime") }?.getFloatExtra("frozenTime", 0f)?.toDouble()
        setContent {
            App(startLibrary = startLibrary, forceBlurFallback = forceBlurFallback, frozenTime = frozenTime)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
