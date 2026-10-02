package com.burkido.kraft.effects.orbs

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The upstream golden vectors (spec/orbs-golden.json): 9 states × 2 sizes × 4 timestamps, every
 * dot and line the web engine emits, to 6 decimals.
 *
 * Like the Swift port's test, dots are compared as a multiset (tie groups at z ≈ 0 may legally
 * sort differently per libm) and draw order is checked as "z never decreases".
 */
class OrbGoldenTest {
    @Serializable
    private class Golden(val tolerance: Double, val resolved: Map<String, ResolvedJson>, val cases: List<Case>)

    @Serializable
    private class ResolvedJson(val mode: String, val speed: Double, val opts: Map<String, Double>)

    @Serializable
    private class Case(
        val key: String,
        val state: String,
        val size: Int,
        val t: Double,
        val dotCount: Int,
        val lineCount: Int,
        val dots: List<Double>,
        val lines: List<Double>,
    )

    private val golden: Golden by lazy {
        val text = javaClass.classLoader.getResource("orbs-golden.json")!!.readText()
        Json { ignoreUnknownKeys = true }.decodeFromString(Golden.serializer(), text)
    }

    private fun state(name: String) = OrbState.entries.first { it.name.equals(name, ignoreCase = true) }
    private fun size(px: Int) = OrbSize.entries.first { it.px == px }

    @Test
    fun presetsResolveLikeTheWeb() {
        for ((key, expected) in golden.resolved) {
            val (stateName, sizePx) = key.split("-")
            val actual = resolvePreset(state(stateName), size(sizePx.toInt()))
            assertEquals(expected.mode, actual.mode.name.lowercase(), key)
            assertEquals(expected.speed, actual.speed, 1e-12, key)
            assertEquals(expected.opts.keys, actual.opts.values.keys, "$key opt keys")
            for ((k, v) in expected.opts) assertEquals(v, actual.opts[k]!!, 1e-9, "$key.$k")
        }
    }

    @Test
    fun framesMatchGoldenVectors() {
        val tol = golden.tolerance
        val frame = OrbFrame()
        var compared = 0
        for (c in golden.cases) {
            val resolved = resolvePreset(state(c.state), size(c.size))
            buildFrame(resolved.mode, c.size.toDouble(), c.t, resolved.opts, frame)
            assertEquals(c.dotCount, frame.dotCount, "${c.key} dot count")
            assertEquals(c.lineCount, frame.lineCount, "${c.key} line count")

            for (i in 1 until frame.dotCount) {
                assertTrue(frame.dots[i * 6 + 2] >= frame.dots[(i - 1) * 6 + 2], "${c.key} z order at $i")
            }
            compareMultiset(c.key + " dots", 6, frame.dots, frame.dotCount, c.dots, tol)
            compareMultiset(c.key + " lines", 7, frame.lines, frame.lineCount, c.lines, tol)
            compared += c.dotCount
        }
        println("Orb golden vectors: ${golden.cases.size} cases, $compared dots matched within $tol")
    }

    private fun compareMultiset(label: String, stride: Int, actual: DoubleArray, count: Int, expected: List<Double>, tol: Double) {
        fun rows(get: (Int) -> Double, n: Int): List<DoubleArray> =
            List(n) { r -> DoubleArray(stride) { k -> get(r * stride + k) } }
        val comparator = Comparator<DoubleArray> { a, b ->
            for (k in 0 until stride) {
                val d = a[k] - b[k]
                if (abs(d) > tol) return@Comparator if (d < 0) -1 else 1
            }
            0
        }
        val mine = rows({ actual[it] }, count).map { r -> DoubleArray(stride) { k -> round6(r[k]) } }.sortedWith(comparator)
        val theirs = rows({ expected[it] }, expected.size / stride).sortedWith(comparator)
        for (i in mine.indices) {
            for (k in 0 until stride) {
                if (abs(mine[i][k] - theirs[i][k]) > tol) {
                    fail("$label row $i field $k: ${mine[i].toList()} vs ${theirs[i].toList()}")
                }
            }
        }
    }

    private fun round6(v: Double) = kotlin.math.round(v * 1e6) / 1e6
}
