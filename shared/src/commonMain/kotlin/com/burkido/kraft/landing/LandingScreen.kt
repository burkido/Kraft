package com.burkido.kraft.landing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.burkido.kraft.designsystem.LocalViewport
import com.burkido.kraft.library.LibraryId
import kotlinx.coroutines.launch

/** libraries.dev's home page: nav, hero, logo row, library cards, quotes, how-to, FAQ, footer. */
@Composable
fun LandingScreen(
    onOpenLibrary: (LibraryId) -> Unit,
    scrollToHowTo: Boolean = false,
    onScrolledToHowTo: () -> Unit = {},
    onLicenses: () -> Unit = {},
) {
    val viewport = LocalViewport.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val anchors = remember { SectionAnchors() }
    var menuOpen by remember { mutableStateOf(false) }
    // `scroll-margin-top: 24px` on the card grid.
    val margin = with(density) { 24.dp.toPx() }
    val scrollTo: (LayoutCoordinates?) -> Unit = { target ->
        val viewport = anchors.viewport
        if (target != null && viewport != null && target.isAttached && viewport.isAttached) {
            val y = target.positionInRoot().y - viewport.positionInRoot().y + scroll.value - margin
            scope.launch { scroll.animateScrollTo(y.toInt().coerceAtLeast(0)) }
        }
    }

    // A library page's "How to use" lands here: wait for the section's first layout, then glide.
    LaunchedEffect(scrollToHowTo) {
        if (!scrollToHowTo) return@LaunchedEffect
        repeat(30) {
            if (anchors.howTo?.isAttached == true && anchors.viewport?.isAttached == true) return@repeat
            withFrameNanos { }
        }
        scrollTo(anchors.howTo)
        onScrolledToHowTo()
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { anchors.viewport = it }
                .verticalScroll(scroll)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // `.app`: max-width 1240, gutter 24 (12 on phones), 64 bottom (48).
            Column(
                Modifier
                    .widthIn(max = 1240.dp)
                    .fillMaxWidth()
                    .padding(horizontal = viewport.gutter)
                    .padding(bottom = if (viewport.maxWidth(639)) 48.dp else 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SiteNav(
                    onLibraries = { onOpenLibrary(LibraryId.Beam) },
                    onHowTo = { scrollTo(anchors.howTo) },
                    onGetStarted = { scrollTo(anchors.howTo) },
                    menuOpen = menuOpen,
                    onMenuToggle = { menuOpen = !menuOpen },
                )
                Hero(
                    onBrowse = { scrollTo(anchors.cards) },
                    onHowTo = { scrollTo(anchors.howTo) },
                    onOpenLibrary = onOpenLibrary,
                )
                CustomerLogos()
                LibraryCards(
                    onOpenLibrary = onOpenLibrary,
                    modifier = Modifier.onGloballyPositioned { anchors.cards = it },
                )
                KindWords()
                HowToUse(Modifier.onGloballyPositioned { anchors.howTo = it })
                Faq()
                Footer(
                    onOpenLibrary = onOpenLibrary,
                    onHowTo = { scrollTo(anchors.howTo) },
                    onHome = { scope.launch { scroll.animateScrollTo(0) } },
                    onLicenses = onLicenses,
                )
            }
        }
        MobileMenu(
            open = menuOpen,
            onDismiss = { menuOpen = false },
            onLibraries = { menuOpen = false; onOpenLibrary(LibraryId.Beam) },
            onHowTo = { menuOpen = false; scrollTo(anchors.howTo) },
        )
    }
}

/** Where the scroll viewport and the CTA targets currently are; read only when a CTA is tapped. */
private class SectionAnchors {
    var viewport: LayoutCoordinates? = null
    var cards: LayoutCoordinates? = null
    var howTo: LayoutCoordinates? = null
}
