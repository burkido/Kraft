package com.burkido.kraft.effects.image

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** `MASK_SIZE`: the lattice the reveal mask shape is evaluated on. */
internal const val MaskSize = 64

// Boil ("regenerating") tuning (`reveal.ts`).
private const val BoilPixInMs = 800f
private const val BoilPixOverlapMs = 300f
private const val BoilRampS = 1.0f
private const val BoilLevel = 0.5f
private const val BoilBand = 0.03f
private const val BoilFieldW = 0.6f
internal const val BoilBlendMs = 800f
private const val BoilBaseMs = 400f
private const val BoilSharpenMs = 1200f

private fun smooth01(t: Float) = t * t * (3 - 2 * t)

/**
 * The boil's clock at [sinceStartMs] into the churn, [handMs] into a pending reveal's handoff
 * (negative when none): the scalars both the main thread (opacities, completion) and the worker
 * (per-cell alphas) read, so the two can never disagree.
 */
internal class BoilTimeline(sinceStartMs: Float, handMs: Float, pending: Boolean) {
    val blendT = if (handMs < 0f) 0f else smooth01(min(1f, handMs / BoilBlendMs))
    private val rt = if (handMs < 0f) -1f else handMs - BoilBlendMs
    val baseT = if (rt <= 0f) 0f else smooth01(min(1f, rt / BoilBaseMs))
    val sharpRaw = if (rt <= 0f) 0f else min(1f, rt / BoilSharpenMs)
    val resolving = pending && rt > 0f
    private val inRaw = min(1f, sinceStartMs / BoilPixInMs)
    val inT = smooth01(inRaw)
    val pixelatingIn = inRaw < 1f
    private val baseOutRaw = min(1f, max(0f, sinceStartMs - (BoilPixInMs - BoilPixOverlapMs)) / BoilPixOverlapMs)
    val pixInBaseAlpha = 1f - smooth01(baseOutRaw)
    private val rampRaw = min(1f, max(0f, sinceStartMs - (BoilPixInMs - BoilPixOverlapMs)) / (BoilRampS * 1000f))
    val ramp = smooth01(rampRaw)
    val boilT = ramp * BoilLevel

    /** The new image is sharp: the boil settles into `hold`. */
    val complete = resolving && sharpRaw >= 1f && baseT >= 1f
}

/** The web's flicker hash, in JS doubles: `fract(sin(i·127.1 + s·17.23)·43758.5453)`. */
private fun cellHash(i: Int, s: Int): Double {
    val x = sin(i * 127.1 + s * 17.23) * 43758.5453
    return x - floor(x)
}

/** A frame's flicker clock (`gsT`): the stepped hash index pair and the smoothed blend between. */
internal class FlickerClock(time: Double, presetSpeed: Float) {
    val step: Int
    val step1: Int
    val fz: Double

    init {
        val t = time * max(presetSpeed, 2f) * 1.6
        val raw = floor(t)
        step = (raw.toLong() % 1024).toInt()
        step1 = (step + 1) % 1024
        val f = t - raw
        fz = f * f * (3 - 2 * f)
    }

    fun rnd(i: Int): Double = cellHash(i, step) * (1 - fz) + cellHash(i, step1) * fz
}

/**
 * The reveal overlay's per-cell state (worker side): the pixel-dissolve grid's chunky colours
 * with their drop alpha, and the reveal mask quantised to that grid — plus the boil's churn.
 * Owns the random patterns, reseeded per reveal / boil, and the throttled shader-field sample.
 */
internal class RevealCells(private val shader: ImageShader) {
    var gridX = 0
        private set
    var gridY = 0
        private set

    /** Chunky cells, premultiplied RGBA, rows top-down. */
    var chunky = ByteArray(0)
        private set

    /** The quantised reveal mask, white with per-cell alpha, rows top-down. */
    var mask = ByteArray(0)
        private set

    private var imageCells = FloatArray(0)
    private var imageCellsB = FloatArray(0)
    private var cellsFor: RevealImage? = null
    private var cellsForB: RevealImage? = null
    private var cellsKey = 0L

    private var pattern = FloatArray(0)
    private var patternSeq = -1
    private var boilPattern = FloatArray(0)
    private var boilSeq = -1
    private var field = FloatArray(0)
    private var fieldValid = false

    private val sample = FloatArray(MaskSize * MaskSize * 3)
    private var sampleValid = false
    private var sampleCounter = 0
    private var sampleSeq = -1
    private val mask64 = FloatArray(MaskSize * MaskSize)
    private var gsTable = FloatArray(0)
    private val random = Random.Default

    /** The reveal grid (`paintPixelMasked`): anisotropic, square cells, from the CSS size. */
    private fun grid(cssW: Int, cssH: Int) {
        val base = 6f + shader.cellSize * 74f
        val gx = max(2, floor(base * max(1, cssW) / RefDim).toInt())
        val gy = max(2, floor(base * max(1, cssH) / RefDim).toInt())
        if (gx != gridX || gy != gridY) {
            gridX = gx
            gridY = gy
            chunky = ByteArray(gx * gy * 4)
            mask = ByteArray(gx * gy * 4)
            imageCells = FloatArray(gx * gy * 3)
            imageCellsB = FloatArray(gx * gy * 3)
            cellsFor = null
            cellsForB = null
            pattern = FloatArray(0)
            boilPattern = FloatArray(0)
            field = FloatArray(0)
            fieldValid = false
        }
    }

    private fun cellsOf(image: RevealImage, cssW: Int, cssH: Int, b: Boolean): FloatArray {
        val key = cssW.toLong() shl 32 or cssH.toLong()
        if (key != cellsKey) { cellsKey = key; cellsFor = null; cellsForB = null }
        return if (!b) {
            if (cellsFor !== image) { image.cellColors(cssW.toFloat(), cssH.toFloat(), gridX, gridY, imageCells); cellsFor = image }
            imageCells
        } else {
            if (cellsForB !== image) { image.cellColors(cssW.toFloat(), cssH.toFloat(), gridX, gridY, imageCellsB); cellsForB = image }
            imageCellsB
        }
    }

    private fun sampleField(frame: FrameInput, every: Int) {
        if (sampleSeq != frame.overlaySeq) { sampleSeq = frame.overlaySeq; sampleValid = false; sampleCounter = 0 }
        val counter = sampleCounter++
        if (!sampleValid || counter % every == 0) {
            sampleRawField(shader, frame.iw, frame.ih, frame.dpr, frame.shaderTime, MaskSize, sample)
            sampleValid = true
            fieldValid = false
        }
    }

    /** One reveal frame (`paintMaskedFrame` + `paintPixelMasked`, pixel presets). */
    fun reveal(frame: FrameInput, image: RevealImage) {
        val mode = frame.mode
        val cfg = mode.reveal
        grid(frame.cssW, frame.cssH)
        val gx = gridX
        val gy = gridY
        val n = gx * gy
        val progress = min(frame.elapsed / max(0.05f, cfg.duration), 1f)
        val eased = cfg.easing.apply(progress)
        val shape = cfg.maskShape
        val softBand = cfg.softness

        var target: Color? = null
        var highlights: List<Color>? = null
        if (shape.fromShader) {
            sampleField(frame, every = 2)
            if (shape.colorSlot >= 0) target = effectivePaletteColor(mode, shape.colorSlot, frame.cardBgOverride)
            if (shape == MaskShape.ShaderHighlight) {
                val bg = frame.cardBgOverride ?: com.burkido.kraft.effects.core.parseCssColor(mode.cardBg)
                highlights = (0 until 5).map { effectivePaletteColor(mode, it, frame.cardBgOverride) }.filter {
                    val dr = it.red - bg.red; val dg = it.green - bg.green; val db = it.blue - bg.blue
                    sqrt(dr * dr + dg * dg + db * db) > 0.15f
                }.ifEmpty { listOf(effectivePaletteColor(mode, 0, frame.cardBgOverride)) }
            }
        }

        // The mask's flicker jitter on its transition edge (square gsGrid, per-frame table).
        val threshold = 1 - eased * (1 + softBand)
        val sweep = shape == MaskShape.GradientSweep
        val amp = mode.flicker
        val clock = FlickerClock(frame.time, mode.speed)
        val gsGrid = max(2, floor(6f + shader.cellSize * 74f).toInt())
        val flickerOn = amp > 0.003f
        if (flickerOn) {
            val cells = gsGrid * gsGrid
            if (gsTable.size != cells) gsTable = FloatArray(cells)
            for (i in 0 until cells) gsTable[i] = ((clock.rnd(i) - 0.5) * amp * 1.6).toFloat()
        }
        val gsW = if (sweep) 0.9f / max(mode.scale, 0.25f) else 0f
        val gsPos = if (sweep) -gsW + eased * (1 + 2 * gsW) else 0f

        for (r in 0 until MaskSize) {
            for (c in 0 until MaskSize) {
                var a: Float
                if (sweep) {
                    val nx = c / (MaskSize - 1f)
                    val ny = r / (MaskSize - 1f)
                    a = (gsPos + gsW - (nx + ny) * 0.5f) / (2 * gsW)
                } else {
                    val v = if (shape.fromShader) {
                        val i = (r * MaskSize + c) * 3
                        val pr = sample[i]; val pg = sample[i + 1]; val pb = sample[i + 2]
                        when {
                            highlights != null -> highlights.maxOf { h ->
                                val dr = pr - h.red; val dg = pg - h.green; val db = pb - h.blue
                                exp(-10f * (dr * dr + dg * dg + db * db))
                            }
                            target != null -> {
                                val dr = pr - target.red; val dg = pg - target.green; val db = pb - target.blue
                                exp(-8f * (dr * dr + dg * dg + db * db))
                            }
                            else -> pr * 0.299f + pg * 0.587f + pb * 0.114f
                        }
                    } else {
                        geometricMask(shape, r, c)
                    }
                    a = (v - threshold) / softBand
                }
                if (flickerOn && a > -0.5f && a < 1.5f) {
                    val at = a.coerceIn(0f, 1f)
                    val edge = at * (1 - at) * 4
                    if (edge > 0.001f) {
                        val nx = c / (MaskSize - 1f)
                        val ny = r / (MaskSize - 1f)
                        val ci = floor(ny * gsGrid).toInt().coerceAtMost(gsGrid - 1) * gsGrid + floor(nx * gsGrid).toInt().coerceAtMost(gsGrid - 1)
                        a += gsTable[ci] * edge
                    }
                }
                a = a.coerceIn(0f, 1f)
                a = a * a * (3 - 2 * a)
                mask64[r * MaskSize + c] = ((a * 255 + 0.5f).toInt()) / 255f
            }
        }

        // The per-cell chunky-to-smooth dissolve: a random drop time per cell.
        if (patternSeq != frame.overlaySeq || pattern.size != n) {
            patternSeq = frame.overlaySeq
            pattern = FloatArray(n) { 0.07f + random.nextFloat() * (1 - 2 * 0.07f) }
        }
        val fadeT = cfg.pixEasing.apply(min(1f, max(0f, frame.elapsed / max(0.05f, cfg.pixDuration))))
        val invBand2 = 1f / (2 * 0.07f)
        val dropFlicker = !sweep && flickerOn
        val colors = cellsOf(image, frame.cssW, frame.cssH, b = false)
        for (i in 0 until n) {
            val dt = pattern[i]
            var a = (0.5f + (dt - fadeT) * invBand2).coerceIn(0f, 1f)
            if (dropFlicker) {
                val prox = 1 - abs(fadeT - dt) * 6.25f
                if (prox > 0) a = (a + ((clock.rnd(i) - 0.5) * amp * 1.6 * prox).toFloat()).coerceIn(0f, 1f)
            }
            writeCell(chunky, i, colors, a)
        }

        // The mask quantised to the grid (the web's high-quality downscale of the 64² mask).
        for (cy in 0 until gy) {
            val r0 = cy * MaskSize / gy
            val r1 = max(r0 + 1, (cy + 1) * MaskSize / gy)
            for (cx in 0 until gx) {
                val c0 = cx * MaskSize / gx
                val c1 = max(c0 + 1, (cx + 1) * MaskSize / gx)
                var sum = 0f
                for (r in r0 until r1) for (c in c0 until c1) sum += mask64[r * MaskSize + c]
                val a = sum / ((r1 - r0) * (c1 - c0))
                val o = (cy * gx + cx) * 4
                val b = channel(a)
                mask[o] = b; mask[o + 1] = b; mask[o + 2] = b; mask[o + 3] = b
            }
        }
    }

    /** One boil frame (`paintBoilFrame`): the churning cells over the shader. */
    fun boil(frame: FrameInput, image: RevealImage, pendingImage: RevealImage?, tl: BoilTimeline) {
        val mode = frame.mode
        grid(frame.cssW, frame.cssH)
        val gx = gridX
        val gy = gridY
        val n = gx * gy
        if (boilSeq != frame.overlaySeq || boilPattern.size != n) {
            boilSeq = frame.overlaySeq
            boilPattern = FloatArray(n) { random.nextFloat() }
            fieldValid = false
        }
        val amp = max(mode.flicker, 0.5f)
        val clock = FlickerClock(frame.time, mode.speed)

        // The live shader field's luminance at each cell centre, normalised across the frame.
        sampleField(frame, every = 3)
        if (!fieldValid || field.size != n) {
            if (field.size != n) field = FloatArray(n)
            var fMin = 1f
            var fMax = 0f
            for (cy in 0 until gy) {
                val my = min(MaskSize - 1, ((cy + 0.5f) * MaskSize / gy).toInt())
                for (cx in 0 until gx) {
                    val mx = min(MaskSize - 1, ((cx + 0.5f) * MaskSize / gx).toInt())
                    val si = (my * MaskSize + mx) * 3
                    val lum = sample[si] * 0.299f + sample[si + 1] * 0.587f + sample[si + 2] * 0.114f
                    field[cy * gx + cx] = lum
                    fMin = min(fMin, lum)
                    fMax = max(fMax, lum)
                }
            }
            val span = if (fMax - fMin > 0.001f) 1 / (fMax - fMin) else 0f
            for (i in 0 until n) field[i] = if (span > 0f) (field[i] - fMin) * span else 0.5f
            fieldValid = true
        }

        val invBand2 = 1 / (2 * BoilBand)
        val churn = amp * 0.6f * (1 - tl.sharpRaw)
        val patW = 1 - BoilFieldW
        val fadeT = if (tl.resolving) mode.reveal.pixEasing.apply(tl.sharpRaw) else 0f
        val invBandS = 1 / (2 * 0.07f)
        val sAmp = mode.flicker
        val colorsA = cellsOf(image, frame.cssW, frame.cssH, b = false)
        val colorsB = if (pendingImage != null && tl.blendT > 0f) cellsOf(pendingImage, frame.cssW, frame.cssH, b = true) else null
        for (i in 0 until n) {
            val rnd = clock.rnd(i)
            val dtEff = field[i] * BoilFieldW + boilPattern[i] * patW + ((rnd - 0.5) * churn).toFloat()
            var a = (0.5f + (dtEff - tl.boilT) * invBand2).coerceIn(0f, 1f)
            if (tl.pixelatingIn) {
                val dt = boilPattern[i]
                var aIn = (0.5f + (tl.inT - dt) * invBandS).coerceIn(0f, 1f)
                val prox = 1 - abs(tl.inT - dt) * 6.25f
                if (prox > 0) aIn = (aIn + ((rnd - 0.5) * amp * 1.6 * prox).toFloat()).coerceIn(0f, 1f)
                if (aIn < a) a = aIn
            }
            if (tl.resolving) {
                val dt = boilPattern[i]
                var a2 = (0.5f + (dt - fadeT) * invBandS).coerceIn(0f, 1f)
                if (sAmp > 0.003f) {
                    val prox = 1 - abs(fadeT - dt) * 6.25f
                    if (prox > 0) a2 = (a2 + ((rnd - 0.5) * sAmp * 1.6 * prox).toFloat()).coerceIn(0f, 1f)
                }
                if (a2 < a) a = a2
            }
            // The mosaic source cross-fades from the outgoing image to the incoming one.
            if (colorsB != null) {
                val o = i * 4
                val k = i * 3
                val bt = tl.blendT
                val aq = ((a * 255 + 0.5f).toInt()) / 255f
                chunky[o] = channel((colorsA[k] + (colorsB[k] - colorsA[k]) * bt) * aq)
                chunky[o + 1] = channel((colorsA[k + 1] + (colorsB[k + 1] - colorsA[k + 1]) * bt) * aq)
                chunky[o + 2] = channel((colorsA[k + 2] + (colorsB[k + 2] - colorsA[k + 2]) * bt) * aq)
                chunky[o + 3] = channel(aq)
            } else {
                writeCell(chunky, i, colorsA, a)
            }
        }
    }

    private fun writeCell(out: ByteArray, i: Int, colors: FloatArray, a0: Float) {
        val a = ((a0 * 255 + 0.5f).toInt()) / 255f
        val o = i * 4
        val k = i * 3
        out[o] = channel(colors[k] * a)
        out[o + 1] = channel(colors[k + 1] * a)
        out[o + 2] = channel(colors[k + 2] * a)
        out[o + 3] = channel(a)
    }
}

/** `getMaskValue`: the geometric mask shapes on the 64² lattice ([r] row, [c] column). */
private fun geometricMask(shape: MaskShape, r: Int, c: Int): Float {
    val nx = c / (MaskSize - 1f)
    val ny = r / (MaskSize - 1f)
    val cx = nx - 0.5f
    val cy = ny - 0.5f
    return when (shape) {
        MaskShape.RadialCenter -> 1 - sqrt(cx * cx + cy * cy) * 2
        MaskShape.RadialCorner -> 1 - sqrt(nx * nx + ny * ny) / 1.414f
        MaskShape.LinearTop -> 1 - ny
        MaskShape.LinearBottom -> ny
        MaskShape.LinearLeft -> 1 - nx
        MaskShape.LinearRight -> nx
        MaskShape.DiagonalTL -> 1 - (nx + ny) / 2
        MaskShape.DiagonalBR -> (nx + ny) / 2
        MaskShape.Diamond -> 1 - (abs(cx) + abs(cy))
        MaskShape.BlindsH -> 1 - ((ny * 8) % 1)
        MaskShape.BlindsV -> 1 - ((nx * 8) % 1)
        else -> -1f
    }
}
