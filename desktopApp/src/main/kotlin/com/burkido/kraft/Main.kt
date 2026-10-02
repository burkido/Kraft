package com.burkido.kraft

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/** `./gradlew :desktopApp:run --args="--library beam"` opens a library page directly. */
fun main(args: Array<String>) = application {
    val flag = args.indexOf("--library")
    val startLibrary = if (flag >= 0) args.getOrNull(flag + 1) else null
    Window(
        onCloseRequest = ::exitApplication,
        title = "Kraft",
        state = rememberWindowState(width = 1280.dp, height = 900.dp),
    ) {
        App(startLibrary = startLibrary)
    }
}
