package com.burkido.kraft.landing

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.burkido.kraft.designsystem.BoxShadow
import com.burkido.kraft.designsystem.BrandMark
import com.burkido.kraft.designsystem.KraftColors
import com.burkido.kraft.designsystem.KraftIcon
import com.burkido.kraft.designsystem.KraftIcons
import com.burkido.kraft.designsystem.KraftMotion
import com.burkido.kraft.designsystem.LocalKraftFonts
import com.burkido.kraft.designsystem.LocalViewport
import com.burkido.kraft.designsystem.TightTracking
import com.burkido.kraft.designsystem.animatedStateColor
import com.burkido.kraft.designsystem.clickableRaw
import com.burkido.kraft.designsystem.cssBlur
import com.burkido.kraft.designsystem.cssSurface
import com.burkido.kraft.designsystem.cssText
import com.burkido.kraft.designsystem.materialShadows
import com.burkido.kraft.designsystem.negativeMarginTop
import com.burkido.kraft.designsystem.rememberInteraction
import com.burkido.kraft.library.LibraryId

/** `.home-section`: max 1008, 96 dp above (64 on phones). */
@Composable
private fun HomeSection(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val vp = LocalViewport.current
    Column(
        modifier
            .padding(top = if (vp.maxWidth(640)) 64.dp else 96.dp)
            .widthIn(max = 1008.dp)
            .fillMaxWidth(),
    ) { content() }
}

@Composable
private fun SectionTitle(text: String) {
    val fonts = LocalKraftFonts.current
    val phone = LocalViewport.current.maxWidth(640)
    BasicText(
        text,
        style = cssText(fonts.display, if (phone) 23f else 30f, null, FontWeight.Medium, KraftColors.Text, TightTracking)
            .copy(textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth().padding(bottom = if (phone) 28.dp else 40.dp),
    )
}

// ── Logo row ──────────────────────────────────────────────────────────────────────────────

/** Placeholder wordmarks in the site's logo-row styling: white at 55 %, the site's heights. */
private val PLACEHOLDER_LOGOS = listOf("Acme" to 20f, "Northwind" to 22f, "Globex" to 16f, "Initech" to 19f, "Hooli" to 22f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomerLogos() {
    val fonts = LocalKraftFonts.current
    val vp = LocalViewport.current
    val phone = vp.maxWidth(640)
    Column(
        Modifier
            // `margin: -48px auto 64px` (24 auto 48 on phones).
            .then(if (phone) Modifier.padding(top = 24.dp) else Modifier.negativeMarginTop(48.dp))
            .padding(bottom = if (phone) 48.dp else 64.dp)
            .widthIn(max = 1008.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            "Made for product teams like",
            style = cssText(fonts.sans, 13f, 20f, FontWeight.Normal, Color(0xFF737373).copy(alpha = 0.7f)),
            modifier = Modifier.padding(top = 32.dp, bottom = 24.dp),
        )
        val scale = when {
            vp.maxWidth(380) -> 0.7f
            phone -> 0.85f
            else -> 1f
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(if (phone) 28.dp else 40.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(if (phone) 18.dp else 24.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            // `.home-customers-break`: 3 + 2 on narrow phones.
            maxItemsInEachRow = if (vp.maxWidth(600)) 3 else Int.MAX_VALUE,
        ) {
            for ((name, size) in PLACEHOLDER_LOGOS) {
                BasicText(
                    name,
                    style = cssText(fonts.display, size * scale, null, FontWeight.Medium, Color.White.copy(alpha = 0.55f), (-0.02).sp),
                )
            }
        }
    }
}

// ── Kind words ────────────────────────────────────────────────────────────────────────────

private class Quote(val text: String, val name: String, val role: String)

private val QUOTES = listOf(
    Quote("Feels exactly like the web version — only smoother.", "Alex Rivera", "Design engineer"),
    Quote("The border beam is the first thing people notice.", "Mina Chen", "Product designer"),
    Quote("Dropped the orb into our chat UI in five minutes.", "Sam Okafor", "iOS engineer"),
    Quote("Finally, motion that doesn't look like a template.", "Jan Novak", "Founder"),
    Quote("Liquid metal on a send button. Enough said.", "Lea Moreau", "Creative director"),
    Quote("The playgrounds make tuning an effect effortless.", "Ken Tanaka", "Android engineer"),
)

/** "Kind words": 3 columns, 2 below 900 px (an odd last card spans both), 1 below 640 (first three). */
@Composable
fun KindWords() {
    val vp = LocalViewport.current
    val columns = when {
        vp.maxWidth(640) -> 1
        vp.maxWidth(900) -> 2
        else -> 3
    }
    val quotes = if (columns == 1) QUOTES.take(3) else QUOTES
    val gap = if (columns == 1) 12.dp else 18.dp
    HomeSection {
        SectionTitle("Kind words")
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in quotes.chunked(columns)) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (q in row) QuoteCard(q, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun QuoteCard(quote: Quote, modifier: Modifier) {
    val fonts = LocalKraftFonts.current
    val phone = LocalViewport.current.maxWidth(640)
    Box(
        modifier
            .heightIn(min = if (phone) 0.dp else 240.dp)
            .cssSurface(
                24.dp,
                listOf(
                    BoxShadow(y = 1.dp, color = Color.White.copy(alpha = 0.02f), inset = true),
                    BoxShadow(spread = 1.dp, color = Color.White.copy(alpha = 0.02f), inset = true),
                ),
            ) { KraftColors.Quote },
    ) {
        KraftIcon(KraftIcons.XLogo, Color.White.copy(alpha = 0.22f), Modifier.align(Alignment.TopEnd).padding(24.dp), size = 14.dp)
        Column(
            Modifier.fillMaxWidth().padding(if (phone) 24.dp else 32.dp),
            verticalArrangement = Arrangement.spacedBy(if (phone) 20.dp else 24.dp),
        ) {
            BasicText(
                quote.text,
                style = cssText(fonts.sans, if (phone) 15f else 16f, if (phone) 23f else 24.2f, FontWeight.Normal, Color(0xFFC9C9C9)),
                modifier = Modifier.padding(end = 28.dp).weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).cssSurface(50.dp) { Color.White.copy(alpha = 0.06f) },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(quote.name.take(1), style = cssText(fonts.sans, 13f, null, FontWeight.Medium, KraftColors.TextMuted))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(quote.name, style = cssText(fonts.sans, 13f, 16f, FontWeight.Medium, Color(0xFFEDEDED)))
                    BasicText(quote.role, style = cssText(fonts.sans, 13f, 16f, FontWeight.Normal, Color(0xFF737373)))
                }
            }
        }
    }
}

// ── How to use ────────────────────────────────────────────────────────────────────────────

private val STEPS = listOf(
    "Add the dependency" to "One Gradle line brings all seven effects to Android, iOS and desktop.",
    "Wrap your composable" to "Put BorderBeam, ThinkingOrb or MetalFx around the UI you already have.",
    "Make it yours" to "Tune it in each library's playground, then copy the exact configuration.",
)

@Composable
fun HowToUse(modifier: Modifier = Modifier) {
    val fonts = LocalKraftFonts.current
    val vp = LocalViewport.current
    val stacked = vp.maxWidth(900)
    HomeSection(modifier) {
        Column(Modifier.widthIn(max = 640.dp).padding(bottom = 40.dp)) {
            BasicText(
                buildAnnotatedString {
                    append("Add the library.\n")
                    withStyle(SpanStyle(color = KraftColors.Text.copy(alpha = 0.6f))) { append("Wrap your composable") }
                },
                style = cssText(fonts.display, if (stacked) 30f else 38f, (if (stacked) 30f else 38f) * 1.16f, FontWeight.Medium, KraftColors.Text, TightTracking),
            )
            BasicText(
                "Every effect is a single composable that wraps the UI you already have — no shaders to write, no setup.",
                style = cssText(fonts.sans, 16f, 24f, FontWeight.Normal, KraftColors.TextMuted),
                modifier = Modifier.padding(top = 14.dp).widthIn(max = 420.dp),
            )
        }
        val gap = if (stacked) 12.dp else 18.dp
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                STEPS.forEachIndexed { i, (title, text) -> StepCard(i + 1, title, text, Modifier.fillMaxWidth()) }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(gap)) {
                STEPS.forEachIndexed { i, (title, text) -> StepCard(i + 1, title, text, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

/** `.howto-step`: a raised card with an inset stage, a blue numbered disc and its halo. */
@Composable
private fun StepCard(number: Int, title: String, text: String, modifier: Modifier) {
    val fonts = LocalKraftFonts.current
    val stacked = LocalViewport.current.maxWidth(900)
    Box(modifier.cssSurface(24.dp, materialShadows()) { KraftColors.Card }) {
        Box(
            Modifier
                .matchParentSize()
                .padding(12.dp)
                .cssSurface(14.dp, listOf(BoxShadow(spread = 1.dp, color = Color(191, 192, 203).copy(alpha = 0.08f), inset = true))) { KraftColors.Stage },
        )
        Column(Modifier.padding(if (stacked) 24.dp else 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(21.dp), contentAlignment = Alignment.Center) {
                    // `::before` halo: 5.25 dp larger all round, the same blue at 20 %.
                    Box(Modifier.size(31.5.dp).cssSurface(50.dp) { KraftColors.Cta.copy(alpha = 0.2f) })
                    Box(
                        Modifier.size(21.dp).cssSurface(
                            50.dp,
                            listOf(
                                BoxShadow(y = 1.dp, color = Color.White.copy(alpha = 0.04f), inset = true),
                                BoxShadow(spread = 1.dp, color = Color.White.copy(alpha = 0.06f), inset = true),
                                BoxShadow(spread = 1.dp, color = Color.White.copy(alpha = 0.1f), inset = true),
                            ),
                        ) { KraftColors.Cta },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText("$number", style = cssText(fonts.sans, 11f, 12f, FontWeight.Medium, Color.White))
                    }
                }
                BasicText(title, style = cssText(fonts.sans, 15f, 20f, FontWeight.Medium, Color(0xFFEDEDED)))
            }
            BasicText(text, style = cssText(fonts.sans, 14f, 22f, FontWeight.Normal, KraftColors.TextMuted))
        }
    }
}

// ── FAQ ───────────────────────────────────────────────────────────────────────────────────

private val FAQS = listOf(
    "Are the libraries free?" to
        "Yes. They are native ports of Jakub Antalik's MIT-licensed Libraries.dev, and the ports are free to use in any project.",
    "What do I need to use them?" to
        "Compose Multiplatform 1.12 or newer. The effects run on Android 12+, iOS and desktop JVMs from one codebase.",
    "Do they look the same as on the web?" to
        "They are drawn the way the web draws them — the same gradients, masks and filters on the same Skia engine — and checked frame by frame against the originals.",
    "Do they respect accessibility settings?" to
        "Yes. Reduced motion shows static frames, and every effect pauses while it is off screen or the app is in the background.",
    "Which platforms are supported?" to
        "Android, iOS and desktop, all from the same Kotlin code.",
)

@Composable
fun Faq() {
    HomeSection {
        SectionTitle("FAQs")
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().align(Alignment.CenterHorizontally)) {
            FAQS.forEachIndexed { i, (q, a) -> Accordion(q, a, last = i == FAQS.lastIndex) }
        }
    }
}

/** `.t-acc`: hairline-separated rows; the panel grows, fades and un-blurs 2 dp over 250 ms. */
@Composable
private fun Accordion(question: String, answer: String, last: Boolean) {
    val fonts = LocalKraftFonts.current
    var open by remember { mutableStateOf(false) }
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
            Modifier
                .fillMaxWidth()
                .clickableRaw(interaction) { open = !open }
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(question, style = cssText(fonts.sans, 15f, 22f, FontWeight.Normal, KraftColors.Text), modifier = Modifier.weight(1f))
            KraftIcon(KraftIcons.ChevronDown, KraftColors.TextMuted, Modifier.graphicsLayer { scaleY = chevron })
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(KraftMotion.Fast, easing = KraftMotion.SmoothOut)),
            exit = shrinkVertically(tween(KraftMotion.Fast, easing = KraftMotion.SmoothOut)),
        ) {
            BasicText(
                answer,
                style = cssText(fonts.sans, 14f, 22f, FontWeight.Normal, KraftColors.TextMuted),
                modifier = Modifier
                    .padding(end = 28.dp, bottom = 16.dp)
                    .graphicsLayer { alpha = reveal }
                    .cssBlur(((1f - reveal) * 2f).dp),
            )
        }
    }
}

// ── Footer ────────────────────────────────────────────────────────────────────────────────

@Composable
fun Footer(onOpenLibrary: (LibraryId) -> Unit, onHowTo: () -> Unit, onHome: () -> Unit = {}, onLicenses: () -> Unit = {}) {
    val fonts = LocalKraftFonts.current
    val vp = LocalViewport.current
    val stacked = vp.maxWidth(720)
    val body = cssText(fonts.sans, 13f, 18.2f, FontWeight.Normal, KraftColors.TextMuted)
    Column(
        Modifier
            .padding(top = 112.dp, bottom = if (vp.maxWidth(639)) 24.dp else 40.dp)
            .widthIn(max = if (vp.maxWidth(1100)) 664.dp else 1008.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(25.dp),
    ) {
        val brand: @Composable () -> Unit = {
            Column(Modifier.widthIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FooterBrand()
                BasicText("High-crafted UI effects for Compose", style = body)
            }
        }
        val cols: @Composable () -> Unit = {
            FlowRowCols(gap = if (stacked) 40.dp else 56.dp) {
                FooterColumn("Libraries", LibraryId.entries.map { it.shortName to { onOpenLibrary(it) } })
                FooterColumn("Product", listOf("Home" to onHome, "How to use" to onHowTo))
                FooterColumn("Legal", listOf("Licenses" to onLicenses, "Credits" to onLicenses))
            }
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(32.dp)) { brand(); cols() }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { brand(); cols() }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = KraftColors.TextFaint)) { append("Ported from Libraries.dev by ") }
                    withStyle(SpanStyle(color = KraftColors.FooterName)) { append("Jakub Antalik") }
                },
                style = body,
            )
            BasicText(
                "Effects © Jakub Antalik (MIT). Liquid metal material by Paper Design (Apache-2.0). Inter and Roboto Mono (OFL).",
                style = cssText(fonts.sans, 11f, 16f, FontWeight.Normal, KraftColors.TextFaint.copy(alpha = 0.8f)),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowCols(gap: Dp, content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(32.dp)) { content() }
}

@Composable
private fun FooterBrand() {
    val fonts = LocalKraftFonts.current
    val interaction = rememberInteraction()
    val active = interaction.hovered || interaction.pressed
    val alpha by animateFloatAsState(if (active) 1f else 0.7f, tween(KraftMotion.Quick))
    val scale by animateFloatAsState(if (active) 1.02f else 1f, tween(KraftMotion.Slow, easing = KraftMotion.SmoothOut))
    Row(
        Modifier.graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }.clickableRaw(interaction) {},
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(redraw = active)
        Row {
            BasicText("Kraft", style = cssText(fonts.sans, 15f, 15f, FontWeight.Medium, KraftColors.Text, TightTracking))
            BasicText("UI", style = cssText(fonts.sans, 15f, 15f, FontWeight.Medium, KraftColors.TextMuted, TightTracking))
        }
    }
}

@Composable
private fun FooterColumn(title: String, links: List<Pair<String, () -> Unit>>) {
    val fonts = LocalKraftFonts.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(
            title,
            style = cssText(fonts.sans, 13f, 18.2f, FontWeight.Medium, KraftColors.TextMuted.copy(alpha = KraftColors.TextMuted.alpha * 0.7f)),
            modifier = Modifier.padding(bottom = 2.dp),
        )
        for ((label, action) in links) {
            val interaction = rememberInteraction()
            val color by animatedStateColor(interaction, KraftColors.TextMuted, KraftColors.Text)
            BasicText(
                label,
                style = cssText(fonts.sans, 13f, 18.2f, FontWeight.Normal, color),
                modifier = Modifier.clickableRaw(interaction, onClick = action),
            )
        }
    }
}
