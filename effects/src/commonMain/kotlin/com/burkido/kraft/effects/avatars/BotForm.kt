package com.burkido.kraft.effects.avatars

import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The plastic material's form (`plastic.ts`, the bake): once per outline and texture size the
 * outline's coverage raster becomes a pillow height field — the torsion function of the outline,
 * so lobes are domes, rays are tubes and there is no crease — its normals as matcap cells, and
 * baked horizon occlusion.
 */

/** The texture covers design units [−Pad, 100 + Pad]. */
internal const val Pad = 3
internal const val Span = 100 + 2 * Pad

/** Matcap side: (nx, ny) ∈ [−1, 1]² in M × M cells. */
internal const val McSize = 64

private const val Inf = 1e12f

/** Texture size for an avatar [devicePx] wide (`tierFor`). */
internal fun tierFor(devicePx: Float): Int = if (devicePx <= 100) 64 else if (devicePx <= 224) 96 else 128

/** One outline's baked form (`Form`). */
internal class BotForm(
    val n: Int,
    /** Bilinear cell in the matcap: index of the top-left cell … */
    val i00: ShortArray,
    /** … and the weights inside it, 0..255. */
    val wx: ByteArray,
    val wy: ByteArray,
    /** Baked occlusion × edge darkening, gamma-compensated, 0..255; 0 = not drawn. */
    val ao: ByteArray,
)

/* Felzenszwalb–Huttenlocher 1-D squared distance transform; s gets the nearest site. */
private fun edt1d(f: FloatArray, n: Int, d: FloatArray, s: IntArray, v: IntArray, z: FloatArray) {
    var k = 0
    v[0] = 0
    z[0] = -1e30f
    z[1] = 1e30f
    for (q in 1 until n) {
        var x: Float
        while (true) {
            val vk = v[k]
            x = (f[q] + q * q - f[vk] - vk * vk) / (2 * (q - vk))
            if (x > z[k]) break
            k--
        }
        k++
        v[k] = q
        z[k] = x
        z[k + 1] = 1e30f
    }
    k = 0
    for (q in 0 until n) {
        while (z[k + 1] < q) k++
        val vk = v[k]
        d[q] = ((q - vk) * (q - vk)).toFloat() + f[vk]
        s[q] = vk
    }
}

/* Squared texel distance to the nearest texel whose mask equals [site] (and optionally its index). */
private fun edt2d(mask: ByteArray, site: Int, n: Int, out: FloatArray, near: IntArray?) {
    val f = FloatArray(n)
    val d = FloatArray(n)
    val s = IntArray(n)
    val v = IntArray(n)
    val z = FloatArray(n + 1)
    val g = FloatArray(n * n)
    val row = IntArray(n * n)
    for (x in 0 until n) {
        for (y in 0 until n) f[y] = if (mask[y * n + x].toInt() == site) 0f else Inf
        edt1d(f, n, d, s, v, z)
        for (y in 0 until n) {
            g[y * n + x] = d[y]
            row[y * n + x] = s[y]
        }
    }
    for (y in 0 until n) {
        val o = y * n
        for (x in 0 until n) f[x] = g[o + x]
        edt1d(f, n, d, s, v, z)
        for (x in 0 until n) {
            out[o + x] = d[x]
            if (near != null) near[o + x] = row[o + s[x]] * n + s[x]
        }
    }
}

/* Separable binomial blur [1 4 6 4 1]/16, edges clamped. */
private fun blur5(a: FloatArray, n: Int, tmp: FloatArray) {
    for (y in 0 until n) {
        val o = y * n
        for (x in 0 until n) {
            val x0 = if (x < 2) 0 else x - 2
            val x1 = if (x < 1) 0 else x - 1
            val x3 = if (x > n - 2) n - 1 else x + 1
            val x4 = if (x > n - 3) n - 1 else x + 2
            tmp[o + x] = (a[o + x0] + 4 * a[o + x1] + 6 * a[o + x] + 4 * a[o + x3] + a[o + x4]) * 0.0625f
        }
    }
    for (x in 0 until n) {
        for (y in 0 until n) {
            val y0 = if (y < 2) 0 else y - 2
            val y1 = if (y < 1) 0 else y - 1
            val y3 = if (y > n - 2) n - 1 else y + 1
            val y4 = if (y > n - 3) n - 1 else y + 2
            a[y * n + x] = (tmp[y0 * n + x] + 4 * tmp[y1 * n + x] + 6 * tmp[y * n + x] + 4 * tmp[y3 * n + x] + tmp[y4 * n + x]) * 0.0625f
        }
    }
}

private class Level(val n: Int, val mask: ByteArray, val phi: FloatArray)

/*
 * Δφ = −1 inside, φ = 0 outside: Gauss–Seidel with over-relaxation, cascaded from a quarter-size
 * grid so the smooth part converges cheaply and the fine sweeps only settle the boundary.
 */
private fun poisson(cov: ByteArray, bigN: Int, u: Float): FloatArray {
    val levels = ArrayList<Level>()
    var n = bigN
    var f = 1
    while ((f <= 4 && n % 2 == 0) || f == 1) {
        val mask = ByteArray(n * n)
        for (y in 0 until n) {
            for (x in 0 until n) {
                var sum = 0
                for (j in 0 until f) for (i in 0 until f) sum += cov[(y * f + j) * bigN + x * f + i].toInt() and 0xFF
                mask[y * n + x] = if (sum >= 128 * f * f) 1 else 0
            }
        }
        levels.add(Level(n, mask, FloatArray(n * n)))
        if (f == 4) break
        n = n shr 1
        f = f shl 1
    }
    fun sweep(level: Level, s: Float, iters: Int, om: Float) {
        val ln = level.n
        val mask = level.mask
        val phi = level.phi
        val s2 = s * s
        for (it in 0 until iters) {
            for (y in 1 until ln - 1) {
                val o = y * ln
                for (x in 1 until ln - 1) {
                    val i = o + x
                    if (mask[i].toInt() == 0) continue
                    val v = (phi[i - 1] + phi[i + 1] + phi[i - ln] + phi[i + ln] + s2) * 0.25f
                    phi[i] += om * (v - phi[i])
                }
            }
        }
    }
    for (l in levels.indices.reversed()) {
        val lev = levels[l]
        val lf = 1 shl l
        if (l < levels.size - 1) {
            // Bilinear prolongation from the coarser level.
            val c = levels[l + 1]
            val ln = lev.n
            val cn = c.n
            for (y in 0 until ln) {
                val fy = ((y + 0.5f) / 2 - 0.5f).coerceIn(0f, (cn - 1).toFloat())
                val y0 = fy.toInt()
                val y1 = min(cn - 1, y0 + 1)
                val ty = fy - y0
                for (x in 0 until ln) {
                    val i = y * ln + x
                    if (lev.mask[i].toInt() == 0) continue
                    val fx = ((x + 0.5f) / 2 - 0.5f).coerceIn(0f, (cn - 1).toFloat())
                    val x0 = fx.toInt()
                    val x1 = min(cn - 1, x0 + 1)
                    val tx = fx - x0
                    lev.phi[i] = (c.phi[y0 * cn + x0] * (1 - tx) + c.phi[y0 * cn + x1] * tx) * (1 - ty) +
                        (c.phi[y1 * cn + x0] * (1 - tx) + c.phi[y1 * cn + x1] * tx) * ty
                }
            }
        }
        val om = min(1.9f, (2 / (1 + sin(PI / lev.n)) - 0.05).toFloat())
        sweep(lev, u * lf, 4, 1f)
        sweep(lev, u * lf, if (l == 2) 100 else if (l == 1) 30 else 16, om)
        sweep(lev, u * lf, 8, 1f)
    }
    return levels[0].phi
}

private val Dx = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
private val Dy = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
private val Dl = floatArrayOf(1f, sqrt(2f), 1f, sqrt(2f), 1f, sqrt(2f), 1f, sqrt(2f))

/**
 * `buildForm`: the form of one outline from its coverage raster ([n] × [n], 0..255 over design
 * units [−Pad, 100 + Pad]). Outside texels near the edge borrow their nearest inside texel so the
 * upscaled texture never bleeds transparent black.
 */
internal fun buildForm(cov: ByteArray, n: Int, halfDepth: Float): BotForm {
    val u = Span.toFloat() / n
    val nn = n * n
    val mask = ByteArray(nn)
    for (i in 0 until nn) mask[i] = if ((cov[i].toInt() and 0xFF) >= 128) 1 else 0

    // Signed distance to the outline in design units, positive inside.
    val dIn = FloatArray(nn)
    val dOut = FloatArray(nn)
    val nearIn = IntArray(nn)
    edt2d(mask, 0, n, dIn, null)
    edt2d(mask, 1, n, dOut, nearIn)
    val sd = FloatArray(nn)
    val tmp = FloatArray(nn)
    for (i in 0 until nn) {
        val a = (cov[i].toInt() and 0xFF) / 255f
        sd[i] = u * if (a > 0 && a < 1) a - 0.5f else if (mask[i].toInt() != 0) sqrt(dIn[i]) - 0.5f else 0.5f - sqrt(dOut[i])
    }
    // Round the medial-axis crease into a ridge.
    blur5(sd, n, tmp)
    blur5(sd, n, tmp)

    // The pillow: √φ of the torsion function, scaled so its height follows the inradius.
    val phi = poisson(cov, n, u)
    var phiMax = 0f
    for (i in 0 until nn) if (phi[i] > phiMax) phiMax = phi[i]
    val rIn = 2 * sqrt(phiMax)
    val hMax = min(0.9f * halfDepth + 0.12f * rIn, 1.2f * rIn)
    val kh = if (phiMax > 0) hMax / sqrt(phiMax) else 0f
    val h = FloatArray(nn)
    for (i in 0 until nn) h[i] = if (phi[i] > 0) kh * sqrt(phi[i]) else 0f
    blur5(h, n, tmp)

    // Normals, the silhouette fix, horizon AO, matcap cells.
    val i00 = ShortArray(nn)
    val wx = ByteArray(nn)
    val wy = ByteArray(nn)
    val ao = ByteArray(nn)
    val steps = if (n <= 64) intArrayOf(1, 2, 3, 5, 8) else if (n <= 96) intArrayOf(1, 2, 4, 7, 11) else intArrayOf(1, 2, 4, 7, 11, 15)
    val halo = 9f
    val last = n - 1
    for (y in 0 until n) {
        for (x in 0 until n) {
            val i = y * n + x
            val src = if (mask[i].toInt() != 0) i else if (dOut[i] <= halo) nearIn[i] else -1
            if (src < 0) continue
            val sx = src % n
            val sy = (src - sx) / n
            val xl = if (sx > 0) sx - 1 else 0
            val xr = if (sx < last) sx + 1 else last
            val yu = if (sy > 0) sy - 1 else 0
            val yd = if (sy < last) sy + 1 else last
            var nx = -(h[sy * n + xr] - h[sy * n + xl]) / (2 * u)
            var ny = -(h[yd * n + sx] - h[yu * n + sx]) / (2 * u)
            var nz = 1f
            var len = sqrt(nx * nx + ny * ny + 1)
            nx /= len; ny /= len; nz /= len
            val dd = max(0f, sd[src])
            if (dd < 2) {
                // Toward the in-plane outward direction, so the silhouette grazes.
                var gx = sd[sy * n + xr] - sd[sy * n + xl]
                var gy = sd[yd * n + sx] - sd[yu * n + sx]
                val gl = hypot(gx, gy).takeIf { it != 0f } ?: 1f
                gx /= gl; gy /= gl
                val w = 0.7f * (1 - dd / 2)
                nx += w * (-gx - nx); ny += w * (-gy - ny); nz += w * (0 - nz)
                len = sqrt(nx * nx + ny * ny + nz * nz).takeIf { it != 0f } ?: 1f
                nx /= len; ny /= len; nz /= len
            }
            // Horizon-based occlusion on the height field.
            val h0 = h[src]
            var occ = 0f
            for (d in 0 until 8) {
                var m = 0f
                for (r in steps) {
                    val qx = (sx + Dx[d] * r).coerceIn(0, last)
                    val qy = (sy + Dy[d] * r).coerceIn(0, last)
                    val t = (h[qy * n + qx] - h0) / (r * u * Dl[d])
                    if (t > m) m = t
                }
                occ += m / sqrt(1 + m * m)
            }
            val e = 1 - min(1f, dd / 3)
            val edge = 1 - 0.2f * e * e
            val aoLin = (1 - 0.9f * occ / 8).toDouble().pow(1.5).toFloat() * edge
            ao[i] = max(1, (255 * aoLin.toDouble().pow(1 / 2.2)).roundToInt()).toByte()
            val fx = (nx * 0.5f + 0.5f) * (McSize - 1)
            val fy = (ny * 0.5f + 0.5f) * (McSize - 1)
            val cx = min(McSize - 2, max(0, fx.toInt()))
            val cy = min(McSize - 2, max(0, fy.toInt()))
            i00[i] = (cy * McSize + cx).toShort()
            wx[i] = (255 * (fx - cx).coerceIn(0f, 1f)).roundToInt().toByte()
            wy[i] = (255 * (fy - cy).coerceIn(0f, 1f)).roundToInt().toByte()
        }
    }
    return BotForm(n, i00, wx, wy, ao)
}
