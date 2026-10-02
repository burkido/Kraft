package com.burkido.kraft

import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSProcessInfo

fun MainViewController() = ComposeUIViewController {
    App(startLibrary = launchArgument("--library"))
}

/** The value following [flag] in the process arguments (e.g. `simctl launch … --library beam`). */
private fun launchArgument(flag: String): String? {
    val args = NSProcessInfo.processInfo.arguments.map { it.toString() }
    val i = args.indexOf(flag)
    return if (i >= 0) args.getOrNull(i + 1) else null
}
