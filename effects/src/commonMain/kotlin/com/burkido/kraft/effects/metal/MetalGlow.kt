package com.burkido.kraft.effects.metal

import com.burkido.kraft.effects.core.PlatformPixelImage
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/** `GLOW_DEFAULTS`: the halo and catch-light as metal-fx v2 ships them. */
internal object GlowConfig {
    const val HaloOpMul = 2.0f
    const val ExtraIntensity = 3.51f
    const val PeakOp = 0.85f
    const val BaseOp = 0.34f
    const val Inset = 1.5f
    const val ExtraOutward = 1.0f
    const val WanderRange = 15f
    const val WanderLerp = 0.0075f
    const val FadeRate = 0.00875f
    const val LumLo = 0.08f
    const val LumHi = 0.32f
    const val MinDwellMs = 1500.0
    const val RelocFadeMs = 300.0
    const val RelocFadeOutMs = 450.0
    const val HaloHalfLen = 7.8f
    const val ExtraHalfLen = 9.13952f / 3
    val Halo = listOf(Layer(26.4f, 8.4f, 0.385f), Layer(15.6f, 4.8f, 0.595f), Layer(7.2f, 2.1f, 0.70f), Layer(3.0f, 0.9f, 0.70f))
    val Extra = listOf(Layer(4.0f / 3, 2.0f / 3, 0.85f), Layer(2.0f / 3, 1.35f / 3, 1f))
    const val ExtraFadeR = 13.0f / 3
    const val PerimSamples = 16

    class Layer(val stroke: Float, val blur: Float, val opacity: Float)
}

/** A white, alpha-only glow sprite: [w]×[h] dp, its stroke centred at ([ax], [ay]). */
internal class GlowSprite(val image: PlatformPixelImage, val w: Float, val h: Float, val ax: Float, val ay: Float)

/**
 * `bake.ts`: the halo (four blurred strokes) and the catch-light (two tight strokes with a radial
 * end fade) rasterised once into alpha sprites — a round-capped segment's exact coverage, then a
 * three-pass box blur standing in for the gaussian — and cached by everything that shapes them.
 */
internal object GlowSprites {
    private val cache = HashMap<String, GlowSprite>()

    fun halo(halfLen: Float, density: Float): GlowSprite =
        cache.getOrPut("h|${(halfLen * 100).roundToInt()}|$density") { compose(GlowConfig.Halo, halfLen, density, 0f) }

    fun extra(halfLen: Float, density: Float): GlowSprite =
        cache.getOrPut("e|${(halfLen * 100).roundToInt()}|$density") { compose(GlowConfig.Extra, halfLen, density, GlowConfig.ExtraFadeR) }

    private fun compose(layers: List<GlowConfig.Layer>, halfLen: Float, dpr: Float, fade: Float): GlowSprite {
        var padMax = 0f
        for (l in layers) padMax = max(padMax, l.stroke / 2 + 3 * l.blur)
        val pad = ceil(padMax) + 1
        val cw = 2 * halfLen + 2 * pad
        val ch = 2 * pad
        val w = ceil(cw * dpr).toInt()
        val h = ceil(ch * dpr).toInt()
        val acc = FloatArray(w * h)
        val cx = cw / 2 * dpr
        val cy = pad * dpr
        for (l in layers) {
            var a = capsule(w, h, cx - halfLen * dpr, cx + halfLen * dpr, cy, l.stroke / 2 * dpr)
            a = gaussBlur(a, w, h, l.blur * dpr)
            for (i in acc.indices) {
                val la = a[i] * l.opacity
                acc[i] = acc[i] + la * (1 - acc[i])
            }
        }
        if (fade > 0f) {
            // The SVG luminance mask: opaque to 30 %, 25 % at 65 %, gone at the radius.
            val r = fade * dpr
            for (y in 0 until h) for (x in 0 until w) {
                val t = hypot(x + 0.5f - cx, y + 0.5f - cy) / r
                val m = when {
                    t <= 0.3f -> 1f
                    t <= 0.65f -> 1 - (t - 0.3f) / 0.35f * 0.75f
                    t < 1f -> 0.25f * (1 - (t - 0.65f) / 0.35f)
                    else -> 0f
                }
                acc[y * w + x] *= m
            }
        }
        val px = ByteArray(w * h * 4)
        for (i in acc.indices) {
            val a = (min(1f, acc[i]) * 255 + 0.5f).toInt().toByte()
            px[i * 4] = a; px[i * 4 + 1] = a; px[i * 4 + 2] = a; px[i * 4 + 3] = a
        }
        val image = PlatformPixelImage(w, h)
        image.upload(px)
        return GlowSprite(image, cw, ch, cw / 2, pad)
    }

    /** Coverage of a horizontal round-capped segment of half-width [r] (px), one-pixel AA. */
    private fun capsule(w: Int, h: Int, x0: Float, x1: Float, y0: Float, r: Float): FloatArray {
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            val py = y + 0.5f
            for (x in 0 until w) {
                val px = x + 0.5f
                val qx = px.coerceIn(x0, x1)
                val d = hypot(px - qx, py - y0)
                out[y * w + x] = (r - d + 0.5f).coerceIn(0f, 1f)
            }
        }
        return out
    }
}

/** Box sizes for [n] passes approximating a gaussian of [sigma] (Kutskir). */
private fun boxesForGauss(sigma: Float, n: Int): IntArray {
    val wIdeal = sqrt(12 * sigma * sigma / n + 1)
    var wl = floor(wIdeal).toInt()
    if (wl % 2 == 0) wl--
    val wu = wl + 2
    val mIdeal = (12 * sigma * sigma - n * wl * wl - 4 * n * wl - 3 * n) / (-4f * wl - 4)
    val m = mIdeal.roundToInt()
    return IntArray(n) { if (it < m) wl else wu }
}

/** Three box passes on an alpha field, edges clamped (`gaussBlur`). */
internal fun gaussBlur(a: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
    if (sigma <= 0.05f) return a
    val tmp = FloatArray(a.size)
    for (box in boxesForGauss(sigma, 3)) {
        val r = (box - 1) / 2
        boxBlurH(a, tmp, w, h, r)
        boxBlurV(tmp, a, w, h, r)
    }
    return a
}

private fun boxBlurH(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
    val inv = 1f / (r + r + 1)
    for (y in 0 until h) {
        val row = y * w
        var acc = 0f
        for (x in -r..r) acc += src[row + x.coerceIn(0, w - 1)]
        for (x in 0 until w) {
            dst[row + x] = acc * inv
            acc += src[row + min(w - 1, x + r + 1)] - src[row + max(0, x - r)]
        }
    }
}

private fun boxBlurV(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int) {
    val inv = 1f / (r + r + 1)
    for (x in 0 until w) {
        var acc = 0f
        for (y in -r..r) acc += src[y.coerceIn(0, h - 1) * w + x]
        for (y in 0 until h) {
            dst[y * w + x] = acc * inv
            acc += src[min(h - 1, y + r + 1) * w + x] - src[max(0, y - r) * w + x]
        }
    }
}

/** What one glow tick produces. */
internal class GlowFrame {
    var x = 0f; var y = 0f
    var ex = 0f; var ey = 0f
    var tangent = 0f
    var haloOp = 0f
    var extraOp = 0f
    var env = 0f
    val tint = floatArrayOf(1f, 1f, 1f)
}

/**
 * The wandering halo (`glow.ts`): it hunts the brightest of 16 perimeter samples, dwells at least
 * 1.5 s, and moves by fading out in place (450 ms) and in at the new spot (300 ms) — never
 * sliding — while wandering a little along the edge and holding each tint 2 s.
 */
internal class GlowState(private val random: Random = Random(7)) {
    private var perimX = FloatArray(0)
    private var perimY = FloatArray(0)
    private var perimArc = FloatArray(0)
    private var w = 0f; private var h = 0f; private var r = 0f
    private var kind = ShapeKind.Pill

    private var currentIdx = 0
    private var glowOpacity = 0f
    private var appearedAt = 0.0
    private var relocNextIdx = -1
    private var tweenFrom = 0f; private var tweenTo = 0f; private var tweenDur = 0.0; private var tweenStart = 0.0
    private var tweenActive = false; private var tweenDone = false; private var tweenVal = 0f
    private var envClock = 0.0
    private var lastTickMs = 0.0
    private var wanderS = 0f; private var wanderTargetS = 0f; private var wanderMs = 0.0
    private val tintFrom = floatArrayOf(255f, 255f, 255f)
    private val tintTarget = floatArrayOf(255f, 255f, 255f)
    private var tintStart = -1.0
    private var tintHoldUntil = 0.0
    private val pt = FloatArray(2)
    private val sample = FloatArray(3)

    fun configure(w: Float, h: Float, r: Float, kind: ShapeKind) {
        if (this.w == w && this.h == h && this.r == r && this.kind == kind && perimX.isNotEmpty()) return
        this.w = w; this.h = h; this.r = r; this.kind = kind
        val n = GlowConfig.PerimSamples
        perimX = FloatArray(n); perimY = FloatArray(n); perimArc = FloatArray(n)
        val total = shapePerim(w, h, r, kind)
        for (i in 0 until n) {
            val arc = total * i / n
            sampleAtArc(arc, w, h, r, GlowConfig.Inset, 0f, kind, pt)
            perimX[i] = pt[0]; perimY[i] = pt[1]; perimArc[i] = arc
        }
        if (currentIdx >= n) currentIdx = 0
    }

    private fun startTween(from: Float, to: Float, dur: Double) {
        tweenFrom = from; tweenTo = to; tweenDur = dur; tweenStart = envClock
        tweenActive = true; tweenDone = false; tweenVal = from
    }

    private fun tickTween(): Float {
        if (!tweenActive) return 1f
        val t = ((envClock - tweenStart) / max(1.0, tweenDur)).coerceIn(0.0, 1.0).toFloat()
        val e = t * t * (3 - 2 * t)
        tweenVal = tweenFrom + (tweenTo - tweenFrom) * e
        tweenDone = t >= 1f
        return tweenVal
    }

    /**
     * Steps the machine at [nowMs] against the sheet [samples] seen through [window]. Returns
     * false before the table exists.
     */
    fun tick(
        nowMs: Double,
        samples: ByteArray,
        sheetSize: Int,
        window: SheetWindow,
        strength: Float,
        deform: Deform?,
        out: GlowFrame,
    ): Boolean {
        val n = perimX.size
        if (n == 0) return false
        val dtMs = if (lastTickMs > 0) (nowMs - lastTickMs).coerceIn(0.5, 200.0) else RateTickMs
        lastTickMs = nowMs
        envClock += min(dtMs, EnvMaxStepMs)
        fun rate(perTick: Float): Float = 1 - (1 - perTick).pow((dtMs / RateTickMs).toFloat())

        var maxLum = -1f
        var maxIdx = currentIdx
        var curLum = 0f
        for (i in 0 until n) {
            val lum = sampleLum(samples, sheetSize, window, perimX[i], perimY[i])
            if (lum > maxLum) { maxLum = lum; maxIdx = i }
            if (i == currentIdx) curLum = lum
        }
        val dwellActive = appearedAt > 0 && nowMs - appearedAt < GlowConfig.MinDwellMs
        val targetOp = GlowConfig.BaseOp + (GlowConfig.PeakOp - GlowConfig.BaseOp) * smoothRange(GlowConfig.LumLo, GlowConfig.LumHi, curLum)
        val rivalDominates = !dwellActive && maxLum - curLum > RelocateDelta

        fun fadeIn() {
            appearedAt = nowMs
            wanderS = 0f; wanderTargetS = 0f; wanderMs = 0.0
            startTween(0f, 1f, GlowConfig.RelocFadeMs)
        }
        if (tweenActive && tweenDone && tweenTo == 0f) {
            currentIdx = relocNextIdx
            val nl = sampleLum(samples, sheetSize, window, perimX[currentIdx], perimY[currentIdx])
            glowOpacity = GlowConfig.BaseOp + (GlowConfig.PeakOp - GlowConfig.BaseOp) * smoothRange(GlowConfig.LumLo, GlowConfig.LumHi, nl)
            fadeIn()
        }
        if (!tweenActive || tweenDone) {
            if (appearedAt == 0.0) {
                currentIdx = maxIdx
                glowOpacity = targetOp
                fadeIn()
            } else if (rivalDominates) {
                relocNextIdx = maxIdx
                startTween(1f, 0f, GlowConfig.RelocFadeOutMs)
            }
        }
        glowOpacity += (targetOp - glowOpacity) * rate(GlowConfig.FadeRate)
        glowOpacity = glowOpacity.coerceIn(0f, 1f)
        val env = tickTween()

        val ratio = shapePerim(w, h, r, kind) / rrPerim(CanonicalW, CanonicalH, 20f)
        wanderMs += dtMs
        if (wanderMs >= WanderRetargetMs) {
            wanderTargetS = (random.nextFloat() * 2 - 1) * GlowConfig.WanderRange * ratio
            wanderMs = 0.0
        }
        wanderS += (wanderTargetS - wanderS) * rate(GlowConfig.WanderLerp)

        val arc = perimArc[currentIdx] + wanderS
        sampleAtArc(arc, w, h, r, GlowConfig.Inset, 0f, kind, pt)
        var bx = pt[0]; var by = pt[1]
        val tangent = tangentAngleAtArc(arc, w, h, r, GlowConfig.Inset, kind)
        sampleAtArc(arc, w, h, r, GlowConfig.Inset, GlowConfig.ExtraOutward * ratio, kind, pt)
        var ex = pt[0]; var ey = pt[1]
        if (deform != null) {
            deform.apply(bx, by, pt); bx = pt[0]; by = pt[1]
            deform.apply(ex, ey, pt); ex = pt[0]; ey = pt[1]
        }

        // Tint: the colour under the hotspot, held 2 s and crossfaded over 400 ms (dark theme).
        sampleRgb(samples, sheetSize, window, bx, by, sample)
        if (tintStart < 0) {
            sample.copyInto(tintFrom); sample.copyInto(tintTarget)
            tintStart = nowMs
            tintHoldUntil = nowMs + TintHoldMs
        } else if (nowMs - tintStart >= TintFadeMs && nowMs >= tintHoldUntil) {
            tintTarget.copyInto(tintFrom); sample.copyInto(tintTarget)
            tintStart = nowMs
            tintHoldUntil = nowMs + TintHoldMs
        }
        val ft = ((nowMs - tintStart) / TintFadeMs).coerceIn(0.0, 1.0).toFloat()
        val hr = tintFrom[0] + (tintTarget[0] - tintFrom[0]) * ft
        val hg = tintFrom[1] + (tintTarget[1] - tintFrom[1]) * ft
        val hb = tintFrom[2] + (tintTarget[2] - tintFrom[2]) * ft
        val peak = max(hr, max(hg, hb)).takeIf { it > 0f } ?: 1f
        out.tint[0] = (255 * (hr / peak)).roundToInt() / 255f
        out.tint[1] = (255 * (hg / peak)).roundToInt() / 255f
        out.tint[2] = (255 * (hb / peak)).roundToInt() / 255f

        val m = strength.coerceIn(0f, 1f)
        out.x = bx; out.y = by; out.ex = ex; out.ey = ey; out.tangent = tangent
        out.haloOp = min(1f, glowOpacity * GlowConfig.HaloOpMul * m)
        out.extraOp = min(1f, glowOpacity * GlowConfig.ExtraIntensity * m)
        out.env = env
        return true
    }

    private companion object {
        const val RelocateDelta = 0.05f
        const val RateTickMs = 1000.0 / 15
        const val WanderRetargetMs = 120 * (1000.0 / 15)
        const val TintHoldMs = 2000.0
        const val TintFadeMs = 400.0
        const val EnvMaxStepMs = 34.0
    }
}
