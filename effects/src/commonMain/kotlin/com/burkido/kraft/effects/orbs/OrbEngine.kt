package com.burkido.kraft.effects.orbs

import com.burkido.kraft.effects.core.jsRound
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// A transcription of thinking-orbs' pure geometry engine (src/engine/*.ts). All maths is in
// Double, like the JavaScript numbers it mirrors, so frames match the golden vectors to 1e-4.

internal fun frac(x: Double): Double = x - floor(x)

/** Deterministic hash in [0, 1). */
internal fun hashD(a: Double, b: Double): Double {
    val h = sin(a * 12.9898 + b * 78.233) * 43758.5453
    return h - floor(h)
}

/** Value noise on a 2D lattice — smooth, deterministic, cheap. */
internal fun vnoise(x: Double, y: Double): Double {
    val xi = floor(x)
    val yi = floor(y)
    var fx = x - xi
    var fy = y - yi
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    val a = hashD(xi, yi)
    val b = hashD(xi + 1, yi)
    val c = hashD(xi, yi + 1)
    val d = hashD(xi + 1, yi + 1)
    return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy
}

private val GOLDEN_ANGLE = PI * (3 - sqrt(5.0))

/** Shortest signed angular distance, wrapped to (-π, π]. */
private fun angleDelta(a: Double, b: Double): Double = atan2(sin(a - b), cos(a - b))

private fun radiusScale(size: Double, pow: Double): Double = (size / 300).pow(pow)

/** Shared spin + tilt + orthographic projection; results land in [px], [py], [pz]. */
private class Projector(yaw: Double, tilt: Double, private val cx: Double, private val cy: Double, private val scale: Double) {
    private val st = sin(tilt)
    private val ct = cos(tilt)
    private val sy = sin(yaw)
    private val cyw = cos(yaw)
    var px = 0.0
    var py = 0.0
    var pz = 0.0

    fun project(x: Double, y: Double, z: Double) {
        val x1 = x * cyw + z * sy
        val z1 = -x * sy + z * cyw
        val y1 = y * ct - z1 * st
        val z2 = y * st + z1 * ct
        px = cx + x1 * scale
        py = cy - y1 * scale
        pz = z2
    }
}

/** Evaluates [mode] at engine time [t] (seconds × speed) into [out]. */
internal fun buildFrame(mode: OrbMode, size: Double, t: Double, o: OrbOpts, out: OrbFrame) {
    out.clear()
    when (mode) {
        OrbMode.Globe -> frameGlobe(size, t, o, out)
        OrbMode.Rubik -> frameRubik(size, t, o, out)
        OrbMode.Wave -> frameWave(size, t, o, out)
        OrbMode.Orbits -> frameOrbits(size, t, o, out)
        OrbMode.Web -> frameWeb(size, t, o, out)
        OrbMode.Braid -> frameBraid(size, t, o, out)
        OrbMode.Ribbon, OrbMode.Ring -> frameRibbon(size, t, o, out)
        OrbMode.Morph -> frameMorph(size, t, o, out)
    }
    out.finalize(o.or("rMin", 0.3))
}

// --- Globe: lat/long field, a scan meridian sweeps — searching ---------------------------

private fun frameGlobe(size: Double, t: Double, o: OrbOpts, out: OrbFrame) {
    val spin = 0.5
    val cx = size / 2
    val cy = size / 2
    val radius = (size / 2) * 0.82
    val tilt = 0.4 + 0.06 * sin(t * 0.35)
    val pt = Projector(t * spin, tilt, cx, cy, radius)
    val scan = t * (spin + (1.7 - spin) * o.or("scanMul", 1.0))
    val rs = radiusScale(size, o.or("rsPow", 0.6))
    val dimBase = o.or("dimBase", 1.0)
    val rBase = o.or("rBase", 0.6)
    val rDepth = o.or("rDepth", 1.7)
    val rBoost = o.or("rBoost", 1.0)
    val inkFar = o.or("inkFar", 0.62)
    val inkSpan = o.or("inkSpan", 0.54)

    val latRings = o.or("latRings", 17.0).toInt()
    val lonDensity = o.or("lonDensity", 44.0)
    for (li in 0..latRings) {
        val lat = -PI / 2 + (li.toDouble() / latRings) * PI
        val cosLat = cos(lat)
        val sinLat = sin(lat)
        val lonCount = max(1.0, jsRound(abs(cosLat) * lonDensity)).toInt()
        for (lj in 0 until lonCount) {
            val lon = (lj.toDouble() / lonCount) * 2 * PI
            pt.project(cosLat * cos(lon), sinLat, cosLat * sin(lon))
            val z = pt.pz
            val depth = (z + 1) / 2
            val d = angleDelta(lon + t * spin, scan)
            val boost = exp(-(d * d) / 0.18) * max(0.0, z)
            out.addDot(
                x = pt.px,
                y = pt.py,
                z = z,
                r = (rBase + rDepth * depth + rBoost * boost) * rs,
                white = inkFar - inkSpan * depth,
                a = dimBase + (1 - dimBase) * min(1.0, boost),
            )
        }
    }
}

// --- Rubik: bands twist in quarter turns, scramble → solve — solving ---------------------

private class Move(val axis: Int, val lo: Double, val hi: Double, val ang: Double)

private fun makeMoves(count: Int): Array<Move> = Array(count) { i ->
    val axis = min(2, floor(hashD(i.toDouble(), 2.3) * 3).toInt())
    val lo = -1.0 + 0.5 * min(3.0, floor(hashD(i.toDouble(), 5.9) * 4))
    val dir = if (hashD(i.toDouble(), 7.7) < 0.5) 1 else -1
    Move(axis, lo, lo + 0.5, (dir * PI) / 2)
}

private fun frameRubik(size: Double, t: Double, o: OrbOpts, out: OrbFrame) {
    val cx = size / 2
    val cy = size / 2
    val r = (size / 2) * 0.82
    val pt = Projector(t * 0.55, 0.35 + 0.1 * sin(t * 0.9), cx, cy, r)
    val rs = radiusScale(size, o.or("rsPow", 0.6))
    val moveCount = o.or("moveCount", 14.0).toInt()
    val moves = makeMoves(moveCount)

    // solveCycle(t, count, slotDur 0.42, rest 1.2): scramble, then replay in reverse.
    val slotDur = 0.42
    val cyc = 2 * moveCount * slotDur + 1.2
    val tc = t % cyc
    val amount = DoubleArray(moveCount)
    var active = -1
    if (tc < 2 * moveCount * slotDur) {
        val slot = floor(tc / slotDur).toInt()
        val p = (tc - slot * slotDur) / slotDur
        val cl = min(1.0, p / 0.7)
        val ep = 1 - (1 - cl).pow(3)
        if (slot < moveCount) {
            for (i in 0 until slot) amount[i] = 1.0
            amount[slot] = ep
            active = slot
        } else {
            val u = 2 * moveCount - 1 - slot
            for (i in 0 until u) amount[i] = 1.0
            amount[u] = 1 - ep
            active = u
        }
    }

    val rBase = o.or("rBase", 0.6)
    val rDepth = o.or("rDepth", 1.7)
    val rActive = o.or("rActive", 0.3)
    val inkFar = o.or("inkFar", 0.62)
    val inkSpan = o.or("inkSpan", 0.54)
    val latRings = o.or("latRings", 15.0).toInt()
    val lonDensity = o.or("lonDensity", 40.0)
    for (li in 0..latRings) {
        val lat = -PI / 2 + (li.toDouble() / latRings) * PI
        val cosLat = cos(lat)
        val sinLat = sin(lat)
        val lonCount = max(1.0, jsRound(abs(cosLat) * lonDensity)).toInt()
        for (lj in 0 until lonCount) {
            val lon = (lj.toDouble() / lonCount) * 2 * PI
            var x = cosLat * cos(lon)
            var y = sinLat
            var z = cosLat * sin(lon)
            var inActive = false
            for (i in moves.indices) {
                if (amount[i] <= 0) continue
                val mv = moves[i]
                val coord = when (mv.axis) {
                    0 -> x
                    1 -> y
                    else -> z
                }
                if (coord < mv.lo || coord >= mv.hi) continue
                if (i == active) inActive = true
                val a = mv.ang * amount[i]
                val ca = cos(a)
                val sa = sin(a)
                when (mv.axis) {
                    0 -> {
                        val y2 = y * ca - z * sa
                        z = y * sa + z * ca
                        y = y2
                    }
                    1 -> {
                        val x2 = x * ca + z * sa
                        z = -x * sa + z * ca
                        x = x2
                    }
                    else -> {
                        val x2 = x * ca - y * sa
                        y = x * sa + y * ca
                        x = x2
                    }
                }
            }
            pt.project(x, y, z)
            val depth = (pt.pz + 1) / 2
            out.addDot(
                x = pt.px,
                y = pt.py,
                z = pt.pz,
                r = (rBase + rDepth * depth + (if (inActive) rActive else 0.0)) * rs,
                white = inkFar - inkSpan * depth - (if (inActive) 0.14 else 0.0),
            )
        }
    }
}

// --- Wave: a waveform rolls through the rings — listening --------------------------------

private fun frameWave(size: Double, t: Double, o: OrbOpts, out: OrbFrame) {
    val cx = size / 2
    val cy = size / 2
    val r = (size / 2) * 0.874
    val pt = Projector(t * 0.18, 0.38, cx, cy, 1.0)
    val rs = radiusScale(size, o.or("rsPow", 0.6))
    val rBase = o.or("rBase", 0.6)
    val rDepth = o.or("rDepth", 1.7)
    val rings = o.or("rings", 15.0).toInt()
    val lonDensity = o.or("lonDensity", 40.0)
    for (ri in 0..rings) {
        val lat = -PI / 2 + (ri.toDouble() / rings) * PI
        val cosLat = cos(lat)
        val sinLat = sin(lat)
        val w = 0.62 * sin(t * 2.1 - ri * 0.52) + 0.38 * sin(t * 1.27 + ri * 0.83)
        val rr = r * (0.88 + 0.105 * w)
        val lonCount = max(1.0, jsRound(abs(cosLat) * lonDensity)).toInt()
        for (lj in 0 until lonCount) {
            val lon = (lj.toDouble() / lonCount) * 2 * PI
            pt.project(cosLat * cos(lon) * rr, sinLat * rr, cosLat * sin(lon) * rr)
            val z = pt.pz
            val depth = (z / r + 1) / 2
            val crest = max(0.0, w)
            out.addDot(
                x = pt.px,
                y = pt.py,
                z = z,
                r = (rBase + rDepth * depth) * (1 + 0.4 * crest) * rs,
                white = 0.66 - 0.56 * depth - 0.1 * crest,
            )
        }
    }
}

// --- Orbits: particles on tilted orbits — working -----------------------------------------

private fun frameOrbits(size: Double, t: Double, o: OrbOpts, out: OrbFrame) {
    val cx = size / 2
    val cy = size / 2
    val r = (size / 2) * 0.82
    val pt = Projector(t * 0.12, 0.3, cx, cy, 1.0)
    val rs = radiusScale(size, o.or("rsPow", 0.6))
    val orbitN = o.or("orbitN", 12.0).toInt()
    val ghostN = o.or("ghostN", 40.0).toInt()
    val particles = o.or("particles", 3.0).toInt()
    val ghostR = o.or("ghostR", 0.9)
    val ghostA = o.or("ghostA", 0.5)
    val partR = o.or("partR", 1.2)
    val partRDepth = o.or("partRDepth", 1.6)

    for (orb in 0 until orbitN) {
        val h1 = hashD(orb.toDouble(), 1.7)
        val h2 = hashD(orb.toDouble(), 5.2)
        val h3 = hashD(orb.toDouble(), 8.9)
        val ro = r * (0.45 + 0.52 * h1)
        val th = h1 * 2 * PI
        val phi = acos(2 * h2 - 1)
        val nx = sin(phi) * cos(th)
        val ny = cos(phi)
        val nz = sin(phi) * sin(th)
        var ux = -ny
        var uy = nx
        val uz = 0.0
        val ul = max(1e-6, sqrt(ux * ux + uy * uy))
        ux /= ul
        uy /= ul
        val vx = ny * uz - nz * uy
        val vy = nz * ux - nx * uz
        val vz = nx * uy - ny * ux
        val speed = (0.25 + 0.55 * h3) * (if (h3 > 0.5) 1 else -1)

        for (k in 0 until ghostN) {
            val a = (k.toDouble() / ghostN) * 2 * PI
            pt.project(
                (ux * cos(a) + vx * sin(a)) * ro,
                (uy * cos(a) + vy * sin(a)) * ro,
                (uz * cos(a) + vz * sin(a)) * ro,
            )
            val depth = (pt.pz / ro + 1) / 2
            out.addDot(pt.px, pt.py, pt.pz, ghostR * rs, 0.72, ghostA * (0.4 + 0.6 * depth))
        }
        for (m in 0 until particles) {
            val a = t * speed + (m.toDouble() / particles) * 2 * PI + h2 * 6
            pt.project(
                (ux * cos(a) + vx * sin(a)) * ro,
                (uy * cos(a) + vy * sin(a)) * ro,
                (uz * cos(a) + vz * sin(a)) * ro,
            )
            val depth = (pt.pz / ro + 1) / 2
            out.addDot(pt.px, pt.py, pt.pz, (partR + partRDepth * depth) * rs, 0.3 - 0.22 * depth)
        }
    }
}

// --- Web: a constellation wires itself — connecting ---------------------------------------

private fun frameWeb(size: Double, t: Double, o: OrbOpts, out: OrbFrame) {
    val cx = size / 2
    val cy = size / 2
    val r = (size / 2) * 0.8 * o.or("spread", 1.0)
    val pt = Projector(t * 0.12, 0.32, cx, cy, r)
    val rs = radiusScale(size, o.or("rsPow", 0.6))
    val nodeN = o.or("nodeN", 30.0).toInt()
    val thr = o.or("thr", 0.72)
    val nodeR = o.or("nodeR", 1.4)
    val nodeRDepth = o.or("nodeRDepth", 1.8)
    val lineW = o.or("lineW", 0.8)

    val nodes = DoubleArray(nodeN * 3)
    for (i in 0 until nodeN) {
        // fibDir(i, nodeN)
        val fy = 1 - (2 * (i + 0.5)) / nodeN
        val rad = sqrt(1 - fy * fy)
        val fa = i * GOLDEN_ANGLE
        val x = rad * cos(fa) + 0.3 * (vnoise(i * 0.31 + 9, t * 0.24) - 0.5) * 2
        val y = fy + 0.3 * (vnoise(i * 0.53 + 27, t * 0.21) - 0.5) * 2
        val z = rad * sin(fa) + 0.3 * (vnoise(i * 0.77 + 55, t * 0.27) - 0.5) * 2
        val l = sqrt(x * x + y * y + z * z)
        nodes[i * 3] = x / l
        nodes[i * 3 + 1] = y / l
        nodes[i * 3 + 2] = z / l
    }

    for (i in 0 until nodeN) {
        for (j in i + 1 until nodeN) {
            val dx = nodes[i * 3] - nodes[j * 3]
            val dy = nodes[i * 3 + 1] - nodes[j * 3 + 1]
            val dz = nodes[i * 3 + 2] - nodes[j * 3 + 2]
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist >= thr) continue
            pt.project(nodes[i * 3], nodes[i * 3 + 1], nodes[i * 3 + 2])
            val x1 = pt.px
            val y1 = pt.py
            val z1 = pt.pz
            pt.project(nodes[j * 3], nodes[j * 3 + 1], nodes[j * 3 + 2])
            val depth = ((z1 + pt.pz) / 2 + 1) / 2
            out.addLine(
                x1 = x1,
                y1 = y1,
                x2 = pt.px,
                y2 = pt.py,
                white = 0.42,
                a = (1 - dist / thr) * (0.3 + 0.55 * depth),
                w = max(0.6, lineW * rs),
            )
        }
    }

    for (i in 0 until nodeN) {
        pt.project(nodes[i * 3], nodes[i * 3 + 1], nodes[i * 3 + 2])
        val depth = (pt.pz + 1) / 2
        val pulse = 1 + 0.25 * sin(t * 1.4 + i * 2.7)
        out.addDot(pt.px, pt.py, pt.pz, (nodeR + nodeRDepth * depth) * pulse * rs, 0.55 - 0.45 * depth)
    }

    val signals = o.or("signals", 5.0).toInt()
    for (s in 0 until signals) {
        val seg = floor(t * 0.55 + s * 7.31)
        val a = floor(hashD(seg, s * 3.1 + 1.7) * nodeN).toInt()
        val b = floor(hashD(seg, s * 5.7 + 4.2) * nodeN).toInt()
        if (a == b) continue
        val f = frac(t * 0.55 + s * 7.31)
        val x = nodes[a * 3] + (nodes[b * 3] - nodes[a * 3]) * f
        val y = nodes[a * 3 + 1] + (nodes[b * 3 + 1] - nodes[a * 3 + 1]) * f
        val z = nodes[a * 3 + 2] + (nodes[b * 3 + 2] - nodes[a * 3 + 2]) * f
        val l = max(1e-6, sqrt(x * x + y * y + z * z))
        pt.project(x / l, y / l, z / l)
        val depth = (pt.pz + 1) / 2
        out.addDot(pt.px, pt.py, pt.pz, (nodeR * 1.5 + nodeRDepth * depth) * rs, 0.05, 0.5 + 0.5 * depth)
    }
}

// --- Braid: three strands plait around the sphere — weaving -------------------------------

private fun ghostSphere(ghostN: Int, r: Double, rs: Double, pt: Projector, out: OrbFrame) {
    for (i in 0 until ghostN) {
        val fy = 1 - (2 * (i + 0.5)) / ghostN
        val rad = sqrt(1 - fy * fy)
        val fa = i * GOLDEN_ANGLE
        pt.project(rad * cos(fa) * r, fy * r, rad * sin(fa) * r)
        val depth = (pt.pz / r + 1) / 2
        out.addDot(pt.px, pt.py, pt.pz, 0.8 * rs, 0.78, 0.1 + 0.22 * depth)
    }
}

private fun frameBraid(size: Double, t: Double, o: OrbOpts, out: OrbFrame) {
    val cx = size / 2
    val cy = size / 2
    val r = (size / 2) * 0.76
    val pt = Projector(t * 0.4, 0.3, cx, cy, 1.0)
    val rs = radiusScale(size, o.or("rsPow", 0.6))
    ghostSphere(o.or("ghostN", 150.0).toInt(), r, rs, pt, out)

    val strandN = o.or("strandN", 52.0).toInt()
    val turns = o.or("turns", 3.0)
    val rBase = o.or("rBase", 1.2)
    val rDepth = o.or("rDepth", 1.8)
    for (s in 0 until 3) {
        val phase = (s / 3.0) * 2 * PI
        for (i in 0 until strandN) {
            val u = (frac(i.toDouble() / strandN + t * 0.045) * 2 - 1) * 0.96
            val surf = sqrt(max(0.0, 1 - u * u))
            val endFade = min(1.0, (1 - abs(u)) / 0.1)
            val a = u * PI * turns + phase
            val weave = 1 + 0.075 * sin(u * PI * turns * 2 + phase * 2 + t * 0.8)
            val rr = surf * r * weave
            pt.project(cos(a) * rr, u * r * weave, sin(a) * rr)
            val depth = (pt.pz / r + 1) / 2
            out.addDot(
                x = pt.px,
                y = pt.py,
                z = pt.pz,
                r = (rBase + rDepth * depth) * rs,
                white = 0.55 - 0.45 * depth,
                a = endFade * (0.45 + 0.55 * depth),
            )
        }
    }
}

// --- Ribbon / ring: an undulating sash (composing) or a face-on ring (breathing) -----------

private fun frameRibbon(size: Double, t: Double, o: OrbOpts, out: OrbFrame) {
    val cx = size / 2
    val cy = size / 2
    val r = (size / 2) * 0.78
    val spin = o.or("spin", 1.0)
    val camTilt = 0.3
    val pt = Projector(t * 0.1 * spin, camTilt, cx, cy, 1.0)
    val rs = radiusScale(size, o.or("rsPow", 0.6))
    ghostSphere(o.or("ghostN", 150.0).toInt(), r, rs, pt, out)

    val faceOn = (o["faceOn"] ?: 0.0) != 0.0
    val ya = t * 0.24 * spin
    val ta = if (faceOn) -camTilt else 0.55 + 0.3 * sin(t * 0.18) * spin
    val ux = cos(ya)
    val uy = 0.0
    val uz = sin(ya)
    val vx = -uz * sin(ta)
    val vy = cos(ta)
    val vz = ux * sin(ta)
    val nx = uy * vz - uz * vy
    val ny = uz * vx - ux * vz
    val nz = ux * vy - uy * vx

    val wobMul = o.or("wobMul", 1.0)
    val wobAmp = 0.23 * wobMul
    val baseR = if (faceOn) r / (1 + 0.85 * wobAmp) else r
    val rBase = o.or("rBase", 1.1)
    val rDepth = o.or("rDepth", 1.7)

    val baseLanes = o.or("lanes", 5.0)
    val segs = o.or("segs", 88.0).toInt()
    val lanes = max(1.0, jsRound(baseLanes * o.or("bandMul", 1.0))).toInt()
    for (w in 0 until lanes) {
        val laneOff = (w - (lanes - 1) / 2.0) * 0.075
        val edge = abs(w - (lanes - 1) / 2.0) / max(1.0, (lanes - 1) / 2.0)
        for (k in 0 until segs) {
            val a = (k.toDouble() / segs) * 2 * PI
            val wob = (0.16 * sin(a * 3 - t * 1.7 + w * 0.22) + 0.07 * sin(a * 5 + t * 1.1)) * wobMul
            val radial = if (faceOn) 1 + wob else 1.0
            val off = if (faceOn) laneOff else laneOff + wob
            val x = ux * cos(a) + vx * sin(a) + nx * off
            val y = uy * cos(a) + vy * sin(a) + ny * off
            val z = uz * cos(a) + vz * sin(a) + nz * off
            val l = sqrt(x * x + y * y + z * z)
            val rr = baseR * radial
            pt.project((x / l) * rr, (y / l) * rr, (z / l) * rr)
            val depth = (pt.pz / r + 1) / 2
            out.addDot(
                x = pt.px,
                y = pt.py,
                z = pt.pz,
                r = (rBase + rDepth * depth) * (1 - 0.25 * edge) * rs,
                white = 0.52 - 0.44 * depth + 0.18 * edge,
                a = 0.4 + 0.6 * depth,
            )
        }
    }
}

// --- Morph: a dotted outline cycling circle → triangle → square — shaping -----------------

private fun smoothE(x: Double): Double = x * x * (3 - 2 * x)

/** A closed outline parameterised by arc length, starting top-centre, clockwise. */
private class OutlinePath(private val verts: Array<DoubleArray>?) {
    private val lengths: DoubleArray
    private val total: Double

    init {
        if (verts == null) {
            lengths = DoubleArray(0)
            total = 0.0
        } else {
            val v = verts.size
            lengths = DoubleArray(v) { i ->
                val a = verts[i]
                val b = verts[(i + 1) % v]
                hypot(b[0] - a[0], b[1] - a[1])
            }
            total = lengths.sum()
        }
    }

    /** Writes the point at arc fraction [f] into [out] (x, y). */
    fun at(f: Double, out: DoubleArray) {
        if (verts == null) {
            val a = -PI / 2 + f * 2 * PI
            out[0] = cos(a) * 0.24
            out[1] = sin(a) * 0.24
            return
        }
        val v = verts.size
        var target = f * total
        var i = 0
        while (target > lengths[i] && i < v - 1) {
            target -= lengths[i]
            i++
        }
        val a = verts[i]
        val b = verts[(i + 1) % v]
        val ff = if (lengths[i] != 0.0) min(1.0, target / lengths[i]) else 0.0
        out[0] = a[0] + (b[0] - a[0]) * ff
        out[1] = a[1] + (b[1] - a[1]) * ff
    }
}

private val CYCLE = arrayOf(
    OutlinePath(null),
    OutlinePath(arrayOf(doubleArrayOf(0.0, -0.26), doubleArrayOf(0.24, 0.16), doubleArrayOf(-0.24, 0.16))),
    OutlinePath(
        arrayOf(
            doubleArrayOf(0.0, -0.2), doubleArrayOf(0.2, -0.2), doubleArrayOf(0.2, 0.2),
            doubleArrayOf(-0.2, 0.2), doubleArrayOf(-0.2, -0.2),
        ),
    ),
)

private const val MORPH_HOLD = 1.4
private const val MORPH_DURATION = 0.9
private const val MORPH_SEGMENT = MORPH_HOLD + MORPH_DURATION
private const val MORPH_SAMPLES = 160

private fun frameMorph(size: Double, t: Double, o: OrbOpts, out: OrbFrame) {
    val k = CYCLE.size
    val tc = t % (MORPH_SEGMENT * k)
    val shape = o["shape"]
    val held = if (shape != null && shape >= 0 && shape < k) floor(shape).toInt() else -1
    val index = if (held >= 0) held else floor(tc / MORPH_SEGMENT).toInt()
    val local = if (held >= 0) t % MORPH_SEGMENT else tc - index * MORPH_SEGMENT
    val m = if (held >= 0) 0.0 else if (local > MORPH_HOLD) smoothE((local - MORPH_HOLD) / MORPH_DURATION) else 0.0
    val spread = o.or("spread", 1.0)

    val pathA = CYCLE[index]
    val pathB = if (held >= 0) pathA else CYCLE[(index + 1) % k]
    val px = DoubleArray(MORPH_SAMPLES)
    val py = DoubleArray(MORPH_SAMPLES)
    val a = DoubleArray(2)
    val b = DoubleArray(2)
    for (i in 0 until MORPH_SAMPLES) {
        val f = i.toDouble() / MORPH_SAMPLES
        pathA.at(f, a)
        pathB.at(f, b)
        px[i] = (a[0] + (b[0] - a[0]) * m) * spread
        py[i] = (a[1] + (b[1] - a[1]) * m) * spread
    }
    val lengths = DoubleArray(MORPH_SAMPLES)
    var total = 0.0
    for (i in 0 until MORPH_SAMPLES) {
        val j = (i + 1) % MORPH_SAMPLES
        lengths[i] = hypot(px[j] - px[i], py[j] - py[i])
        total += lengths[i]
    }

    val n = max(6.0, jsRound(34 * o.or("iconD", 1.0))).toInt()
    val re = o.or("rDot", 0.021) * 1.35 * spread
    val pulse = 1 + 0.02 * sin(local * 3.1)
    val c2 = size / 2
    var seg = 0
    var acc = 0.0
    for (k2 in 0 until n) {
        val target = (k2.toDouble() / n) * total
        while (acc + lengths[seg] < target && seg < MORPH_SAMPLES - 1) {
            acc += lengths[seg]
            seg++
        }
        val next = (seg + 1) % MORPH_SAMPLES
        val f = if (lengths[seg] != 0.0) min(1.0, (target - acc) / lengths[seg]) else 0.0
        val x = (px[seg] + (px[next] - px[seg]) * f) * pulse
        val y = (py[seg] + (py[next] - py[seg]) * f) * pulse
        out.addDot(c2 + x * size, c2 + y * size, 0.0, max(0.35, re * size), 0.1)
    }
}
