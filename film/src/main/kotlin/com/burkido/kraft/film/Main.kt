package com.burkido.kraft.film

import com.burkido.kraft.film.shots.FilmVoice
import com.burkido.kraft.film.shots.Shots
import com.burkido.kraft.film.shots.VoiceTakes
import java.io.File
import kotlin.system.exitProcess

/**
 * `--shots open,title|all --quality draft|preview|final|uhd [--still 2.5] [--voice onyx|sage]`
 *
 * Writes film/build/shots/<quality>/<shot>.mp4, or with `--still t` one PNG per shot at time t.
 */
fun main(args: Array<String>) {
    fun arg(name: String) = args.indexOf("--$name").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    val quality = Quality.named(arg("quality") ?: "preview")
    val wanted = (arg("shots") ?: "all").split(",").map { it.trim() }
    val shots = if (wanted == listOf("all")) Shots.all else wanted.map { n -> (Shots.all + Shots.extra).firstOrNull { it.name == n } ?: error("no shot '$n'; have ${(Shots.all + Shots.extra).map { it.name }}") }
    val still = arg("still")?.toDouble()
    arg("voice")?.let { v -> FilmVoice = VoiceTakes.firstOrNull { it.name == v } ?: error("no voice '$v'; have ${VoiceTakes.map { it.name }}") }
    for (shot in shots) {
        if (still != null) {
            renderStill(shot, quality, still, File("film/build/stills/${shot.name}-$still.png"))
        } else {
            renderShot(shot, quality, File("film/build/shots/${quality.name}/${shot.name}.${quality.ext}"))
        }
    }
    exitProcess(0)
}
