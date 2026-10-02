package com.burkido.kraft.landing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.burkido.kraft.designsystem.BrandLockup
import com.burkido.kraft.designsystem.ChipPill
import com.burkido.kraft.designsystem.KraftColors
import com.burkido.kraft.designsystem.KraftIcons
import com.burkido.kraft.designsystem.KraftMotion
import com.burkido.kraft.designsystem.LocalKraftFonts
import com.burkido.kraft.designsystem.LocalViewport
import com.burkido.kraft.designsystem.NavPill
import com.burkido.kraft.designsystem.TightTracking
import com.burkido.kraft.designsystem.TintPill
import com.burkido.kraft.designsystem.animatedStateColor
import com.burkido.kraft.designsystem.clickableRaw
import com.burkido.kraft.designsystem.cssBlur
import com.burkido.kraft.designsystem.cssSurface
import com.burkido.kraft.designsystem.cssText
import com.burkido.kraft.designsystem.rememberInteraction
import kotlinx.coroutines.delay

/** Where the ported designs come from; the source pill links to it. */
const val UPSTREAM_URL = "https://github.com/Jakubantalik/Libraries.dev"

/** `.site-nav`: brand + pills on the left; tint CTA, source pill and burger on the right. */
@Composable
fun SiteNav(
    onLibraries: () -> Unit,
    onHowTo: () -> Unit,
    onGetStarted: () -> Unit,
    menuOpen: Boolean,
    onMenuToggle: () -> Unit,
    modifier: Modifier = Modifier,
    librariesActive: Boolean = false,
) {
    val vp = LocalViewport.current
    val phone = vp.maxWidth(639)
    val uri = LocalUriHandler.current
    Row(
        modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(34.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandLockup(word = "Kraft", dimWord = "UI", onClick = {})
            if (!phone) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NavPill("Libraries", onLibraries, active = librariesActive)
                    NavPill("How to use", onHowTo)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TintPill(
                text = "Get started",
                onClick = onGetStarted,
                height = if (phone) 34.dp else 36.dp,
                horizontalPadding = when {
                    vp.maxWidth(380) -> 10.dp
                    phone -> 12.dp
                    else -> 16.dp
                },
            )
            // The site hides its GitHub pill between 640 and 900 px and shows it again on phones.
            if (!vp.maxWidth(900) || phone) ChipPill("Source", KraftIcons.GitHub, onClick = { uri.openUri(UPSTREAM_URL) })
            if (phone) BurgerButton(open = menuOpen, onClick = onMenuToggle)
        }
    }
}

/** `.nav-burger`: two lines that meet at 7.25 px and cross into an X. */
@Composable
private fun BurgerButton(open: Boolean, onClick: () -> Unit) {
    val interaction = rememberInteraction()
    val bg by animatedStateColor(interaction, KraftColors.Chip, KraftColors.ChipHover, KraftColors.ChipPressed)
    val tint by animatedStateColor(interaction, KraftColors.IconMuted, KraftColors.Icon)
    val topY by animateFloatAsState(if (open) 7.25f else 5f, tween(300, easing = KraftMotion.SmoothOut))
    val bottomY by animateFloatAsState(if (open) 7.25f else 9.5f, tween(300, easing = KraftMotion.SmoothOut))
    // Closing, the lines un-cross 50 ms after they start moving apart.
    val angle by animateFloatAsState(
        if (open) 45f else 0f,
        tween(300, delayMillis = if (open) 0 else 50, easing = KraftMotion.SmoothOut),
    )
    Box(
        Modifier.size(36.dp).cssSurface(50.dp) { bg }.clickableRaw(interaction, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(16.dp)) {
            BurgerLine(topY, angle, tint)
            BurgerLine(bottomY, -angle, tint)
        }
    }
}

@Composable
private fun BurgerLine(top: Float, rotation: Float, color: Color) {
    Box(
        Modifier
            .offset(x = 1.dp, y = top.dp)
            .width(14.dp)
            .height(1.5.dp)
            .graphicsLayer { rotationZ = rotation }
            .cssSurface(2.dp) { color },
    )
}

/**
 * The phone menu: a dark, blurred backdrop (in 400 ms, out 250 ms) and links that rise 12 dp and
 * un-blur 3 dp over 500 ms, 40 ms apart; the bottom CTA follows 160 ms later.
 */
@Composable
fun MobileMenu(open: Boolean, onDismiss: () -> Unit, onLibraries: () -> Unit, onHowTo: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val backdrop = remember { Animatable(0f) }
    LaunchedEffect(open) {
        if (open) {
            visible = true
            backdrop.animateTo(1f, tween(400, easing = KraftMotion.SmoothOut))
        } else if (visible) {
            backdrop.animateTo(0f, tween(250, easing = KraftMotion.SmoothOut))
            visible = false
        }
    }
    if (!visible) return
    val fonts = LocalKraftFonts.current
    val vp = LocalViewport.current
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = backdrop.value }
                .background(Color(18, 18, 18).copy(alpha = 0.9f))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = vp.gutter)) {
            SiteNav(onLibraries = onLibraries, onHowTo = onHowTo, onGetStarted = onHowTo, menuOpen = open, onMenuToggle = onDismiss)
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    // `padding: 88px 30px 40px` from the screen top; the nav row already takes 68.
                    .padding(start = 30.dp - vp.gutter, end = 30.dp - vp.gutter, top = 20.dp, bottom = 40.dp),
            ) {
                val links = listOf("Libraries" to onLibraries, "How to use" to onHowTo, "Get started" to onHowTo)
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    links.forEachIndexed { i, (label, action) ->
                        MenuReveal(open, delayMs = i * KraftMotion.Stagger) {
                            BasicText(
                                label,
                                style = cssText(
                                    fonts.sans, 20f, 24f, FontWeight.Medium,
                                    if (i == links.lastIndex) KraftColors.ProInk else KraftColors.Text,
                                    TightTracking,
                                ),
                                modifier = Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = action),
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                MenuReveal(open, delayMs = 160) {
                    val interaction = rememberInteraction()
                    val bg by animatedStateColor(interaction, Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.1f))
                    Box(
                        Modifier.fillMaxWidth().height(44.dp).cssSurface(26.dp) { bg }.clickableRaw(interaction, onClick = onLibraries),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText("Browse libraries", style = cssText(fonts.sans, 16f, 16f, FontWeight.Medium, Color(0xFFFBFBFB)))
                    }
                }
            }
        }
    }
}

/** One menu entry's entrance: opacity 0→1, rise 12→0 dp, blur 3→0 dp; exits with a 200 ms fade. */
@Composable
private fun MenuReveal(open: Boolean, delayMs: Int, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(open) {
        if (open) {
            progress.snapTo(0f)
            fade.snapTo(0f)
            delay(delayMs.toLong())
            fade.snapTo(1f)
            progress.animateTo(1f, tween(KraftMotion.VerySlow, easing = KraftMotion.SmoothOut))
        } else {
            fade.animateTo(0f, tween(200, easing = KraftMotion.Ease))
        }
    }
    val p = progress.value
    Box(
        Modifier
            .graphicsLayer {
                alpha = p * fade.value
                translationY = (1f - p) * 12.dp.toPx()
            }
            .cssBlur(((1f - p) * 3f).dp),
    ) {
        content()
    }
}
