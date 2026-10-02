package com.burkido.kraft.film.shots

/** The cut, in order. */
object Shots {
    val all = listOf(
        Opening,
        Title,
        Orbs,
        Beam,
        Metal,
        Gooey,
        Voice,
        Bots,
        Image,
        Code,
        Platforms,
        Finale,
        End,
    )

    /** Passes that are not part of the cut, rendered on request (`--shots finale-matte`). */
    val extra = listOf(FinaleImageMatte)
}
