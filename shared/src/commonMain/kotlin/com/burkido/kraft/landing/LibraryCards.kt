package com.burkido.kraft.landing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.burkido.kraft.designsystem.KraftColors
import com.burkido.kraft.designsystem.LocalKraftFonts
import com.burkido.kraft.designsystem.LocalViewport
import com.burkido.kraft.designsystem.StageRing
import com.burkido.kraft.designsystem.animatedStateColor
import com.burkido.kraft.designsystem.clickableRaw
import com.burkido.kraft.designsystem.cssSurface
import com.burkido.kraft.designsystem.cssText
import com.burkido.kraft.designsystem.materialShadows
import com.burkido.kraft.designsystem.rememberInteraction
import com.burkido.kraft.effects.core.primaryPointerCanHover
import com.burkido.kraft.library.LibraryId
import org.jetbrains.compose.resources.painterResource

/**
 * `.cards`: a six-track grid. Beam and Orbs span three tracks, the rest two, so the rows read
 * 2 · 3 · 2; below 1100 px it becomes two columns (max 664), below 740 px one.
 */
@Composable
fun LibraryCards(onOpenLibrary: (LibraryId) -> Unit, modifier: Modifier = Modifier) {
    val vp = LocalViewport.current
    val oneColumn = vp.maxWidth(740)
    val twoColumns = !oneColumn && vp.maxWidth(1100)
    val gap = if (oneColumn) 16.dp else 24.dp
    val rows: List<List<LibraryId?>> = when {
        oneColumn -> LibraryId.entries.map { listOf(it) }
        twoColumns -> LibraryId.entries.chunked(2).map { if (it.size == 2) it else it + null }
        else -> listOf(
            listOf(LibraryId.Beam, LibraryId.Orbs),
            listOf(LibraryId.Gooey, LibraryId.Voice, LibraryId.Bots),
            listOf(LibraryId.Metal, LibraryId.Image, null),
        )
    }
    Column(
        modifier
            .widthIn(max = if (twoColumns) 664.dp else if (oneColumn) Dp.Unspecified else 1008.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        for (row in rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (id in row) {
                    if (id == null) Spacer(Modifier.weight(1f)) else LibraryCard(id, oneColumn, { onOpenLibrary(id) }, Modifier.weight(1f))
                }
            }
        }
    }
}

/** `.lib-card`: a raised card with the live preview stage and the icon + name + description row. */
@Composable
private fun LibraryCard(id: LibraryId, compact: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val fonts = LocalKraftFonts.current
    val interaction = rememberInteraction()
    val bg by animatedStateColor(interaction, KraftColors.Card, KraftColors.CardHover)
    val stageHeight = if (compact) 200.dp else 254.dp
    Box(
        modifier
            .height(if (compact) 290.dp else 344.dp)
            .cssSurface(24.dp, materialShadows()) { bg }
            .clickableRaw(interaction, onClick = onClick),
    ) {
        BoxWithConstraints(
            Modifier
                .padding(start = 12.dp, top = 12.dp, end = 12.dp)
                .fillMaxWidth()
                .height(stageHeight)
                .clip(RoundedCornerShape(14.dp))
                .cssSurface(14.dp) { KraftColors.Stage },
            contentAlignment = Alignment.Center,
        ) {
            // As the site's clips: on hover where there is hover, always (while on screen) on touch.
            LibraryScene(id, maxWidth, maxHeight, compact = false, playing = !primaryPointerCanHover || interaction.hovered)
            Box(Modifier.fillMaxSize().cssSurface(14.dp, StageRing))
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(id.icon),
                contentDescription = null,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                BasicText(id.title, style = cssText(fonts.sans, 14f, 13f, FontWeight.Medium, Color(0xFFEDEDED)))
                BasicText(
                    id.tagline,
                    style = cssText(fonts.sans, 14f, 13f, FontWeight.Normal, KraftColors.TextSubtle),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
