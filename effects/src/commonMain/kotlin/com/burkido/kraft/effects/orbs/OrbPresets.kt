package com.burkido.kraft.effects.orbs

import com.burkido.kraft.effects.core.jsRound
import kotlin.math.max
import kotlin.math.sqrt

/** The nine loading states. [label] is the web component's `aria-label`. */
enum class OrbState(internal val mode: OrbMode, val label: String) {
    Working(OrbMode.Orbits, "Working…"),
    Searching(OrbMode.Globe, "Searching…"),
    Solving(OrbMode.Rubik, "Solving…"),
    Listening(OrbMode.Wave, "Listening…"),
    Connecting(OrbMode.Web, "Connecting…"),
    Weaving(OrbMode.Braid, "Weaving…"),
    Composing(OrbMode.Ribbon, "Composing…"),
    Breathing(OrbMode.Ring, "Thinking…"),
    Shaping(OrbMode.Morph, "Shaping…"),
}

/** The tuned sizes. 32 is interpolated upstream; 64 and 20 are the reference tunings. */
enum class OrbSize(val px: Int) { S64(64), S32(32), S20(20) }

internal enum class OrbMode { Orbits, Globe, Rubik, Wave, Web, Braid, Ribbon, Ring, Morph }

/** A mode's draw options (`ModeOpts`): named numbers, read with defaults by the mode functions. */
class OrbOpts internal constructor(internal val values: Map<String, Double>) {
    operator fun get(key: String): Double? = values[key]

    internal fun or(key: String, default: Double): Double = values[key] ?: default

    override fun toString(): String = values.toString()
}

internal class ResolvedOrb(val mode: OrbMode, val speed: Double, val opts: OrbOpts)

private class Preset(val speed: Double, val count: Double, val size: Double, val extra: Map<String, Double> = emptyMap())

// Grid pairs scale by √scale per side so the TOTAL count scales by `scale`; flat counts linearly.
private val COUNT_PAIRS = listOf("latRings" to "lonDensity", "rings" to "lonDensity", "lanes" to "segs")
private val COUNT_KEYS = listOf("orbitN", "ghostN", "nodeN", "strandN", "signals")
private val RADIUS_KEYS = listOf("rBase", "rDepth", "rActive", "rDot", "ghostR", "partR", "partRDepth", "nodeR", "nodeRDepth")

internal fun scaleCounts(opts: Map<String, Double>, scale: Double): Map<String, Double> {
    val out = LinkedHashMap(opts)
    val done = HashSet<String>()
    val rt = sqrt(scale)
    for ((a, b) in COUNT_PAIRS) {
        val va = out[a]
        val vb = out[b]
        if (va != null && vb != null && a !in done && b !in done) {
            out[a] = max(2.0, jsRound(va * rt))
            out[b] = max(2.0, jsRound(vb * rt))
            done += a
            done += b
        }
    }
    for (k in COUNT_KEYS) {
        val v = out[k]
        // 0 means the mode opted out of that layer; scaling must not resurrect it.
        if (v != null && v != 0.0 && k !in done) out[k] = max(1.0, jsRound(v * scale))
    }
    out["iconD"]?.let { out["iconD"] = max(0.02, it * scale) }
    return out
}

internal fun scaleRadii(opts: Map<String, Double>, scale: Double): Map<String, Double> {
    val out = LinkedHashMap(opts)
    for (k in RADIUS_KEYS) out[k]?.let { out[k] = it * scale }
    out["rSizeMul"] = (out["rSizeMul"] ?: 1.0) * scale
    return out
}

/** Base ("fine") profiles per mode, before preset multipliers. */
private val BASE_PROFILES: Map<OrbMode, Map<String, Double>> = mapOf(
    OrbMode.Globe to linkedMapOf(
        "latRings" to 17.0, "lonDensity" to 44.0, "rBase" to 0.6, "rDepth" to 1.7, "rBoost" to 1.0,
        "inkFar" to 0.62, "inkSpan" to 0.54, "rsPow" to 0.6, "rMin" to 0.3,
    ),
    OrbMode.Orbits to linkedMapOf(
        "orbitN" to 12.0, "ghostN" to 40.0, "ghostR" to 0.9, "ghostA" to 0.5, "particles" to 3.0,
        "partR" to 1.2, "partRDepth" to 1.6, "rsPow" to 0.6, "rMin" to 0.3,
    ),
    OrbMode.Rubik to linkedMapOf(
        "latRings" to 15.0, "lonDensity" to 40.0, "moveCount" to 14.0, "rBase" to 0.6, "rDepth" to 1.7,
        "rActive" to 0.3, "inkFar" to 0.62, "inkSpan" to 0.54, "rsPow" to 0.6, "rMin" to 0.3,
    ),
    OrbMode.Wave to linkedMapOf(
        "rings" to 15.0, "lonDensity" to 40.0, "rBase" to 0.6, "rDepth" to 1.7, "rsPow" to 0.6, "rMin" to 0.3,
    ),
    OrbMode.Web to linkedMapOf(
        "nodeN" to 30.0, "thr" to 0.72, "signals" to 5.0, "nodeR" to 1.4, "nodeRDepth" to 1.8,
        "lineW" to 0.8, "rsPow" to 0.6, "rMin" to 0.3,
    ),
    OrbMode.Braid to linkedMapOf(
        "strandN" to 52.0, "turns" to 3.0, "ghostN" to 150.0, "rBase" to 1.2, "rDepth" to 1.8,
        "rsPow" to 0.6, "rMin" to 0.3,
    ),
    OrbMode.Ribbon to linkedMapOf(
        "lanes" to 5.0, "segs" to 88.0, "ghostN" to 150.0, "rBase" to 1.1, "rDepth" to 1.7,
        "rsPow" to 0.6, "rMin" to 0.3,
    ),
    // Ring shares ribbon's geometry: faceOn cancels the camera tilt, no ghost sphere.
    OrbMode.Ring to linkedMapOf(
        "lanes" to 5.0, "segs" to 88.0, "ghostN" to 0.0, "faceOn" to 1.0, "rBase" to 1.1, "rDepth" to 1.7,
        "rsPow" to 0.6, "rMin" to 0.3,
    ),
    OrbMode.Morph to linkedMapOf("rDot" to 0.021, "iconD" to 1.0, "rMin" to 0.25),
)

/** The shipped tunings (`src/presets.ts`): nine modes × three sizes. */
private val PRESETS: Map<OrbMode, Map<OrbSize, Preset>> = mapOf(
    OrbMode.Orbits to mapOf(
        OrbSize.S64 to Preset(1.885, 1.0, 1.0),
        OrbSize.S32 to Preset(2.9072, 0.4251, 1.6849),
        OrbSize.S20 to Preset(3.9, 0.238, 2.4),
    ),
    OrbMode.Globe to mapOf(
        OrbSize.S64 to Preset(2.015, 0.42, 1.15, mapOf("scanMul" to 4.08, "dimBase" to 0.45)),
        OrbSize.S32 to Preset(2.3803, 0.1839, 1.4769, mapOf("scanMul" to 4.2301, "dimBase" to 0.45)),
        OrbSize.S20 to Preset(2.665, 0.105, 1.75, mapOf("scanMul" to 4.335, "dimBase" to 0.45)),
    ),
    OrbMode.Rubik to mapOf(
        OrbSize.S64 to Preset(1.82, 0.35, 1.05),
        OrbSize.S32 to Preset(1.8964, 0.1537, 1.4951),
        OrbSize.S20 to Preset(1.95, 0.088, 1.9),
    ),
    OrbMode.Wave to mapOf(
        OrbSize.S64 to Preset(4.388, 0.341, 1.0),
        OrbSize.S32 to Preset(4.1512, 0.169, 1.3232),
        OrbSize.S20 to Preset(3.998, 0.105, 1.6),
    ),
    OrbMode.Web to mapOf(
        OrbSize.S64 to Preset(3.315, 1.35, 0.95),
        OrbSize.S32 to Preset(5.0104, 0.4942, 1.2571),
        OrbSize.S20 to Preset(6.63, 0.25, 1.52),
    ),
    OrbMode.Braid to mapOf(
        OrbSize.S64 to Preset(1.625, 0.5, 1.0),
        OrbSize.S32 to Preset(2.2234, 0.2056, 1.2011),
        OrbSize.S20 to Preset(2.75, 0.1125, 1.36),
    ),
    OrbMode.Ribbon to mapOf(
        OrbSize.S64 to Preset(2.34, 0.25, 0.85, mapOf("spin" to 0.0, "bandMul" to 3.9, "wobMul" to 1.0)),
        OrbSize.S32 to Preset(2.7776, 0.0969, 0.9766, mapOf("spin" to 0.0, "bandMul" to 4.49, "wobMul" to 1.0)),
        OrbSize.S20 to Preset(3.12, 0.051, 1.073, mapOf("spin" to 0.0, "bandMul" to 4.94, "wobMul" to 1.0)),
    ),
    OrbMode.Ring to mapOf(
        OrbSize.S64 to Preset(3.24, 0.25, 0.956, mapOf("spin" to 0.0, "bandMul" to 3.627, "wobMul" to 0.368)),
        OrbSize.S32 to Preset(3.5517, 0.0678, 1.31, mapOf("spin" to 0.0, "bandMul" to 3.8265, "wobMul" to 0.4751)),
        OrbSize.S20 to Preset(3.78, 0.028, 1.622, mapOf("spin" to 0.0, "bandMul" to 3.968, "wobMul" to 0.565)),
    ),
    OrbMode.Morph to mapOf(
        OrbSize.S64 to Preset(2.405, 0.702, 0.395, mapOf("spread" to 1.45)),
        OrbSize.S32 to Preset(2.2057, 0.5937, 0.6916, mapOf("spread" to 1.45)),
        OrbSize.S20 to Preset(2.08, 0.53, 1.011, mapOf("spread" to 1.45)),
    ),
)

private val resolvedCache = HashMap<Pair<OrbState, OrbSize>, ResolvedOrb>()

/** `resolvePreset()`: a (state, size) pair → its mode, clock speed and fully scaled options. */
internal fun resolvePreset(state: OrbState, size: OrbSize): ResolvedOrb = resolvedCache.getOrPut(state to size) {
    val mode = state.mode
    val preset = PRESETS.getValue(mode).getValue(size)
    var opts: Map<String, Double> = LinkedHashMap(BASE_PROFILES.getValue(mode))
    if (preset.count != 1.0) opts = scaleCounts(opts, preset.count)
    if (preset.size != 1.0) opts = scaleRadii(opts, preset.size)
    if (preset.extra.isNotEmpty()) opts = LinkedHashMap(opts).apply { putAll(preset.extra) }
    ResolvedOrb(mode, preset.speed, OrbOpts(opts))
}
