package com.burkido.kraft.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.burkido.kraft.designsystem.BoxShadow
import com.burkido.kraft.designsystem.ChipIconButton
import com.burkido.kraft.designsystem.KraftColors
import com.burkido.kraft.designsystem.KraftIcons
import com.burkido.kraft.designsystem.LocalKraftFonts
import com.burkido.kraft.designsystem.LocalViewport
import com.burkido.kraft.designsystem.animatedStateColor
import com.burkido.kraft.designsystem.clickableRaw
import com.burkido.kraft.designsystem.cssSurface
import com.burkido.kraft.designsystem.cssText
import com.burkido.kraft.designsystem.rememberInteraction
import com.burkido.kraft.landing.Footer
import com.burkido.kraft.landing.MobileMenu
import com.burkido.kraft.landing.SiteNav
import org.jetbrains.compose.resources.painterResource
import kotlin.math.min
import kotlin.math.roundToInt

/** A library's detail page: `.docs` — sidebar (or chip strip) and the page body. */
@Composable
fun LibraryScreen(id: LibraryId, onSelectLibrary: (LibraryId) -> Unit, onBack: () -> Unit, onHowTo: () -> Unit, onLicenses: () -> Unit = {}) {
    val vp = LocalViewport.current
    val wide = !vp.maxWidth(900)
    val scroll = rememberScrollState()
    val page = remember(id) { pageFor(id) }
    var menuOpen by remember { mutableStateOf(false) }
    // A new library starts at the top of its page, as a new document would.
    LaunchedEffect(id) { scroll.scrollTo(0) }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 1240.dp).fillMaxWidth().padding(horizontal = vp.gutter)) {
                SiteNav(
                    onLibraries = {},
                    onHowTo = onHowTo,
                    onGetStarted = onHowTo,
                    menuOpen = menuOpen,
                    onMenuToggle = { menuOpen = !menuOpen },
                    librariesActive = true,
                )
                if (wide) {
                    var sidebarTop by remember { mutableFloatStateOf(0f) }
                    var mainHeight by remember { mutableIntStateOf(0) }
                    var sidebarHeight by remember { mutableIntStateOf(0) }
                    val density = LocalDensity.current
                    Row(Modifier.padding(top = 32.dp, bottom = 96.dp), horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                        // `position: sticky; top: 84px` inside the scrolling page.
                        Box(
                            Modifier
                                .width(200.dp)
                                .onGloballyPositioned { sidebarTop = it.positionInRoot().y + scroll.value }
                                .offset {
                                    val stick = scroll.value - sidebarTop + with(density) { 84.dp.toPx() }
                                    val max = (mainHeight - sidebarHeight).coerceAtLeast(0)
                                    IntOffset(0, stick.roundToInt().coerceIn(0, max))
                                },
                        ) {
                            Box(Modifier.onGloballyPositioned { sidebarHeight = it.size.height }) {
                                DocsSidebar(id, onSelectLibrary, onOverview = onBack)
                            }
                        }
                        Box(Modifier.weight(1f).onGloballyPositioned { mainHeight = it.size.height }) {
                            DocsMain(id, page, wide = true, onSelectLibrary)
                        }
                    }
                } else {
                    Column(Modifier.padding(top = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        DocsChipStrip(id, onSelectLibrary)
                        DocsMain(id, page, wide = false, onSelectLibrary)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = vp.gutter), contentAlignment = Alignment.TopCenter) {
                Footer(onOpenLibrary = onSelectLibrary, onHowTo = onHowTo, onHome = onBack, onLicenses = onLicenses)
            }
        }
        MobileMenu(
            open = menuOpen,
            onDismiss = { menuOpen = false },
            onLibraries = { menuOpen = false },
            onHowTo = { menuOpen = false; onHowTo() },
        )
    }
}

@Composable
private fun DocsSidebar(current: LibraryId, onSelect: (LibraryId) -> Unit, onOverview: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            GroupLabel("Getting started", horizontalPadding = 12)
            DocsLink(label = "Overview", icon = null, active = false, height = 32, onClick = onOverview)
        }
        Column {
            GroupLabel("Libraries", horizontalPadding = 8)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (lib in LibraryId.entries) {
                    DocsLink(lib.shortName, lib, lib == current, height = 40) { onSelect(lib) }
                }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String, horizontalPadding: Int) {
    val fonts = LocalKraftFonts.current
    BasicText(
        text,
        style = cssText(fonts.sans, 11f, 14f, FontWeight.Normal, KraftColors.TextSubtle),
        modifier = Modifier.padding(horizontal = horizontalPadding.dp, vertical = 9.dp),
    )
}

/** `.docs-link`: 40 dp rows, faint on hover, raised chip when active; icon art is 30 dp in a 24 box. */
@Composable
private fun DocsLink(label: String, icon: LibraryId?, active: Boolean, height: Int, onClick: () -> Unit) {
    val fonts = LocalKraftFonts.current
    val interaction = rememberInteraction()
    val bg by animatedStateColor(
        interaction,
        if (active) Color.White.copy(alpha = 0.08f) else Color.Transparent,
        if (active) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.04f),
        durationMs = 140,
    )
    val shadows = if (active) {
        listOf(
            BoxShadow(y = 1.dp, blur = 1.dp, color = Color.Black.copy(alpha = 0.24f)),
            BoxShadow(spread = 1.dp, color = Color.White.copy(alpha = 0.02f), inset = true),
            BoxShadow(y = 1.dp, color = Color.White.copy(alpha = 0.04f), inset = true),
        )
    } else {
        emptyList()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .cssSurface(12.dp, shadows) { bg }
            .clickableRaw(interaction, onClick = onClick)
            .padding(horizontal = if (icon == null) 12.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(24.dp)) {
                Image(
                    painterResource(icon.sideIcon),
                    contentDescription = null,
                    modifier = Modifier.offset(x = (-3).dp, y = (-2).dp).requiredSize(30.dp),
                )
            }
        }
        BasicText(
            label,
            style = cssText(
                fonts.sans, 13f, if (icon == null) 16f else 13f,
                if (icon == null) FontWeight.Normal else FontWeight.Medium,
                if (active) Color(0xFFF5F5F5) else Color(0xFFEDEDED),
            ),
            softWrap = false,
        )
    }
}

/**
 * Below 900 px: a horizontally scrolling chip strip, opened with the active chip centred
 * (`centerActive`); each edge fades over up to 24 dp, as far as there is more to scroll that way.
 */
@Composable
private fun DocsChipStrip(current: LibraryId, onSelect: (LibraryId) -> Unit) {
    val fonts = LocalKraftFonts.current
    val scroll = rememberScrollState()
    val fade = with(LocalDensity.current) { 24.dp.toPx() }
    // Each chip's x and width in the row, recorded as it is placed.
    val chips = remember { HashMap<LibraryId, IntArray>() }
    var viewport by remember { mutableIntStateOf(0) }
    LaunchedEffect(current, viewport) {
        val chip = chips[current] ?: return@LaunchedEffect
        if (viewport == 0) return@LaunchedEffect
        scroll.scrollTo((chip[0] - (viewport - chip[1]) / 2).coerceIn(0, scroll.maxValue))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { viewport = it.width }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val start = min(scroll.value.toFloat(), fade)
                val end = min((scroll.maxValue - scroll.value).toFloat(), fade)
                if (start <= 0f && end <= 0f) return@drawWithContent
                drawRect(
                    Brush.horizontalGradient(
                        0f to (if (start > 0f) Color.Transparent else Color.Black),
                        (start / size.width) to Color.Black,
                        (1f - end / size.width) to Color.Black,
                        1f to (if (end > 0f) Color.Transparent else Color.Black),
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        Row(Modifier.horizontalScroll(scroll).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (lib in LibraryId.entries) {
                val active = lib == current
                val interaction = rememberInteraction()
                val bg by animatedStateColor(
                    interaction,
                    if (active) Color.White.copy(alpha = 0.16f) else KraftColors.Chip,
                    if (active) Color.White.copy(alpha = 0.16f) else KraftColors.ChipHover,
                    durationMs = 140,
                )
                Box(
                    Modifier
                        .onPlaced { chips[lib] = intArrayOf(it.positionInParent().x.roundToInt(), it.size.width) }
                        .height(36.dp)
                        .cssSurface(12.dp) { bg }
                        .clickableRaw(interaction) { onSelect(lib) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        lib.shortName,
                        style = cssText(fonts.sans, 13f, 13f, FontWeight.Medium, if (active) KraftColors.Text else Color(0xFFEDEDED)),
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocsMain(id: LibraryId, page: LibraryPage, wide: Boolean, onSelectLibrary: (LibraryId) -> Unit) {
    val fonts = LocalKraftFonts.current
    var tab by remember(id) { mutableIntStateOf(0) }
    Box(Modifier.fillMaxWidth()) {
        if (wide) {
            // `.detail-topbar`: previous / next library.
            Row(Modifier.align(Alignment.TopEnd).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                val all = LibraryId.entries
                ChipIconButton(KraftIcons.ArrowLeft, onClick = { onSelectLibrary(all[(id.ordinal - 1 + all.size) % all.size]) })
                ChipIconButton(KraftIcons.ArrowRight, onClick = { onSelectLibrary(all[(id.ordinal + 1) % all.size]) })
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(id.icon), contentDescription = null, modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)))
                BasicText(id.pageTitle, style = cssText(fonts.display, 36f, 42f, FontWeight.Medium, KraftColors.Text, (-0.18).sp))
                BasicText(page.subtitle, style = cssText(fonts.sans, 14f, 22f, FontWeight.Normal, Color(0xFFCDCDCD)))
            }
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 21.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                DetailTabs(listOf("Preview", "Install & Usage"), tab) { tab = it }
                CopyPromptButton("Copy prompt", page.prompt)
            }
            Column(Modifier.padding(top = 16.dp)) {
                if (tab == 0) page.Preview() else InstallAndUsage(page)
            }
        }
    }
}

/**
 * The "Install & Usage" panel (`.st-stage-panel`): the platform title, then the Gradle lines and
 * the usage snippet under their own titles on compact #1F1F1F code surfaces.
 */
@Composable
private fun InstallAndUsage(page: LibraryPage) {
    val fonts = LocalKraftFonts.current
    Column(
        Modifier.fillMaxWidth().cssSurface(16.dp) { Pg.Stage }.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp),
    ) {
        BasicText(
            "Compose Multiplatform",
            style = cssText(fonts.sans, 14f, 14f, FontWeight.Medium, Pg.Title),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        CodeGroup("Installation", page.installCode, page.installNote)
        CodeGroup("Usage", page.usageCode, null)
    }
}

@Composable
private fun CodeGroup(title: String, code: String, note: String?) {
    val fonts = LocalKraftFonts.current
    val scroll = rememberScrollState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(title, style = cssText(fonts.sans, 13f, 14f, FontWeight.Medium, Pg.Title))
        Box(
            Modifier
                .fillMaxWidth()
                .cssSurface(10.dp, listOf(BoxShadow(spread = 1.dp, color = Color.White.copy(alpha = 0.02f), inset = true))) { Color(0xFF1F1F1F) },
        ) {
            Box(Modifier.fillMaxWidth().codeFade(scroll, fade = 56.dp, end = 40.dp).horizontalScroll(scroll)) {
                BasicText(
                    code,
                    style = cssText(fonts.mono, 14f, 22.2f, FontWeight.Normal, Color.White),
                    softWrap = false,
                    modifier = Modifier.padding(start = 12.dp, end = 48.dp, top = 9.5.dp, bottom = 9.5.dp),
                )
            }
            CopyIconButton(code, Modifier.align(Alignment.TopEnd).padding(4.dp))
        }
        if (note != null) {
            BasicText(note, style = cssText(fonts.sans, 12f, 18f, FontWeight.Normal, Pg.Label))
        }
    }
}
