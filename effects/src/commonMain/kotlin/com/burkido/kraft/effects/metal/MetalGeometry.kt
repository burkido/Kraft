package com.burkido.kraft.effects.metal

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A point displacement: (x, y) in the host's dp box → where it is drawn. */
internal fun interface Deform {
    fun apply(x: Float, y: Float, out: FloatArray)
}

/** The ring's two silhouettes: a rounded pill or a true circle. */
internal enum class ShapeKind { Pill, Circle }

/** Reusable flat outline buffer (x0, y0, x1, y1, …), so a bend traces outlines without garbage. */
internal class OutlineBuf {
    var xy = FloatArray(512 * 2)
    var n = 0
}

private const val ArcN = 14
private const val EdgeStep = 1.5f
private val scratchPoint = FloatArray(2)

/**
 * `roundRectOutline`: a rounded rect sampled clockwise from the end of the top-left corner —
 * edges every ~1.5 dp, 14 points per corner — optionally displaced by [deform].
 */
internal fun roundRectOutline(x: Float, y: Float, w: Float, h: Float, radius: Float, deform: Deform?, buf: OutlineBuf): OutlineBuf {
    val r = max(0f, min(radius, min(w, h) / 2))
    val need = 4 * (ArcN + 1) + ceil(2 * (w + h) / EdgeStep).toInt() + 8
    if (buf.xy.size < need * 2) buf.xy = FloatArray(need * 2)
    var n = 0
    fun push(px: Float, py: Float) {
        if (deform != null) {
            deform.apply(px, py, scratchPoint)
            buf.xy[n * 2] = scratchPoint[0]; buf.xy[n * 2 + 1] = scratchPoint[1]
        } else {
            buf.xy[n * 2] = px; buf.xy[n * 2 + 1] = py
        }
        n++
    }
    fun edge(x0: Float, y0: Float, x1: Float, y1: Float) {
        val len = hypot(x1 - x0, y1 - y0)
        val k = max(1, ceil(len / EdgeStep).toInt())
        for (i in 0 until k) {
            val t = i.toFloat() / k
            push(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t)
        }
    }
    fun arc(cx: Float, cy: Float, a0: Double, a1: Double) {
        for (i in 0..ArcN) {
            val a = a0 + (a1 - a0) * (i.toDouble() / ArcN)
            push((cx + r * cos(a)).toFloat(), (cy + r * sin(a)).toFloat())
        }
    }
    edge(x + r, y, x + w - r, y)
    arc(x + w - r, y + r, -PI / 2, 0.0)
    edge(x + w, y + r, x + w, y + h - r)
    arc(x + w - r, y + h - r, 0.0, PI / 2)
    edge(x + w - r, y + h, x + r, y + h)
    arc(x + r, y + h - r, PI / 2, PI)
    edge(x, y + h - r, x, y + r)
    arc(x + r, y + r, PI, 1.5 * PI)
    buf.n = n
    return buf
}

/** Appends a closed outline, scaled by [scale] (dp → px) and offset by ([ox], [oy]). */
internal fun Path.addOutline(buf: OutlineBuf, scale: Float, ox: Float = 0f, oy: Float = 0f) {
    val xy = buf.xy
    for (i in 0 until buf.n) {
        val x = xy[i * 2] * scale + ox
        val y = xy[i * 2 + 1] * scale + oy
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** The ring band: the outer outline with the inner one punched out. */
internal fun bandPath(outer: OutlineBuf, inner: OutlineBuf, scale: Float, into: Path = Path()): Path {
    into.rewind()
    into.fillType = PathFillType.EvenOdd
    into.addOutline(outer, scale)
    into.addOutline(inner, scale)
    return into
}

internal fun rrPerim(w: Float, h: Float, r: Float): Float {
    val rr = max(0f, min(r, min(w, h) / 2))
    return 2 * max(0f, w - 2 * rr) + 2 * max(0f, h - 2 * rr) + 2 * PI.toFloat() * rr
}

internal fun shapePerim(w: Float, h: Float, r: Float, kind: ShapeKind): Float =
    if (kind == ShapeKind.Circle) 2 * PI.toFloat() * max(0f, min(r, min(w, h) / 2)) else rrPerim(w, h, r)

/**
 * `sampleAtArc`: the point at arc length [s] along the outline, pushed [inset] inward and
 * [outward] outward, clockwise from the top edge's left end (circle: from 12 o'clock).
 */
internal fun sampleAtArc(s0: Float, w: Float, h: Float, r: Float, inset: Float, outward: Float, kind: ShapeKind, out: FloatArray) {
    val rr = max(0f, min(r, min(w, h) / 2))
    if (kind == ShapeKind.Circle) {
        val perim = 2 * PI.toFloat() * rr
        if (perim <= 0.0001f) { out[0] = w / 2; out[1] = h / 2; return }
        val s = ((s0 % perim) + perim) % perim
        val theta = -PI / 2 + (s / perim) * PI * 2
        val rad = max(0f, rr - inset + outward)
        out[0] = (w / 2 + rad * cos(theta)).toFloat()
        out[1] = (h / 2 + rad * sin(theta)).toFloat()
        return
    }
    val topLen = max(0f, w - 2 * rr)
    val sideLen = max(0f, h - 2 * rr)
    val arcLen = PI.toFloat() * rr / 2
    val perim = 2 * (topLen + sideLen) + 4 * arcLen
    var d = ((s0 % perim) + perim) % perim
    val rad = max(0f, rr - inset + outward)
    fun arcPoint(cx: Float, cy: Float, theta: Double) {
        out[0] = (cx + rad * cos(theta)).toFloat(); out[1] = (cy + rad * sin(theta)).toFloat()
    }
    val q = PI / 2
    if (d < topLen) { out[0] = rr + d; out[1] = inset - outward; return }
    d -= topLen
    if (d < arcLen) { arcPoint(w - rr, rr, -q + (if (arcLen > 0) d / arcLen else 0f) * q); return }
    d -= arcLen
    if (d < sideLen) { out[0] = w - inset + outward; out[1] = rr + d; return }
    d -= sideLen
    if (d < arcLen) { arcPoint(w - rr, h - rr, (if (arcLen > 0) d / arcLen else 0f) * q); return }
    d -= arcLen
    if (d < topLen) { out[0] = w - rr - d; out[1] = h - inset + outward; return }
    d -= topLen
    if (d < arcLen) { arcPoint(rr, h - rr, q + (if (arcLen > 0) d / arcLen else 0f) * q); return }
    d -= arcLen
    if (d < sideLen) { out[0] = inset - outward; out[1] = h - rr - d; return }
    d -= sideLen
    arcPoint(rr, rr, PI + (if (arcLen > 0) d / arcLen else 0f) * q)
}

private val tA = FloatArray(2)
private val tB = FloatArray(2)

internal fun tangentAngleAtArc(s: Float, w: Float, h: Float, r: Float, inset: Float, kind: ShapeKind): Float {
    sampleAtArc(s - 0.1f, w, h, r, inset, 0f, kind, tA)
    sampleAtArc(s + 0.1f, w, h, r, inset, 0f, kind, tB)
    return atan2(tB[1] - tA[1], tB[0] - tA[0])
}

internal fun smoothRange(a: Float, b: Float, x: Float): Float {
    if (a == b) return if (x < a) 0f else 1f
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}
