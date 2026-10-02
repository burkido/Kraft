package com.burkido.kraft.landing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.burkido.kraft.designsystem.KraftColors
import com.burkido.kraft.designsystem.KraftIcon
import com.burkido.kraft.designsystem.KraftIcons
import com.burkido.kraft.designsystem.KraftMotion
import com.burkido.kraft.designsystem.LocalKraftFonts
import com.burkido.kraft.designsystem.LocalViewport
import com.burkido.kraft.designsystem.clickableRaw
import com.burkido.kraft.designsystem.cssBlur
import com.burkido.kraft.designsystem.cssSurface
import com.burkido.kraft.designsystem.cssText
import com.burkido.kraft.designsystem.rememberInteraction
import com.burkido.kraft.library.LibraryId
import com.burkido.kraft.library.Pg
import com.burkido.kraft.resources.Res

/** One third-party work the app is built on, and the file holding its licence text. */
private class Notice(val name: String, val url: String, val holder: String, val license: String, val usedFor: String, val file: String)

private val Notices = listOf(
    Notice(
        "Libraries.dev", "https://github.com/Jakubantalik/Libraries.dev", "Copyright (c) 2026 Jakub Antalik", "MIT License",
        "The seven effects and this app are ports of its packages and site, with its spec data, bot shapes and demo images.",
        "LibrariesDev-MIT.txt",
    ),
    Notice(
        "Paper Shaders", "https://github.com/paper-design/shaders", "Copyright (c) Paper Design, Inc.", "Apache License 2.0",
        "The metal material is a Kotlin port of its liquidMetal shader, evaluated on the CPU.",
        "PaperShaders-Apache-2.0.txt",
    ),
    Notice(
        "Inter", "https://github.com/rsms/inter", "Copyright (c) 2016 The Inter Project Authors", "SIL Open Font License 1.1",
        "Display and body type.",
        "Inter-OFL.txt",
    ),
    Notice(
        "Roboto Mono", "https://github.com/googlefonts/robotomono", "Copyright 2015 The Roboto Mono Project Authors", "SIL Open Font License 1.1",
        "Code snippets.",
        "RobotoMono-OFL.txt",
    ),
)

/**
 * The Legal page: who made what this app ports, then each third-party work with its licence
 * text behind an accordion row, laid out like a library page's main column.
 */
@Composable
fun LicensesScreen(onOpenLibrary: (LibraryId) -> Unit, onHome: () -> Unit, onHowTo: () -> Unit) {
    val vp = LocalViewport.current
    val fonts = LocalKraftFonts.current
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 1240.dp).fillMaxWidth().padding(horizontal = vp.gutter)) {
                SiteNav(
                    onLibraries = { onOpenLibrary(LibraryId.Beam) },
                    onHowTo = onHowTo,
                    onGetStarted = onHowTo,
                    menuOpen = menuOpen,
                    onMenuToggle = { menuOpen = !menuOpen },
                )
            }
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = vp.gutter).padding(top = 48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BasicText("Licenses", style = cssText(fonts.display, 36f, 42f, FontWeight.Medium, KraftColors.Text, (-0.18).sp))
                BasicText(
                    "Kraft is a Compose Multiplatform port of Libraries.dev. These are the works it is built on and the terms they come with.",
                    style = cssText(fonts.sans, 16f, 24f, FontWeight.Normal, KraftColors.TextMuted),
                )
                Credits()
                Column(Modifier.padding(top = 20.dp)) {
                    Notices.forEachIndexed { i, n -> NoticeRow(n, last = i == Notices.lastIndex) }
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = vp.gutter), contentAlignment = Alignment.TopCenter) {
                Footer(onOpenLibrary = onOpenLibrary, onHowTo = onHowTo, onHome = onHome)
            }
        }
        MobileMenu(
            open = menuOpen,
            onDismiss = { menuOpen = false },
            onLibraries = { menuOpen = false; onOpenLibrary(LibraryId.Beam) },
            onHowTo = { menuOpen = false; onHowTo() },
        )
    }
}

/** The site's own credits, which travel with the port. */
@Composable
private fun Credits() {
    val fonts = LocalKraftFonts.current
    Column(
        Modifier.padding(top = 20.dp).fillMaxWidth().cssSurface(16.dp) { KraftColors.Card }.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText("Credits", style = cssText(fonts.sans, 15f, 22f, FontWeight.Medium, KraftColors.Text))
        BasicText(
            "Libraries.dev, its effects and its design are by Jakub Antalik, made with help from Alexandr Brinza (Orbs) " +
                "and Martin Petercak (Metal). The liquid metal material is by Paper Design.",
            style = cssText(fonts.sans, 14f, 22f, FontWeight.Normal, KraftColors.TextMuted),
        )
    }
}

/** `.t-acc`-style row: name and licence; opening it shows the full text, loaded on first open. */
@Composable
private fun NoticeRow(n: Notice, last: Boolean) {
    val fonts = LocalKraftFonts.current
    val uri = LocalUriHandler.current
    var open by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(open) {
        if (open && text == null) text = Res.readBytes("files/licenses/${n.file}").decodeToString()
    }
    val chevron by animateFloatAsState(if (open) -1f else 1f, tween(KraftMotion.Fast, easing = KraftMotion.SmoothOut))
    val reveal by animateFloatAsState(if (open) 1f else 0f, tween(KraftMotion.Fast, easing = KraftMotion.SmoothOut))
    val interaction = rememberInteraction()
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRect(KraftColors.Hairline, Offset.Zero, size.copy(height = stroke))
                if (last) drawRect(KraftColors.Hairline, Offset(0f, size.height - stroke), size.copy(height = stroke))
            },
    ) {
        Row(
            Modifier.fillMaxWidth().clickableRaw(interaction) { open = !open }.padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(n.name, style = cssText(fonts.sans, 15f, 22f, FontWeight.Normal, KraftColors.Text))
                BasicText("${n.license} · ${n.holder}", style = cssText(fonts.sans, 13f, 18f, FontWeight.Normal, KraftColors.TextFaint))
            }
            KraftIcon(KraftIcons.ChevronDown, KraftColors.TextMuted, Modifier.graphicsLayer { scaleY = chevron })
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(KraftMotion.Fast, easing = KraftMotion.SmoothOut)),
            exit = shrinkVertically(tween(KraftMotion.Fast, easing = KraftMotion.SmoothOut)),
        ) {
            Column(
                Modifier.padding(bottom = 16.dp).graphicsLayer { alpha = reveal }.cssBlur(((1f - reveal) * 2f).dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BasicText(n.usedFor, style = cssText(fonts.sans, 14f, 22f, FontWeight.Normal, KraftColors.TextMuted))
                val linkInteraction = rememberInteraction()
                BasicText(
                    n.url,
                    style = cssText(fonts.sans, 13f, 18f, FontWeight.Normal, KraftColors.FooterName),
                    modifier = Modifier.clickableRaw(linkInteraction, role = Role.Button) { uri.openUri(n.url) },
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .cssSurface(12.dp) { Pg.Stage }
                        .horizontalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    BasicText(text ?: "", style = cssText(fonts.mono, 11.5f, 18f, FontWeight.Normal, KraftColors.TextMuted), softWrap = false)
                }
            }
        }
    }
}
