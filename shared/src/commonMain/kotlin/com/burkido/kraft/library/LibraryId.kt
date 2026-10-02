package com.burkido.kraft.library

import com.burkido.kraft.resources.Res
import com.burkido.kraft.resources.side_avatars
import com.burkido.kraft.resources.side_beam
import com.burkido.kraft.resources.side_gooey
import com.burkido.kraft.resources.side_image
import com.burkido.kraft.resources.side_metal
import com.burkido.kraft.resources.side_orbs
import com.burkido.kraft.resources.side_voice
import com.burkido.kraft.resources.tile_avatars
import com.burkido.kraft.resources.tile_beam
import com.burkido.kraft.resources.tile_gooey
import com.burkido.kraft.resources.tile_image
import com.burkido.kraft.resources.tile_metal
import com.burkido.kraft.resources.tile_orbs
import com.burkido.kraft.resources.tile_voice
import org.jetbrains.compose.resources.DrawableResource

/** The seven libraries, in the order libraries.dev lists them. */
enum class LibraryId(
    /** Card and page title. */
    val title: String,
    /** The one-line description under the title. */
    val tagline: String,
    /** Short name for footers and chips. */
    val shortName: String,
    val icon: DrawableResource,
    /** The sidebar's small icon art. */
    val sideIcon: DrawableResource,
    /** The detail page's `<h1>`. */
    val pageTitle: String,
) {
    Beam("Border beam", "A soft glow that rides the border", "Border beam", Res.drawable.tile_beam, Res.drawable.side_beam, "Border beam"),
    Orbs("Thinking orbs", "Orbs that think while you wait", "Thinking orbs", Res.drawable.tile_orbs, Res.drawable.side_orbs, "Thinking orbs"),
    Gooey("Gooey", "Pieces that merge like goo", "Gooey", Res.drawable.tile_gooey, Res.drawable.side_gooey, "Gooey"),
    Voice("Voice", "A glow that rises with your voice", "Voice", Res.drawable.tile_voice, Res.drawable.side_voice, "Voice"),
    Bots("Bot avatars", "Animated faces for your AI agents", "Bot avatars", Res.drawable.tile_avatars, Res.drawable.side_avatars, "Bot avatars"),
    Metal("Liquid Metal", "Liquid metal for your buttons", "Metal", Res.drawable.tile_metal, Res.drawable.side_metal, "Metal"),
    Image("Image generation", "A loader that becomes the image", "Image", Res.drawable.tile_image, Res.drawable.side_image, "Image"),
}
