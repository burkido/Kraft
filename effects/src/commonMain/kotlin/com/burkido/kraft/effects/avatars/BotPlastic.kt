package com.burkido.kraft.effects.avatars

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.burkido.kraft.effects.core.PixelSurface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The plastic material, per frame (`plastic.ts`): the light and the view rotated into the cap's
 * own frame; a small matcap (a lit sphere — one colour per normal) evaluated with the full
 * material only when either has moved a bin, cross-faded over the frames the last one lasted;
 * the cap's texels as a bilinear lookup into it times the baked occlusion; and the side slices'
 * sweep gradients sampled from the same matcap, so sides and cap agree.
 */

private const val MM = McSize * McSize
private const val ConicStops = 24

/** Light elevation off the screen plane. */
private val El = 48 * PI / 180
private val EXy = cos(El)
private val EZ = sin(El)

/** The material's knobs (`Material`). */
internal class BotMaterial(val shadow: Double, val highlight: Double, val spread: Double, val rim: Double)

/** Light, view, half-vector, sky, window axes — in the cap's frame (`Frame`). */
internal class CapFrame(
    val l: DoubleArray, val v: DoubleArray, val h: DoubleArray, val u: DoubleArray,
    val w: DoubleArray, val a: DoubleArray, val b: DoubleArray,
)

/** The rig as the material needs it (`Rig`). */
internal class PlasticRig(
    /** The (floored) cos/sin of yaw and pitch, as the slice affines use them. */
    val cy: Double, val sy: Double, val cp: Double, val sp: Double,
    /** cos(yaw)·cos(pitch), unfloored: the front cap faces the viewer while positive. */
    val facing: Double,
    val roll: Double,
    val halfDepth: Double,
    val cap: Double,
    /** Unit vector toward the light on screen. */
    val lx: Double, val ly: Double,
    /** The avatar box in device pixels. */
    val dev: Float,
)

private fun norm3(x: Double, y: Double, z: Double): DoubleArray {
    val l = sqrt(x * x + y * y + z * z).takeIf { it != 0.0 } ?: 1.0
    return doubleArrayOf(x / l, y / l, z / l)
}

/** `capFrame`: screen-space vectors into the cap's frame — un-roll, then the rig's rotation transposed. */
internal fun capFrame(r: PlasticRig): CapFrame {
    val cr = cos(r.roll)
    val sr = sin(r.roll)
    val lx = cr * r.lx + sr * r.ly
    val ly = -sr * r.lx + cr * r.ly
    val mirror = if (r.facing < 0) -1.0 else 1.0
    // The flip passes through edge-on: fade the mirrored component so the light doesn't pop.
    val zf = mirror * min(1.0, abs(r.facing) / 0.16)
    val cy = r.cy
    val sy = r.sy
    val cp = r.cp
    val sp = r.sp
    fun local(x: Double, y: Double, z: Double, zk: Double = zf) =
        norm3(cy * x + sy * sp * y - sy * cp * z, cp * y + sp * z, zk * (sy * x - cy * sp * y + cy * cp * z))
    val l = local(EXy * lx, EXy * ly, EZ)
    val v = local(0.0, 0.0, 1.0, mirror)
    val h = norm3(l[0] + v[0], l[1] + v[1], l[2] + v[2])
    val u = local(lx, ly, 0.0, mirror)
    // The window: 80° round from the light, 34° off the view axis.
    val cw = cos(80 * PI / 180)
    val sw = sin(80 * PI / 180)
    val wx = cw * lx - sw * ly
    val wy = sw * lx + cw * ly
    val ws = norm3(0.55 * wx, 0.55 * wy, 0.83)
    val asv = norm3(ws[1], -ws[0], 0.0)
    val bs = doubleArrayOf(ws[1] * asv[2] - ws[2] * asv[1], ws[2] * asv[0] - ws[0] * asv[2], ws[0] * asv[1] - ws[1] * asv[0])
    return CapFrame(
        l, v, h, u,
        local(ws[0], ws[1], ws[2], mirror), local(asv[0], asv[1], asv[2], mirror), local(bs[0], bs[1], bs[2], mirror),
    )
}

// Tone map (soft shoulder above 0.75, keeps hue) + sRGB encode, as a table.
private const val ToneN = 2048
private const val ToneMax = 2.5
private const val ToneScale = ToneN / ToneMax
private val toneLut = FloatArray(ToneN) { i ->
    val v = (i + 0.5) / ToneScale
    val y = if (v <= 0.75) v else 0.75 + 0.25 * (1 - exp(-(v - 0.75) / 0.25))
    (255 * if (y <= 0.0031308) 12.92 * y else 1.055 * y.pow(1 / 2.4) - 0.055).toFloat()
}

private fun tone(v: Double): Float = toneLut[if (v <= 0) 0 else if (v >= ToneMax) ToneN - 1 else (v * ToneScale).toInt()]

// x^e over [0, 1], as a table, cached by exponent.
private const val PowN = 1024
private val powLuts = HashMap<Int, FloatArray>()

private fun powLut(e: Int): FloatArray = powLuts.getOrPut(e) {
    if (powLuts.size > 16) powLuts.clear()
    FloatArray(PowN + 1) { i -> (i.toDouble() / PowN).pow(e).toFloat() }
}

private val Env = doubleArrayOf(0.92, 0.96, 1.0)
private val Warm = doubleArrayOf(1.0, 0.98, 0.95)

private fun smooth(a: Double, b: Double, v: Double): Double {
    val t = if (v <= a) 0.0 else if (v >= b) 1.0 else (v - a) / (b - a)
    return t * t * (3 - 2 * t)
}

/* 1 inside |x| < w, falling to 0 over ±s around it. */
private fun soft(w: Double, s: Double, x: Double) = 1 - smooth(w - s, w + s, x)

/** `buildMatcap`: fills [out] (M × M × rgb, sRGB 0..255) with the lit sphere for linear colour [c]. */
internal fun buildMatcap(out: FloatArray, c: DoubleArray, f: CapFrame, p: BotMaterial) {
    val l = f.l
    val v = f.v
    val h = f.h
    val u = f.u
    val w = f.w
    val a = f.a
    val b = f.b
    val mx = max(max(c[0], c[1]), max(c[2], 0.05))
    val tint0 = c[0] / mx
    val tint1 = c[1] / mx
    val tint2 = c[2] / mx
    val amb = max(0.03, 0.30 - 0.15 * p.shadow)
    val wrap = 0.15 + 0.14 * p.spread
    val kd = 0.85
    val e1 = min(90, (110 / p.spread.pow(1.3)).roundToInt())
    val e2 = max(2, (8 / p.spread).roundToInt())
    val lut1 = powLut(e1)
    val lut2 = powLut(e2)
    val ks1 = 0.45 * p.highlight
    val ks2 = 0.10 * p.highlight
    val winK = 0.11 * p.highlight
    val rimK = 0.30 * p.rim
    val ambT0 = amb * tint0
    val ambT1 = amb * tint1
    val ambT2 = amb * tint2
    for (j in 0 until McSize) {
        for (i in 0 until McSize) {
            var nx = i.toDouble() / (McSize - 1) * 2 - 1
            var ny = j.toDouble() / (McSize - 1) * 2 - 1
            var r2 = nx * nx + ny * ny
            // Samples lie inside the unit disc and read one cell beyond it at most.
            if (r2 > 1.14) continue
            if (r2 > 1) {
                val s = 1 / sqrt(r2)
                nx *= s; ny *= s; r2 = 1.0
            }
            val nz = sqrt(1 - r2)
            val nl = nx * l[0] + ny * l[1] + nz * l[2]
            val nv = max(0.0, nx * v[0] + ny * v[1] + nz * v[2])
            val nh = max(0.0, nx * h[0] + ny * h[1] + nz * h[2])
            val dif = ((nl + wrap) / (1 + wrap)).coerceIn(0.0, 1.0)
            val q = 1 - nv
            val q2 = q * q
            val f3 = q2 * q
            val f5 = f3 * q2
            val ni = (nh * PowN).toInt()
            val spec = (ks1 * lut1[ni] + ks2 * lut2[ni]) * (1 + 3 * f5)
            // The mirror direction: the sky/floor gradient and the window.
            val rx = 2 * nv * nx - v[0]
            val ry = 2 * nv * ny - v[1]
            val rz = 2 * nv * nz - v[2]
            val sky = 0.45 + 0.55 * smooth(-0.4, 0.6, rx * u[0] + ry * u[1] + rz * u[2])
            val rw = rx * w[0] + ry * w[1] + rz * w[2]
            var win = 0.0
            if (rw > 0.5) {
                val ra = (rx * a[0] + ry * a[1] + rz * a[2]) / rw
                val rb = (rx * b[0] + ry * b[1] + rz * b[2]) / rw
                win = soft(0.34, 0.12, abs(ra)) * soft(0.12, 0.06, abs(rb))
            }
            val env = rimK * f3 * sky + winK * win
            val k = (j * McSize + i) * 3
            out[k] = tone(c[0] * (ambT0 + kd * dif) + spec * Warm[0] + env * Env[0])
            out[k + 1] = tone(c[1] * (ambT1 + kd * dif) + spec * Warm[1] + env * Env[1])
            out[k + 2] = tone(c[2] * (ambT2 + kd * dif) + spec * Warm[2] + env * Env[2])
        }
    }
}

/** Bilinear read of the matcap at a normal's (nx, ny). */
private fun sampleMatcap(mc: FloatArray, nx: Double, ny: Double, out: FloatArray) {
    val fx = (nx * 0.5 + 0.5) * (McSize - 1)
    val fy = (ny * 0.5 + 0.5) * (McSize - 1)
    val cx = min(McSize - 2, max(0, fx.toInt()))
    val cy = min(McSize - 2, max(0, fy.toInt()))
    val x = (fx - cx).toFloat()
    val y = (fy - cy).toFloat()
    val b = (cy * McSize + cx) * 3
    val r = McSize * 3
    val w00 = (1 - x) * (1 - y)
    val w10 = x * (1 - y)
    val w01 = (1 - x) * y
    val w11 = x * y
    for (ch in 0 until 3) out[ch] = mc[b + ch] * w00 + mc[b + 3 + ch] * w10 + mc[b + r + ch] * w01 + mc[b + r + 3 + ch] * w11
}

/** `shadeTexels`: matcap lookup × baked occlusion into RGBA premultiplied [px]. */
internal fun shadeTexels(form: BotForm, mc: FloatArray, px: ByteArray, aoMul: FloatArray) {
    val n = form.n
    val r = McSize * 3
    var k = 0
    for (i in 0 until n * n) {
        val a = form.ao[i].toInt() and 0xFF
        if (a == 0) {
            px[k] = 0; px[k + 1] = 0; px[k + 2] = 0; px[k + 3] = 0
            k += 4
            continue
        }
        val m = aoMul[a]
        val b = (form.i00[i].toInt() and 0xFFFF) * 3
        val x = (form.wx[i].toInt() and 0xFF) * (1f / 255)
        val y = (form.wy[i].toInt() and 0xFF) * (1f / 255)
        val w00 = (1 - x) * (1 - y) * m
        val w10 = x * (1 - y) * m
        val w01 = (1 - x) * y * m
        val w11 = x * y * m
        px[k] = byteOf(mc[b] * w00 + mc[b + 3] * w10 + mc[b + r] * w01 + mc[b + r + 3] * w11)
        px[k + 1] = byteOf(mc[b + 1] * w00 + mc[b + 4] * w10 + mc[b + r + 1] * w01 + mc[b + r + 4] * w11)
        px[k + 2] = byteOf(mc[b + 2] * w00 + mc[b + 5] * w10 + mc[b + r + 2] * w01 + mc[b + r + 5] * w11)
        px[k + 3] = 255.toByte()
        k += 4
    }
}

/** `Uint8ClampedArray` assignment: clamp and round half to even — rounding to nearest is close enough. */
private fun byteOf(v: Float): Byte = (v.coerceIn(0f, 255f) + 0.5f).toInt().coerceAtMost(255).toByte()

private fun toLin(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

/* The matcap is rebuilt once a direction has moved a bin from the one it was built for. */
private const val Bin = 1.0 / 48

private fun moved(a: DoubleArray, b: DoubleArray?): Boolean =
    b == null || abs(a[0] - b[0]) >= Bin || abs(a[1] - b[1]) >= Bin || abs(a[2] - b[2]) >= Bin

/**
 * One avatar's plastic state for one outline (`State`): the matcap it shows, the fade between the
 * last one and the new, the occlusion curve, the texels and the side brushes.
 */
internal class PlasticState {
    private val mc = FloatArray(MM * 3)
    private val mcPrev = FloatArray(MM * 3)
    private val mcMix = FloatArray(MM * 3)
    private var mixVersion = 0
    private var blendT = 1.0
    private var blendFrames = 1
    private var sinceBuild = 0
    private var lastL: DoubleArray? = null
    private var lastV: DoubleArray? = null
    private var lastLx = Double.NaN
    private var lastLy = Double.NaN
    private var lastBase: Color? = null
    private var lastMat: BotMaterial? = null
    private var version = 0
    private var imgVersion = -1
    private var imgAoK = Double.NaN
    private var imgForm: BotForm? = null
    private var aoK = -1.0
    private val aoMul = FloatArray(256)
    private var pixels = ByteArray(0)

    /** The texels as a bitmap (N × N). */
    var texels: PixelSurface? = null
        private set

    /** The side brushes: the near shoulder (57° tilt), the rim, the far half in shade. */
    var near: Brush? = null
        private set
    var rimBrush: Brush? = null
        private set
    var far: Brush? = null
        private set

    /** The in-plane light direction the cap's hairline follows. */
    var lxy = floatArrayOf(0f, -1f)
        private set

    private fun sameMat(a: BotMaterial?, b: BotMaterial) =
        a != null && a.shadow == b.shadow && a.highlight == b.highlight && a.spread == b.spread && a.rim == b.rim

    /** Steps the material for this frame; the cap's texels and the side brushes are then current. */
    fun update(form: BotForm, rig: PlasticRig, base: Color, mat: BotMaterial) {
        val f = capFrame(rig)
        val ll = hypot(f.l[0], f.l[1])
        lxy = if (ll < 0.05) floatArrayOf(0f, -1f) else floatArrayOf((f.l[0] / ll).toFloat(), (f.l[1] / ll).toFloat())
        if (moved(f.l, lastL) || moved(f.v, lastV) || rig.lx != lastLx || rig.ly != lastLy || base != lastBase || !sameMat(lastMat, mat)) {
            // The fade starts from what is showing now, so a rebuild during a fade does not jump.
            if (version > 0) mcMix.copyInto(mcPrev)
            buildMatcap(mc, doubleArrayOf(toLin(base.red), toLin(base.green), toLin(base.blue)), f, mat)
            if (version == 0) {
                mc.copyInto(mcMix)
                blendT = 1.0
            } else {
                blendFrames = min(10, max(1, sinceBuild))
                blendT = 0.0
            }
            sinceBuild = 0
            mixVersion++
            lastL = f.l
            lastV = f.v
            lastLx = rig.lx
            lastLy = rig.ly
            lastBase = base
            lastMat = mat
            version++
            near = null
            rimBrush = null
            far = null
        }
        sinceBuild++
        if (blendT < 1) {
            blendT = min(1.0, blendT + 1.0 / blendFrames)
            val e = (if (blendT >= 1) 1.0 else blendT * blendT * (3 - 2 * blendT)).toFloat()
            for (i in 0 until MM * 3) mcMix[i] = mcPrev[i] + (mc[i] - mcPrev[i]) * e
            mixVersion++
        }
        // The occlusion strength follows `shadow`.
        val k = min(1.3, 1.2 * mat.shadow)
        if (k != aoK) {
            for (a in 0 until 256) aoMul[a] = max(0.0, 1 - k * (1 - a / 255.0)).toFloat()
            aoK = k
        }
        val n = form.n
        if (texels?.width != n) {
            texels = PixelSurface(n, n)
            pixels = ByteArray(n * n * 4)
            imgVersion = -1
        }
        if (imgVersion != mixVersion || imgAoK != aoK || imgForm !== form) {
            shadeTexels(form, mcMix, pixels, aoMul)
            texels!!.commit(pixels)
            imgVersion = mixVersion
            imgAoK = aoK
            imgForm = form
        }
        if (near == null) {
            near = sideBrush(0.55, 0.0)
            rimBrush = sideBrush(0.0, 0.0)
            far = sideBrush(0.0, min(0.6, 0.25 * mat.shadow))
        }
    }

    /** `sideGradient`: the matcap's ring at tilt [nz] as a sweep round the outline's centre. */
    private fun sideBrush(nz: Double, dark: Double): Brush {
        val rr = sqrt(1 - nz * nz)
        val c = FloatArray(3)
        val k = 1 - dark
        val stops = Array(ConicStops + 1) { s ->
            val phi = s.toDouble() / ConicStops * PI * 2
            sampleMatcap(mc, rr * cos(phi), rr * sin(phi), c)
            // `rgb(… | 0)`: truncated.
            (s.toFloat() / ConicStops) to Color((c[0] * k).toInt(), (c[1] * k).toInt(), (c[2] * k).toInt())
        }
        return Brush.sweepGradient(*stops, center = Offset(50f, 50f))
    }
}
