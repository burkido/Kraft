package com.burkido.kraft.library

import androidx.compose.runtime.Composable

/** What a library's detail page shows under the shared head: copy, snippets and the preview. */
interface LibraryPage {
    val subtitle: String

    /** The agent prompt the "Copy prompt" button puts on the clipboard. */
    val prompt: String

    val usageCode: String

    val installCode: String get() = INSTALL_CODE

    val installNote: String? get() = INSTALL_NOTE

    /** The "Preview" tab: examples wearing the effect, then the playground and its snippet. */
    @Composable
    fun Preview()
}

fun pageFor(id: LibraryId): LibraryPage = when (id) {
    LibraryId.Beam -> BeamPage
    LibraryId.Orbs -> OrbsPage
    LibraryId.Voice -> VoicePage
    LibraryId.Gooey -> GooeyPage
    LibraryId.Metal -> MetalPage
    LibraryId.Image -> ImagePage
    LibraryId.Bots -> BotsPage
}

private val INSTALL_CODE = """
    // settings.gradle.kts
    include(":effects")

    // build.gradle.kts of your shared module
    commonMain.dependencies {
        implementation(project(":effects"))
    }
""".trimIndent()

private const val INSTALL_NOTE =
    "The effects ship as this repo's :effects module for Android (API 31+), iOS and Desktop; they are not on Maven yet."
