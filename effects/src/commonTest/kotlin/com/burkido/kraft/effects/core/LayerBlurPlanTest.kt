package com.burkido.kraft.effects.core

import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/** The layer pyramid's depth and first-step scale reproduce the σ asked for. */
class LayerBlurPlanTest {
    private val sigmas = listOf(1f, 1.3f, 2f, 3.7f, 6f, 8f, 15.75f, 31.9f, 48f, 90f)

    @Test
    fun plainChainHitsSigma() {
        for (sigma in sigmas) {
            val (n, s0) = LayerBlur.plan(sigma)
            assertTrue(n >= 1 && s0 in 1f..2f, "σ $sigma → n $n, s₀ $s0")
            // σ ≈ s₀·2ⁿ⁻¹ for n ≥ 1 halvings.
            assertTrue(abs(s0 * 2f.pow(n - 1) - sigma) < 1e-3f * sigma, "σ $sigma → ${s0 * 2f.pow(n - 1)}")
        }
    }

    @Test
    fun isotropicChainHitsSigma() {
        for (sigma in sigmas.filter { it >= 2f }) {
            val (n, s0) = LayerBlur.planIsotropic(sigma)
            assertTrue(n >= 1 && s0 in 1f..2f, "σ $sigma → n $n, s₀ $s0")
            assertTrue(abs(0.968f * s0 * 2f.pow(n) - sigma) < 1e-3f * sigma, "σ $sigma → ${0.968f * s0 * 2f.pow(n)}")
        }
    }

    @Test
    fun subPixelSigmaUsesOneScaledStep() {
        val (n, s0) = LayerBlur.plan(0.7f)
        assertTrue(n == 0 && s0 in 1f..2f)
    }
}
