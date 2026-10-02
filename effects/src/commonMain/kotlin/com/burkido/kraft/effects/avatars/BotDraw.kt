package com.burkido.kraft.effects.avatars

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The renderer (`draw.ts`). The body is a stack of copies of its outline along a depth axis with
 * a pillow profile, each projected with the head's yaw and pitch, so the stack reads as a rounded
 * extruded solid that turns and flips; in plastic the side copies take gradients from the
 * material's matcap and the front cap is its lit texture. The face lives on the front cap.
 */

/** The canvas is drawn larger than the avatar's box, so a hop or a flip is never clipped. */
internal const val Overscan = 1.5f

/** The body's centre sits this fraction of the box below the canvas centre. */
internal const val Rise = 0.1f

private const val Slices = 17
private const val HalfDepth = 15.0
private const val CapScale = 0.9
private fun profile(z: Double, cap: Double) = cap + (1 - cap) * sqrt(max(0.0, 1 - z * z))

private const val EyeGap = 25.0
private const val EyeRx = 6.3
private const val FaceR = 30.0
private const val EyeSteps = 8
private val MouthSin = sin(0.684)
private val MouthCos = cos(0.684)

private const val WhirlSegments = 34
private const val WhirlSpan = PI * 1.55
private const val WhirlRx = 57.0
private const val WhirlRatio = 0.4
private const val WhirlTilt = -0.28

/** The whirl round a spin (`whirl*` props): strength 0–2 (off by default) and its ring's shape. */
data class BotWhirl(
    val strength: Double = 0.0,
    val size: Double = 1.0,
    val width: Double = 1.0,
    val length: Double = 1.0,
    val tilt: Double = 1.0,
)

/** Everything one frame needs (`DrawConfig`). */
internal class BotDrawConfig(
    val type: BotAvatarType,
    val face: BotAvatarFace,
    val faceX: Double,
    val faceY: Double,
    val faceScale: Double,
    /** The body colour, and the same as the HSL numbers the web's shades are taken from. */
    val color: Color,
    val hsl: Hsl,
    val ink: Color,
    val shading: BotAvatarShading,
    val shadow: Double,
    val highlight: Double,
    val depth: Double,
    val light: Double,
    val rim: Double,
    val spread: Double,
    val whirl: BotWhirl,
    /** No loop follows (paused, reduced motion): bake the plastic form now. */
    val still: Boolean,
)

/** A canvas affine (a, b, c, d, e, f): x' = a·x + c·y + e, y' = b·x + d·y + f. */
private fun mul(p: DoubleArray, q: DoubleArray) = doubleArrayOf(
    p[0] * q[0] + p[2] * q[1], p[1] * q[0] + p[3] * q[1],
    p[0] * q[2] + p[2] * q[3], p[1] * q[2] + p[3] * q[3],
    p[0] * q[4] + p[2] * q[5] + p[4], p[1] * q[4] + p[3] * q[5] + p[5],
)

private fun inverse(p: DoubleArray): DoubleArray {
    val det = p[0] * p[3] - p[1] * p[2]
    val a = p[3] / det
    val b = -p[1] / det
    val c = -p[2] / det
    val d = p[0] / det
    return doubleArrayOf(a, b, c, d, -(a * p[4] + c * p[5]), -(b * p[4] + d * p[5]))
}

private class BotPalette(
    val base: Color,
    val near: Color,
    val light: Color,
    val dark: Color,
    val capTop: Color,
    val capBottom: Color,
    /** The slice colours by draw order (far → near): crisp (null past 0.6) and smooth. */
    val crispMix: Array<Color?>,
    val smoothMix: Array<Color>,
)

private class WhirlInk(val base: Hsl, val light: Hsl, val dark: Hsl, val halo: Hsl)

/** Palettes, whirl inks and face paths, shared by every avatar (main thread). */
private object BotCaches {
    val palettes = HashMap<String, BotPalette>()
    val whirlInks = HashMap<Color, WhirlInk>()
    val eyePaths = HashMap<Long, Path>()
    val mouthPaths = HashMap<String, Path>()
    val bodies = HashMap<BotAvatarType, Path>()
    val parts = HashMap<BotAvatarType, Path?>()
}

internal fun bodyPath(type: BotAvatarType): Path = BotCaches.bodies.getOrPut(type) { parsePath(shapePath(type)) }
internal fun partsPath(type: BotAvatarType): Path? = BotCaches.parts.getOrPut(type) { shapeParts(type)?.let(::parsePath) }

private fun palette(cfg: BotDrawConfig, shadow: Double, highlight: Double): BotPalette {
    val key = "${cfg.color.value}|$shadow|$highlight"
    BotCaches.palettes[key]?.let { return it }
    val far = shade(cfg.hsl, -0.3 * shadow, 0.05 * shadow)
    val near = shade(cfg.hsl, -0.12 * shadow, 0.03 * shadow)
    val crisp = arrayOfNulls<Color>(Slices)
    val smooth = Array(Slices) { cfg.color }
    for (j in 0 until Slices) {
        val t = j.toDouble() / (Slices - 1)
        crisp[j] = if (t > 0.6) null else far.mix(near, t / 0.6).rounded().toColor()
        smooth[j] = if (t >= 0.5) cfg.color else far.mix(cfg.hsl, t / 0.5).rounded().toColor()
    }
    val p = BotPalette(
        base = cfg.color,
        near = near.toColor(),
        light = shade(cfg.hsl, 0.04 * highlight).toColor(),
        dark = shade(cfg.hsl, -0.3 * shadow, 0.05 * shadow).toColor(),
        capTop = shade(cfg.hsl, 0.035 * highlight).toColor(),
        capBottom = shade(cfg.hsl, -0.035 * shadow).toColor(),
        crispMix = crisp,
        smoothMix = smooth,
    )
    if (BotCaches.palettes.size > 200) BotCaches.palettes.clear()
    BotCaches.palettes[key] = p
    return p
}

private fun whirlInk(cfg: BotDrawConfig): WhirlInk = BotCaches.whirlInks.getOrPut(cfg.color) {
    if (BotCaches.whirlInks.size > 200) BotCaches.whirlInks.clear()
    WhirlInk(shade(cfg.hsl, 0.1, 0.02), shade(cfg.hsl, 0.3, 0.04), shade(cfg.hsl, -0.22, 0.08), shade(cfg.hsl, 0.2))
}

/* An eye's curve as a short polyline (a quadratic from ±x0,y0 through 0,cy), cached by its numbers. */
private fun eyePath(x0: Double, y0: Double, cy: Double): Path {
    val qx = (x0 * 50).roundToInt()
    val qy = (y0 * 50).roundToInt()
    val qc = (cy * 50).roundToInt()
    val key = qx + 2000L * qy + 4_000_000L * qc
    return BotCaches.eyePaths.getOrPut(key) {
        if (BotCaches.eyePaths.size > 256) BotCaches.eyePaths.clear()
        val ax = qx / 50.0
        val ay = qy / 50.0
        val ac = qc / 50.0
        Path().apply {
            moveTo((-ax).toFloat(), ay.toFloat())
            for (i in 1..EyeSteps) {
                val t = i.toDouble() / EyeSteps
                val mt = 1 - t
                lineTo((mt * mt * -ax + t * t * ax).toFloat(), ((mt * mt + t * t) * ay + 2 * mt * t * ac).toFloat())
            }
        }
    }
}

/* The mouth: corners at ±hw, two cubic edges between them, round caps of radius t0. */
private fun mouthPath(hw0: Double, t00: Double, a0: Double, yt0: Double, ab0: Double, yb0: Double): Path {
    fun q(v: Double) = (v * 50).roundToInt() / 50.0
    val hw = q(hw0)
    val t0 = q(t00)
    val a = q(a0)
    val yt = q(yt0)
    val ab = q(ab0)
    val yb = q(yb0)
    val key = "$hw,$t0,$a,$yt,$ab,$yb"
    return BotCaches.mouthPaths.getOrPut(key) {
        if (BotCaches.mouthPaths.size > 256) BotCaches.mouthPaths.clear()
        val nx = t0 * MouthSin
        val ny = t0 * MouthCos
        val ltx = -hw + nx
        val lty = -ny
        val rtx = hw - nx
        val rty = -ny
        val lbx = -hw - nx
        val lby = ny
        val rbx = hw + nx
        val rby = ny
        val cx = 4.0 / 3 * t0 * MouthCos
        val cy = 4.0 / 3 * t0 * MouthSin
        fun f(v: Double) = v.toFloat()
        Path().apply {
            moveTo(f(ltx), f(lty))
            cubicTo(f(-hw + a * hw), f(yt - t0), f(hw - a * hw), f(yt - t0), f(rtx), f(rty))
            cubicTo(f(rtx + cx), f(rty - cy), f(rbx + cx), f(rby - cy), f(rbx), f(rby))
            cubicTo(f(hw - ab * hw), f(yb + t0), f(-hw + ab * hw), f(yb + t0), f(lbx), f(lby))
            cubicTo(f(lbx - cx), f(lby - cy), f(ltx - cx), f(lty - cy), f(ltx), f(lty))
            close()
        }
    }
}

/** One avatar's renderer: its plastic states and scratch space. */
internal class BotRenderer {
    private val bodyState = PlasticState()
    private val partsState = PlasticState()
    private val matrix = Matrix()

    // Strokes that need round joins go through their own paint: on Skiko, `Stroke(join = Round)`
    // in a DrawScope is not applied, and an open eye (a hairpin) came out as a flat-topped half-disc.
    private val roundStroke = Paint().apply {
        style = PaintingStyle.Stroke
        strokeCap = StrokeCap.Round
        strokeJoin = StrokeJoin.Round
        isAntiAlias = true
    }

    private inline fun DrawScope.withAffine(t: DoubleArray, block: DrawScope.() -> Unit) {
        matrix.reset()
        val v = matrix.values
        v[0] = t[0].toFloat(); v[1] = t[1].toFloat(); v[4] = t[2].toFloat(); v[5] = t[3].toFloat()
        v[12] = t[4].toFloat(); v[13] = t[5].toFloat()
        withTransform({ transform(matrix) }, block)
    }

    /**
     * Draws one frame. [box] is the avatar's layout size in dp; the scope is `box · Overscan`
     * square (at [dpr] px per dp) with the body's centre `Rise · box` below its middle. The
     * density comes in as a value: a recording scope must not be asked for its own.
     */
    fun draw(scope: DrawScope, box: Float, pose: BotPose, cfg: BotDrawConfig, dpr: Double) = with(scope) {
        val dev = box * min(2.0, dpr).toFloat()
        val full = box * Overscan.toDouble()
        val s = box / 100.0
        val shadow = cfg.shadow
        val highlight = cfg.highlight
        val halfDepth = HalfDepth * cfg.depth
        val cap = 1 - (1 - CapScale) * cfg.rim
        val spread = cfg.spread
        val la = cfg.light * PI / 180
        val lx = sin(la)
        val ly = -cos(la)
        val pal = palette(cfg, shadow, highlight)

        val cy0 = cos(pose.yaw)
        val sy = sin(pose.yaw)
        val cp0 = cos(pose.pitch)
        val sp = sin(pose.pitch)
        // Which cap faces the viewer: the front while this is positive.
        val facing = cy0 * cp0
        // Edge-on every slice would thin to a line: a floor on the foreshortening keeps it solid.
        fun floorV(v: Double) = if (abs(v) < 0.22) (if (v < 0) -0.22 else 0.22) else v
        val cy = floorV(cy0)
        val cp = floorV(cp0)

        // Body space: the box centre plus the pose's offset, roll and squash about the base.
        val cr = cos(pose.roll)
        val sr = sin(pose.roll)
        val kx = pose.sx * s
        val ky = pose.sy * s
        val lift = 50 * (1 - pose.sy) * s
        val body = mul(
            doubleArrayOf(dpr, 0.0, 0.0, dpr, 0.0, 0.0),
            doubleArrayOf(cr * kx, sr * kx, -sr * ky, cr * ky, full / 2 + pose.x * s - sr * lift, full / 2 + Rise * box + pose.y * s + cr * lift),
        )

        drawWhirl(body, pose, cfg, lx, ly, near = false)

        val parts = partsPath(cfg.type)
        if (parts != null) {
            drawSolid(parts, "${cfg.type.name}:parts", shapeParts(cfg.type)!!, halfDepth * 0.4, body, cfg, pal, partsState, cy, sy, cp, sp, facing, pose.roll, cap, lx, ly, shadow, highlight, spread, dev)
        }
        val path = bodyPath(cfg.type)
        drawSolid(path, cfg.type.name, shapePath(cfg.type), halfDepth, body, cfg, pal, bodyState, cy, sy, cp, sp, facing, pose.roll, cap, lx, ly, shadow, highlight, spread, dev)

        // The face is printed on the front cap: clipped to the front slice's outline.
        if (facing > -0.2) {
            val zf = if (facing >= 0) 1.0 else -1.0
            val sf = profile(zf, cap)
            val m0 = cy * sf
            val m1 = sy * sp * sf
            val m3 = cp * sf
            val e = zf * sy * halfDepth - 50 * m0
            val fo = -zf * cy * sp * halfDepth - 50 * m1 - 50 * m3
            val slice = doubleArrayOf(m0, m1, 0.0, m3, e, fo)
            withAffine(mul(body, slice)) {
                clipPath(path) {
                    // Back to body space inside the clip, then the face's own offset and scale.
                    val faceM = mul(inverse(slice), doubleArrayOf(cfg.faceScale, 0.0, 0.0, cfg.faceScale, cfg.faceX - 50, cfg.faceY - 50))
                    withAffine(faceM) { drawFace(pose, cfg) }
                }
            }
        }
        drawWhirl(body, pose, cfg, lx, ly, near = true)
    }

    /** One solid: the plastic material, or the slice stack (plastic falls back while it bakes). */
    private fun DrawScope.drawSolid(
        path: Path,
        key: String,
        pathData: String,
        halfDepth: Double,
        body: DoubleArray,
        cfg: BotDrawConfig,
        pal: BotPalette,
        state: PlasticState,
        cy: Double, sy: Double, cp: Double, sp: Double,
        facing: Double,
        roll: Double,
        cap: Double,
        lx: Double, ly: Double,
        shadow: Double, highlight: Double, spread: Double,
        dev: Float,
    ): Boolean {
        val mode = cfg.shading
        if (mode == BotAvatarShading.Plastic) {
            val rig = PlasticRig(cy, sy, cp, sp, facing, roll, halfDepth, cap, lx, ly, dev)
            if (drawPlastic(path, key, pathData, rig, body, cfg, state, BotMaterial(shadow, highlight, spread, cfg.rim))) return true
        }
        val mode2 = if (mode == BotAvatarShading.Plastic) BotAvatarShading.Smooth else mode
        val softMode = mode2 == BotAvatarShading.Smooth
        val crisp = mode2 == BotAvatarShading.Crisp
        // Crisp: the lit side and the cap as gradients (in each slice's own space, as the web fills them).
        val lit: Brush? = if (crisp) {
            Brush.linearGradient(
                0f to pal.light, 0.45f to pal.near, 1f to pal.dark,
                start = Offset((lx * 56).toFloat(), (ly * 56).toFloat()), end = Offset((-lx * 56).toFloat(), (-ly * 56).toFloat()),
            )
        } else {
            null
        }
        val capFill: Brush? = if (crisp) {
            Brush.linearGradient(
                0f to pal.capTop, 1f to pal.capBottom,
                start = Offset((lx * 46).toFloat(), (ly * 46).toFloat()), end = Offset((-lx * 46).toFloat(), (-ly * 46).toFloat()),
            )
        } else {
            null
        }
        val union = if (softMode) Path() else null
        val order = if (facing >= 0) 1 else -1
        for (j in 0 until Slices) {
            val k = if (order > 0) j else Slices - 1 - j
            val z = -1 + 2.0 * k / (Slices - 1)
            val s = profile(z, cap)
            val near = j.toDouble() / (Slices - 1)
            val m0 = cy * s
            val m1 = sy * sp * s
            val m3 = cp * s
            val e = z * sy * halfDepth - 50 * m0
            val fo = -z * cy * sp * halfDepth - 50 * m1 - 50 * m3
            val slice = doubleArrayOf(m0, m1, 0.0, m3, e, fo)
            withAffine(mul(body, slice)) {
                when {
                    softMode -> drawPath(path, pal.smoothMix[j])
                    j == Slices - 1 -> if (capFill != null) drawPath(path, capFill) else drawPath(path, pal.base)
                    near > 0.6 -> if (lit != null) drawPath(path, lit) else drawPath(path, pal.near)
                    else -> drawPath(path, pal.crispMix[j] ?: pal.near)
                }
            }
            if (union != null) {
                val copy = Path().apply { addPath(path) }
                copy.transform(Matrix().apply {
                    val v = values
                    v[0] = m0.toFloat(); v[1] = m1.toFloat(); v[4] = 0f; v[5] = m3.toFloat(); v[12] = e.toFloat(); v[13] = fo.toFloat()
                })
                union.addPath(copy)
            }
        }
        // Smooth: a soft shadow from the far side and a light from the near one, over the whole form.
        if (union != null) {
            withAffine(body) {
                clipPath(union) {
                    val sa = min(1.0, 0.34 * shadow).toFloat()
                    val r0 = (4 * spread).toFloat()
                    val r1 = (84 * spread).toFloat()
                    val sg = Brush.radialGradient(
                        0f to Color.Black.copy(alpha = sa), r0 / r1 to Color.Black.copy(alpha = sa),
                        (r0 + 0.5f * (r1 - r0)) / r1 to Color.Black.copy(alpha = sa * 0.35f), 1f to Color.Black.copy(alpha = 0f),
                        center = Offset((-lx * 45).toFloat(), (-ly * 45).toFloat()), radius = r1,
                    )
                    drawRect(sg, topLeft = Offset(-120f, -120f), size = Size(240f, 240f), blendMode = BlendMode.Multiply)
                    val ha = min(1.0, 0.22 * highlight).toFloat()
                    val hg = Brush.radialGradient(
                        0f to Color.White.copy(alpha = ha), 0.6f to Color.White.copy(alpha = ha * 0.23f), 1f to Color.White.copy(alpha = 0f),
                        center = Offset((lx * 37).toFloat(), (ly * 37).toFloat()), radius = (62 * spread).toFloat(),
                    )
                    drawRect(hg, topLeft = Offset(-120f, -120f), size = Size(240f, 240f))
                }
            }
        }
        return false
    }

    /** `drawPlasticCap`: the side copies from the matcap, then the front cap's lit texels. */
    private fun DrawScope.drawPlastic(
        path: Path,
        key: String,
        pathData: String,
        rig: PlasticRig,
        body: DoubleArray,
        cfg: BotDrawConfig,
        st: PlasticState,
        mat: BotMaterial,
    ): Boolean {
        val n = tierFor(rig.dev)
        val form = BotForms.formFor(key, pathData, n, rig.halfDepth, cfg.still) ?: return false
        st.update(form, rig, cfg.color, mat)
        val near = st.near ?: return false
        val rimB = st.rimBrush ?: return false
        val far = st.far ?: return false
        val texels = st.texels ?: return false

        // 1. The side copies, far → near, without the nearest (the texture is the whole front).
        val cy = rig.cy
        val sy = rig.sy
        val cp = rig.cp
        val sp = rig.sp
        val halfDepth = rig.halfDepth
        val cap = rig.cap
        val order = if (rig.facing >= 0) 1 else -1
        for (j in 0 until Slices - 1) {
            val k = if (order > 0) j else Slices - 1 - j
            val z = -1 + 2.0 * k / (Slices - 1)
            val s = profile(z, cap)
            val zn = z * order
            val m0 = cy * s
            val m1 = sy * sp * s
            val m3 = cp * s
            val e = z * sy * halfDepth - 50 * m0
            val fo = -z * cy * sp * halfDepth - 50 * m1 - 50 * m3
            val brush = if (zn > 0.4) near else if (zn >= 0) rimB else far
            withAffine(mul(body, doubleArrayOf(m0, m1, 0.0, m3, e, fo))) { drawPath(path, brush) }
        }

        // 2. The cap: the texture is the front seen head-on, stretched along a turn to span the
        //    front's projection from the trailing equator to the leading shoulder.
        val inv = 1 / (cy * cp)
        val dx = sy * halfDepth / cy
        val dy = -sp * halfDepth * inv
        val dl = hypot(dx, dy)
        val ex = if (dl > 1e-6) order * dx / dl else 1.0
        val ey = if (dl > 1e-6) order * dy / dl else 0.0
        val lead = 50 * cap + hypot(50 * (1 - cap), dl)
        val stretch = (lead + 50) / 100
        val shift = (lead - 50) / 2
        val a = 1 + (stretch - 1) * ex * ex
        val b = (stretch - 1) * ex * ey
        val d = 1 + (stretch - 1) * ey * ey
        val capM = mul(
            mul(body, doubleArrayOf(cy, sy * sp, 0.0, cp, 0.0, 0.0)),
            doubleArrayOf(a, b, b, d, shift * ex - 50 * a - 50 * b, shift * ey - 50 * b - 50 * d),
        )
        withAffine(capM) {
            clipPath(path) {
                drawImage(
                    texels.image,
                    srcSize = IntSize(texels.width, texels.height),
                    dstOffset = IntOffset(-Pad, -Pad),
                    dstSize = IntSize(Span, Span),
                    filterQuality = FilterQuality.High,
                )
            }
            // 3. Large avatars: a crisp hairline of the environment along the lit side of the silhouette.
            if (rig.dev >= 256 && mat.rim > 0 && mat.highlight > 0) {
                val al = min(0.5, 0.3 * mat.rim * min(1.4, mat.highlight)).toFloat()
                val l = st.lxy
                val c = Color(235, 244, 255)
                val g = Brush.linearGradient(
                    0f to c.copy(alpha = al), 0.45f to c.copy(alpha = 0.35f * al), 0.75f to c.copy(alpha = 0f),
                    start = Offset(50 + l[0] * 50, 50 + l[1] * 50), end = Offset(50 - l[0] * 50, 50 - l[1] * 50),
                )
                drawPath(path, g, style = Stroke(1.3f, join = StrokeJoin.Round), blendMode = BlendMode.SrcAtop)
            }
        }
        return true
    }

    /** `drawWhirl`: one tapered trail on a tilted ring; the far half behind, the near over the face. */
    private fun DrawScope.drawWhirl(body: DoubleArray, pose: BotPose, cfg: BotDrawConfig, lx: Double, ly: Double, near: Boolean) {
        val knobs = cfg.whirl
        val k = min(1.0, pose.whirl * knobs.strength)
        if (k <= 0.01) return
        val span = WhirlSpan * knobs.length
        val ink = whirlInk(cfg)
        val head = -pose.whirlAngle
        val rx = WhirlRx * knobs.size
        val ry = rx * WhirlRatio * knobs.tilt * (if (near) 1.14 else 0.86)
        val lightA = atan2(ly, lx) - WhirlTilt
        val rot = doubleArrayOf(cos(WhirlTilt), sin(WhirlTilt), -sin(WhirlTilt), cos(WhirlTilt), 0.0, 0.0)
        withAffine(mul(mul(body, rot), doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, 5.0))) {
            fun seg(a0: Double, a1: Double, width: Double, color: Color, dy: Double) {
                drawArc(
                    color,
                    startAngle = (a0 * 180 / PI).toFloat(),
                    sweepAngle = ((a1 - a0) * 180 / PI).toFloat(),
                    useCenter = false,
                    topLeft = Offset(-rx.toFloat(), (dy - ry).toFloat()),
                    size = Size((2 * rx).toFloat(), (2 * ry).toFloat()),
                    style = Stroke(width.toFloat(), cap = StrokeCap.Butt),
                )
            }
            // The near half casts a soft shadow on the body it crosses.
            if (near) {
                for (i in 0 until WhirlSegments) {
                    val f = i.toDouble() / WhirlSegments
                    val a1 = head + f * span
                    val a0 = a1 + span / WhirlSegments + 0.012
                    if (sin((a0 + a1) / 2) <= 0) continue
                    val fade = (1 - f).pow(1.3)
                    seg(a1, a0, (2 + 8 * fade) * 1.5 * knobs.width, Color.Black.copy(alpha = (0.2 * k * fade).toFloat()), 3.5)
                }
            }
            for (i in 0 until WhirlSegments) {
                val f = i.toDouble() / WhirlSegments
                val a1 = head + f * span
                val a0 = a1 + span / WhirlSegments + 0.012
                val mid = (a0 + a1) / 2
                if ((sin(mid) > 0) != near) continue
                val depth = 0.6 + 0.4 * sin(mid)
                val fade = (1 - f).pow(1.3)
                val puff = 1 + 0.18 * sin(f * 9 + 1.2)
                val width = (2 + 8 * fade) * depth * knobs.width * puff
                val a = k * (0.3 + 0.7 * fade) * depth
                val lit = 0.5 + 0.5 * cos(mid - lightA)
                fun alpha(v: Double) = v.coerceIn(0.0, 1.0).toFloat()
                seg(a1, a0, width * 2.6, ink.halo.toColor(alpha(a * 0.2)), 0.0)
                seg(a1, a0, width * 0.8, ink.dark.toColor(alpha(a * 0.45)), width * 0.32)
                seg(a1, a0, width, ink.base.toColor(alpha(a * 0.72)), 0.0)
                seg(a1, a0, width * 0.62, ink.light.toColor(alpha(a * 0.78 * (0.4 + 0.6 * lit))), -width * 0.16)
                seg(a1, a0, width * 0.24, Color.White.copy(alpha = alpha(a * 0.9 * (0.15 + 0.85 * lit * lit))), -width * 0.3)
            }
        }
    }

    /** `drawFace`: the eyes (one morphing curve each) and the mouth, placed on a sphere behind the cap. */
    private fun DrawScope.drawFace(pose: BotPose, cfg: BotDrawConfig) {
        val wd = pose.w[0]
        val ww = pose.w[1]
        val ws = pose.w[2]
        val ink = cfg.ink
        val ey = if (cfg.face == BotAvatarFace.Mouth) -3.5 else 1.0
        val half = EyeGap / 2
        val lx = pose.lookX
        val ly = pose.lookY
        val yaw = pose.yaw
        val pitch = pose.pitch

        fun at(x: Double, y: Double, alpha: Double, draw: DrawScope.(Float) -> Unit) {
            val lon = asin((x / FaceR).coerceIn(-1.0, 1.0)) + yaw
            val lat = asin((-y / FaceR).coerceIn(-1.0, 1.0)) + pitch
            val cl = cos(lat)
            val z = cos(lon) * cl
            if (z <= 0.02 || alpha <= 0.01) return
            val a = (alpha * min(1.0, z * 5)).toFloat()
            val qx = FaceR * sin(lon) * cl
            val qy = -FaceR * sin(lat)
            withTransform({
                translate(qx.toFloat(), qy.toFloat())
                scale(max(0.02, cos(lon)).toFloat(), max(0.02, cl).toFloat(), Offset.Zero)
            }) { draw(a) }
        }

        // The eyes take the head's aim once it really aims somewhere.
        fun past(v: Double, d: Double) = if (abs(v) <= d) 0.0 else kotlin.math.sign(v) * (abs(v) - d) / (1 - d)
        fun clamp1(v: Double) = v.coerceIn(-1.0, 1.0)
        val up = past(clamp1(-pose.pitch / 0.26 - pose.lookY / 7), 0.34)
        val side = abs(past(clamp1(pose.lookX / 4.5), 0.4))
        val tall = max(0.3, 1 + 0.55 * up - 0.1 * side)
        val wide = 1 - 0.05 * up + 0.12 * side
        val open = wd + ww * (1 - pose.laugh)
        val laugh = ww * pose.laugh
        val lift = max(0.0, -pose.y) / 26
        val sag = 0.5 + 0.5 * pose.breath
        for (sideSign in intArrayOf(-1, 1)) {
            val lid = if (sideSign < 0) pose.blinkL else pose.blinkR
            val e = (pose.eyeOpen * (1 - lid)).coerceIn(0.0, 1.0)
            val kOpen = open * e
            val kShut = open * (1 - e)
            val kLaugh = laugh
            val kSleep = ws
            val x0 = kOpen * 0.01 + kShut * 5.4 + kLaugh * 6.2 + kSleep * 6
            val y0 = kOpen * 1.1 * tall + kShut * 0.6 + kLaugh * (2.2 - lift * 1.5) + kSleep * (-1.4 + sag)
            val cyv = kOpen * -3.3 * tall + kShut * 0.6 + kLaugh * (-11.4 - 4 * lift) + kSleep * (5.4 + 2 * sag)
            val w = kOpen * EyeRx * 2 * wide + kShut * 2.8 + kLaugh * 4.4 + kSleep * 4
            val dx = lx * (kOpen + 0.5 * (kShut + kLaugh))
            val dy = ly * (kOpen + 0.5 * kShut)
            at(sideSign * half + dx, ey + dy, 1.0) { a ->
                roundStroke.color = ink.copy(alpha = ink.alpha * a)
                roundStroke.strokeWidth = w.toFloat()
                val eye = eyePath(x0, y0, cyv)
                drawIntoCanvas { it.drawPath(eye, roundStroke) }
            }
        }

        if (cfg.face == BotAvatarFace.Mouth) {
            val mx = lx * 0.35
            val kd = (0.6 + 0.4 * wd) * (1 + 0.06 * pose.breath)
            val kw = (0.6 + 0.4 * ww) * (1 + 0.25 * max(0.0, -pose.y) / 26)
            val r = 2.7 * ws * (1 + 0.25 * pose.breath)
            fun b(d: Double, w: Double, s: Double) = wd * d + ww * w + ws * s
            val path = mouthPath(
                hw0 = b(6.5 * kd, 9.5 * kw, r),
                t00 = b(1.9, 0.0, 0.0),
                a0 = b(2.0 / 3, 2.0 / 3, 0.0),
                yt0 = b(3.53 * kd, 1.6 * kw, -4 * r / 3),
                ab0 = b(2.0 / 3, 0.0, 0.0),
                yb0 = b(3.53 * kd, 17.3 * kw, 4 * r / 3),
            )
            at(mx, b(12.5, 11.6, 15.5), 1.0) { a -> drawPath(path, ink, alpha = a) }
        }
    }
}
