package com.burkido.kraft.effects.orbs

/**
 * One rendered instant of an orb, in reusable arrays so the render loop allocates nothing.
 *
 * Dots use stride 6 (x, y, z, r, white, a) and lines stride 7 (x1, y1, x2, y2, white, a, w) —
 * the upstream golden-vector layout. After [finalize] the dots are culled, radius-clamped and
 * z-sorted, so drawing them in order reproduces the web painter.
 */
class OrbFrame {
    var dots = DoubleArray(6 * 256)
        private set
    var lines = DoubleArray(7 * 64)
        private set
    var dotCount = 0
        private set
    var lineCount = 0
        private set

    private var order = IntArray(256)
    private var scratch = IntArray(256)
    private var sortedDots = DoubleArray(6 * 256)

    internal fun clear() {
        dotCount = 0
        lineCount = 0
    }

    internal fun addDot(x: Double, y: Double, z: Double, r: Double, white: Double, a: Double = 1.0) {
        if ((dotCount + 1) * 6 > dots.size) dots = dots.copyOf(dots.size * 2)
        val o = dotCount * 6
        dots[o] = x
        dots[o + 1] = y
        dots[o + 2] = z
        dots[o + 3] = r
        dots[o + 4] = white
        dots[o + 5] = a
        dotCount++
    }

    internal fun addLine(x1: Double, y1: Double, x2: Double, y2: Double, white: Double, a: Double, w: Double) {
        if ((lineCount + 1) * 7 > lines.size) lines = lines.copyOf(lines.size * 2)
        val o = lineCount * 7
        lines[o] = x1
        lines[o + 1] = y1
        lines[o + 2] = x2
        lines[o + 3] = y2
        lines[o + 4] = white
        lines[o + 5] = a
        lines[o + 6] = w
        lineCount++
    }

    /**
     * `finalizeFrame()`: drop marks with a < 0.02, clamp radii to [rMin], and stable-sort dots
     * far → near (JS `Array.prototype.sort` is stable, so equal depths keep emission order).
     */
    internal fun finalize(rMin: Double) {
        var kept = 0
        for (i in 0 until dotCount) {
            val o = i * 6
            if (dots[o + 5] < 0.02) continue
            if (dots[o + 3] < rMin) dots[o + 3] = rMin
            if (kept >= order.size) {
                order = order.copyOf(order.size * 2)
                scratch = IntArray(order.size)
            }
            order[kept++] = i
        }
        mergeSortByZ(kept)
        if (sortedDots.size < kept * 6) sortedDots = DoubleArray(dots.size)
        for (k in 0 until kept) dots.copyInto(sortedDots, k * 6, order[k] * 6, order[k] * 6 + 6)
        val swap = dots
        dots = sortedDots
        sortedDots = swap
        dotCount = kept

        var keptLines = 0
        for (i in 0 until lineCount) {
            val o = i * 7
            if (lines[o + 5] < 0.02) continue
            if (keptLines != i) lines.copyInto(lines, keptLines * 7, o, o + 7)
            keptLines++
        }
        lineCount = keptLines
    }

    /** Bottom-up stable merge sort of `order[0 until n]` by each dot's z. */
    private fun mergeSortByZ(n: Int) {
        if (scratch.size < order.size) scratch = IntArray(order.size)
        var src = order
        var dst = scratch
        var width = 1
        while (width < n) {
            var lo = 0
            while (lo < n) {
                val mid = minOf(lo + width, n)
                val hi = minOf(lo + 2 * width, n)
                var i = lo
                var j = mid
                var k = lo
                while (i < mid && j < hi) {
                    // `<=` keeps the left run first on ties: stability.
                    if (dots[src[i] * 6 + 2] <= dots[src[j] * 6 + 2]) dst[k++] = src[i++] else dst[k++] = src[j++]
                }
                while (i < mid) dst[k++] = src[i++]
                while (j < hi) dst[k++] = src[j++]
                lo += 2 * width
            }
            val t = src
            src = dst
            dst = t
            width *= 2
        }
        if (src !== order) src.copyInto(order, 0, 0, n)
    }
}
