package com.burkido.kraft.library

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.burkido.kraft.designsystem.BoxShadow
import com.burkido.kraft.designsystem.KraftIcon
import com.burkido.kraft.designsystem.KraftMotion
import com.burkido.kraft.designsystem.LocalKraftFonts
import com.burkido.kraft.designsystem.cssBlur
import com.burkido.kraft.designsystem.cssSurface
import com.burkido.kraft.designsystem.cssText
import com.burkido.kraft.effects.core.CubicBezier
import com.burkido.kraft.effects.core.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

// The voice page's mock hosts (sites/home/src/examples/voice-mocks.tsx, examples.css dark). Each
// is split in two like the web's stacking: a surface under the glow and a foreground over it.

private val HostRing = listOf(
    BoxShadow(spread = 1.dp, color = Color(44, 47, 54).copy(alpha = 0.52f), inset = true),
    BoxShadow(blur = 50.dp, color = Color.White.copy(alpha = 0.02f), inset = true),
)
private val ButtonChrome = listOf(
    BoxShadow(spread = 1.dp, color = Color.White.copy(alpha = 0.04f), inset = true),
    BoxShadow(y = 1.dp, color = Color.White.copy(alpha = 0.06f), inset = true),
)

// ── Chat input (`.mock-vchat`, 371 × 104) ─────────────────────────────────────────────────

internal const val VoiceChatWidth = 371f

@Composable
internal fun VoiceChatSurface(width: Dp) {
    Box(Modifier.size(width, 104.dp).cssSurface(20.dp, HostRing) { Color(48, 48, 48).copy(alpha = 0.4f) })
}

@Composable
internal fun BoxScope.VoiceChatForeground() {
    val fonts = LocalKraftFonts.current
    Box(Modifier.matchParentSize()) {
        BasicText(
            "Ask me anything..",
            style = cssText(fonts.sans, 14f, 16f, FontWeight.Normal, Color(0xFF4E4E4E)),
            modifier = Modifier.padding(start = 14.dp, top = 20.dp, end = 14.dp),
            maxLines = 1,
        )
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 11.dp, end = 13.dp, bottom = 13.dp).height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChatButton { PlusGlyph(10.dp, 1.5.dp, Color(0xFFEAEAEA)) }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.height(36.dp).cssSurface(36.dp, ButtonChrome) { Color.White.copy(alpha = 0.1f) }.padding(start = 14.dp, end = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText("Agent", style = cssText(fonts.sans, 12f, 14f, FontWeight.Medium, Color(0xFFCACCD2)), softWrap = false)
                    KraftIcon(VoiceIcons.Chevron15, Color.White.copy(alpha = 0.4f), Modifier.rotate(90f))
                }
                ChatButton { KraftIcon(VoiceIcons.Mic16, Color(0xFFB2B2B2)) }
                ChatButton { PlusGlyph(10.dp, 1.5.dp, Color(0xFFEAEAEA), Modifier.rotate(45f)) }
            }
        }
    }
}

@Composable
private fun ChatButton(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.size(36.dp).cssSurface(36.dp, ButtonChrome) { Color.White.copy(alpha = 0.1f) }, contentAlignment = Alignment.Center, content = content)
}

/** `.mk-ico-plus`: two rounded bars crossing at the centre (the Figma glyph collapsed on export). */
@Composable
private fun PlusGlyph(length: Dp, thickness: Dp, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.size(length).drawBehind {
            val l = length.toPx()
            val t = thickness.toPx()
            val r = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
            drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(0f, (l - t) / 2), size = androidx.compose.ui.geometry.Size(l, t), cornerRadius = r)
            drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset((l - t) / 2, 0f), size = androidx.compose.ui.geometry.Size(t, l), cornerRadius = r)
        },
    )
}

// ── Recording pill (`.mock-pill`, 149 × 44) ───────────────────────────────────────────────

@Composable
internal fun RecordingPillSurface() {
    Box(Modifier.size(149.dp, 44.dp).cssSurface(22.dp, PillChrome) { Color.White.copy(alpha = 0.08f) })
}

private val PillChrome = listOf(
    BoxShadow(spread = 1.dp, color = Color.White.copy(alpha = 0.04f), inset = true),
    BoxShadow(y = 1.dp, color = Color.White.copy(alpha = 0.04f), inset = true),
)

/**
 * The pill's mic, a live mm:ss counter while [running] and not [paused] (from [startSeconds]),
 * and the stop button.
 */
@Composable
internal fun BoxScope.RecordingPillForeground(running: Boolean, paused: Boolean, resetKey: Int, startSeconds: Int = 0) {
    val fonts = LocalKraftFonts.current
    var seconds by remember { mutableIntStateOf(startSeconds) }
    val elapsed = remember { doubleArrayOf(startSeconds.toDouble()) }
    LaunchedEffect(running, resetKey) {
        elapsed[0] = startSeconds.toDouble()
        seconds = startSeconds
    }
    LaunchedEffect(running, paused, resetKey) {
        if (!running || paused) return@LaunchedEffect
        val base = elapsed[0]
        val start = TimeSource.Monotonic.markNow()
        while (true) {
            delay(250)
            elapsed[0] = base + start.elapsedNow().inWholeMilliseconds / 1000.0
            seconds = elapsed[0].toInt()
        }
    }
    val mm = (seconds / 60).toString().padStart(2, '0')
    val ss = (seconds % 60).toString().padStart(2, '0')
    Box(Modifier.matchParentSize()) {
        KraftIcon(VoiceIcons.Mic16, Color(0xFFB2B2B2), Modifier.offset(13.dp, 14.dp))
        BasicText(
            "$mm:$ss",
            style = cssText(fonts.sans, 13f, 16f, FontWeight.Medium, Color(0xFF878787)).copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.offset(51.dp, 14.dp),
            softWrap = false,
        )
        Box(
            Modifier.offset(111.dp, 7.dp).size(30.dp).cssSurface(30.dp, ButtonChrome) { Color.White.copy(alpha = 0.1f) },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(11.dp).cssSurface(2.dp) { Color(0xFFD9D9D9).copy(alpha = 0.6f) })
        }
    }
}

// ── Phone screen (`.mock-phone`, 402 × 874, cropped to its bottom 273 × 357 at 0.68) ───────

internal const val PhoneCrop = 0.68f
internal val PhoneCropWidth = 273.dp
internal val PhoneCropHeight = 357.dp

/**
 * `.mock-phone-scale`: the 402 × 874 screen scaled to 0.68 and lifted 237.7 dp, so the 273 × 357
 * window shows its lower part. Surface and foreground are placed through the same window.
 */
@Composable
internal fun PhoneWindow(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.size(PhoneCropWidth, PhoneCropHeight).clip(RectangleShape)) {
        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(402.dp, 874.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = PhoneCrop
                    scaleY = PhoneCrop
                    translationY = -237.7.dp.toPx()
                },
            content = content,
        )
    }
}

/** The screen itself and the prompt / transcript text, all under the glow. */
@Composable
internal fun PhoneSurface(promptKey: Int, transcript: List<String>) {
    PhoneWindow {
        Box(Modifier.matchParentSize().cssSurface(66.dp, HostRing) { Color(0xFF171717) })
        val speaking = transcript.isNotEmpty()
        PhoneText(hidden = speaking) {
            key(promptKey) { BlurWords(listOf("How", "can", "I", "help", "you?"), stagger = true, color = Color(0xFFB2B2B2).copy(alpha = 0.6f)) }
        }
        PhoneText(hidden = !speaking) {
            BlurWords(transcript, stagger = false, color = Color(0xFFE6E6E6).copy(alpha = 0.9f))
        }
    }
}

/** The bottom row — Agent (auto), mic and close — over the glow. */
@Composable
internal fun PhoneForeground() {
    val fonts = LocalKraftFonts.current
    PhoneWindow {
        Row(
            Modifier.offset(y = 781.dp).padding(horizontal = 32.dp).fillMaxWidth().height(44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.height(44.dp).cssSurface(70.dp, PhoneButtonShadows) { Color.White.copy(alpha = 0.08f) }.padding(start = 20.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText("Agent (auto)", style = cssText(fonts.sans, 14f, 14f, FontWeight.Medium, Color(0xFFCACCD2)), softWrap = false)
                KraftIcon(VoiceIcons.Chevron16, Color.White.copy(alpha = 0.4f), Modifier.rotate(90f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                PhoneButton { KraftIcon(VoiceIcons.Mic20, Color(0xFFEFEFEF), size = 20.dp) }
                PhoneButton { PlusGlyph(14.dp, 2.dp, Color(0xFFEFEFEF), Modifier.rotate(45f)) }
            }
        }
    }
}

private val PhoneButtonShadows = listOf(
    BoxShadow(y = 2.dp, blur = 2.dp, spread = (-2).dp, color = Color.Black.copy(alpha = 0.04f)),
    BoxShadow(y = 2.dp, blur = 6.dp, spread = (-1).dp, color = Color.Black.copy(alpha = 0.04f)),
    BoxShadow(spread = 0.5.dp, color = Color.Black.copy(alpha = 0.12f)),
    // The glassy rim: four 1 dp highlights tucked in from each side.
    BoxShadow(y = 2.dp, blur = 1.dp, spread = (-2).dp, color = Color.White.copy(alpha = 0.24f), inset = true),
    BoxShadow(x = 1.dp, y = 2.dp, blur = 1.dp, spread = (-2).dp, color = Color.White.copy(alpha = 0.24f), inset = true),
    BoxShadow(x = (-1).dp, y = (-2).dp, blur = 1.dp, spread = (-2).dp, color = Color.White.copy(alpha = 0.24f), inset = true),
    BoxShadow(y = (-2).dp, blur = 1.dp, spread = (-2).dp, color = Color.White.copy(alpha = 0.24f), inset = true),
)

@Composable
private fun PhoneButton(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.size(44.dp).cssSurface(70.dp, PhoneButtonShadows) { Color.White.copy(alpha = 0.08f) }, contentAlignment = Alignment.Center, content = content)
}

/** `.mock-phone-text`: left / right 36, bottom 194; cross-blurs out (5 dp, 320 ms) when hidden. */
@Composable
private fun BoxScope.PhoneText(hidden: Boolean, content: @Composable () -> Unit) {
    val t by animateFloatAsState(if (hidden) 1f else 0f, tween(320, easing = KraftMotion.SmoothOut))
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 36.dp, end = 36.dp, bottom = 194.dp)
            .fillMaxWidth()
            .graphicsLayer { alpha = 1f - t }
            .cssBlur((5f * t).dp),
        contentAlignment = Alignment.BottomCenter,
    ) { content() }
}

/**
 * `BlurWords`: centred words that each arrive from 3 dp of blur (550 ms, `(.2,.7,.2,1)`); with
 * [stagger] they wait 90 ms apiece. When a new word re-centres the line, the others slide over
 * (the FLIP transition, 400 ms smooth-out).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun BlurWords(words: List<String>, stagger: Boolean, color: Color) {
    val fonts = LocalKraftFonts.current
    val reduced = rememberReducedMotion()
    val style = cssText(fonts.sans, 21f, 28f, FontWeight.Normal, color).copy(textAlign = TextAlign.Center)
    LookaheadScope {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.5.dp, Alignment.CenterHorizontally)) {
            words.forEachIndexed { i, word ->
                key(i) {
                    val enter = remember { Animatable(if (reduced) 1f else 0f) }
                    LaunchedEffect(Unit) {
                        if (stagger) delay(90L * i)
                        enter.animateTo(1f, tween(550, easing = CubicBezier(0.2, 0.7, 0.2, 1.0).easing))
                    }
                    BasicText(
                        word,
                        style = style,
                        softWrap = false,
                        modifier = Modifier
                            .animateBounds(this@LookaheadScope, boundsTransform = { _, _ -> tween(400, easing = KraftMotion.SmoothOut) })
                            .graphicsLayer { alpha = enter.value }
                            .cssBlur((3f * (1f - enter.value)).dp),
                    )
                }
            }
        }
    }
}

// ── The demo transcript (useDemoTranscript) ───────────────────────────────────────────────

/** What the demo voice "says", a word at a time while it is heard. */
internal val DemoTranscriptWords = "Set a timer for ten minutes and remind me to water the plants".split(" ")

/**
 * Paces the phone's transcript from the level the beam reports each frame: a word every
 * ~320 ms while the voice is up, cleared after 2.5 s of silence; a voice after ≥ 1.2 s of
 * silence starts a new run, which replays the prompt's entrance.
 */
@Stable
internal class DemoTranscript {
    var words: List<String> by mutableStateOf(emptyList())
        private set
    var runKey: Int by mutableIntStateOf(0)
        private set

    private val clock = TimeSource.Monotonic.markNow()
    private var silentSince = 0L
    private var wasSilent = true
    private var index = 0
    private var lastWordAt = -10_000L
    private var cleared = true

    fun onLevel(level: Float) {
        val now = clock.elapsedNow().inWholeMilliseconds
        if (level < 0.05f) {
            if (!wasSilent) silentSince = now
            wasSilent = true
            if (!cleared && now - silentSince > 2500) {
                cleared = true
                index = 0
                words = emptyList()
            }
        } else if (wasSilent && level > 0.2f) {
            wasSilent = false
            if (now - silentSince > 1200) restart()
        }
        if (level > 0.22f && now - lastWordAt > 320 && index < DemoTranscriptWords.size) {
            index++
            lastWordAt = now
            cleared = false
            words = DemoTranscriptWords.take(index)
        }
    }

    fun restart() {
        runKey++
        index = 0
        cleared = true
        words = emptyList()
    }
}

private object VoiceIcons {
    val Mic16 = stroke(
        16f, 1.5f,
        "M12.6667 6.66634V7.99967C12.6667 10.577 10.5774 12.6663 8.00004 12.6663M3.33337 6.66634V7.99967C3.33337 10.577 5.42271 12.6663 8.00004 12.6663M8.00004 12.6663V14.6663M5.33337 14.6663H10.6667M8.00004 9.99967C6.89547 9.99967 6.00004 9.10424 6.00004 7.99967V3.33301C6.00004 2.22844 6.89547 1.33301 8.00004 1.33301C9.10461 1.33301 10 2.22844 10 3.33301V7.99967C10 9.10424 9.10461 9.99967 8.00004 9.99967Z",
    )
    val Mic20 = stroke(
        20f, 2f,
        "M15.8334 8.33293V9.99959C15.8334 13.2213 13.2217 15.8329 10.0001 15.8329M4.16672 8.33293V9.99959C4.16672 13.2213 6.77839 15.8329 10.0001 15.8329M10.0001 15.8329V18.3329M6.66672 18.3329H13.3334M10.0001 12.4996C8.61934 12.4996 7.50005 11.3803 7.50005 9.99959V4.16626C7.50005 2.78555 8.61934 1.66626 10.0001 1.66626C11.3808 1.66626 12.5001 2.78555 12.5001 4.16626V9.99959C12.5001 11.3803 11.3808 12.4996 10.0001 12.4996Z",
    )
    val Chevron15 = stroke(16f, 1.5f, "M7 11L10 8L7 5")
    val Chevron16 = stroke(16f, 2f, "M7 11L10 8L7 5")

    private fun stroke(viewport: Float, width: Float, d: String): ImageVector =
        ImageVector.Builder("VoiceIcon", viewport.dp, viewport.dp, viewport, viewport)
            .addPath(addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            .build()
}
