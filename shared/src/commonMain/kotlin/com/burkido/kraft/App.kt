package com.burkido.kraft

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.burkido.kraft.designsystem.KraftColors
import com.burkido.kraft.designsystem.KraftTheme
import com.burkido.kraft.designsystem.LocalViewport
import com.burkido.kraft.designsystem.Viewport
import com.burkido.kraft.landing.LandingScreen
import com.burkido.kraft.landing.LicensesScreen
import com.burkido.kraft.library.LibraryId
import com.burkido.kraft.library.LibraryScreen
import com.burkido.kraft.navigation.Route

/**
 * The app root. [startLibrary] opens a library page (or `licenses`) directly (launch argument `--library <name>`),
 * which screenshots and parity recordings use.
 */
@Composable
fun App(startLibrary: String? = null) {
    KraftTheme {
        BoxWithConstraints(Modifier.fillMaxSize().background(KraftColors.Background)) {
            CompositionLocalProvider(LocalViewport provides Viewport(maxWidth, maxHeight)) {
                val backStack = remember {
                    val start = LibraryId.entries.firstOrNull { it.name.equals(startLibrary, ignoreCase = true) }
                    mutableStateListOf<Route>(Route.Landing).apply {
                        if (start != null) add(Route.Library(start))
                        if (startLibrary.equals("licenses", ignoreCase = true)) add(Route.Licenses)
                    }
                }
                var scrollToHowTo by remember { mutableStateOf(false) }
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = entryProvider {
                        entry<Route.Landing> {
                            LandingScreen(
                                onOpenLibrary = { backStack.add(Route.Library(it)) },
                                scrollToHowTo = scrollToHowTo,
                                onScrolledToHowTo = { scrollToHowTo = false },
                                onLicenses = { backStack.add(Route.Licenses) },
                            )
                        }
                        entry<Route.Library> { route ->
                            LibraryScreen(
                                id = route.id,
                                onSelectLibrary = { backStack[backStack.lastIndex] = Route.Library(it) },
                                onBack = { backStack.removeLastOrNull() },
                                onHowTo = {
                                    scrollToHowTo = true
                                    while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                                },
                                onLicenses = { backStack.add(Route.Licenses) },
                            )
                        }
                        entry<Route.Licenses> {
                            val home = { while (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
                            LicensesScreen(
                                onOpenLibrary = { home(); backStack.add(Route.Library(it)) },
                                onHome = home,
                                onHowTo = {
                                    scrollToHowTo = true
                                    home()
                                },
                            )
                        }
                    },
                )
            }
        }
    }
}
