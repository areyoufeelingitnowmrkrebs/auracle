package io.github.areyoufeelingitnowmrkrebs.auracle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AuracleMatrixSimulationTest {

    @Test
    fun `resize computes expected grid dimensions and hue gradient`() {
        val sim = AuracleMatrixSimulation(Random(42))
        sim.resize(widthPx = 100, heightPx = 50, cellSizePx = 14f)

        // ceil(100 / 14) = 8, ceil(50 / 14) = 4
        assertEquals(8, sim.columns)
        assertEquals(4, sim.rows)
        assertEquals(32, sim.totalCells)

        // targetHue = (x * 45 + y * 45) % 360
        assertEquals(0, sim.targetHues[0]) // (0, 0)
        assertEquals(45, sim.targetHues[1]) // (1, 0)
        assertEquals(45, sim.targetHues[8]) // (0, 1)
        assertEquals(90, sim.targetHues[9]) // (1, 1)
        assertEquals(14f, sim.xPixels[1], 0.001f)
        assertEquals(14f, sim.yPixels[8], 0.001f)
    }

    @Test
    fun `resize with zero dimensions resets grid cleanly`() {
        val sim = AuracleMatrixSimulation(Random(42))
        sim.resize(widthPx = 100, heightPx = 100, cellSizePx = 14f)
        assertTrue(sim.totalCells > 0)

        sim.resize(widthPx = 0, heightPx = 100, cellSizePx = 14f)
        assertEquals(0, sim.columns)
        assertEquals(0, sim.rows)
        assertEquals(0, sim.totalCells)

        // Should no-op safely without exceptions
        var drawn = 0
        sim.stepAndRender(16f) { _, _, _, _, _ -> drawn++ }
        assertEquals(0, drawn)
    }

    @Test
    fun `stepAndRender activates cells, cycles hue, and decays alpha`() {
        val sim = AuracleMatrixSimulation(Random(123))
        sim.resize(widthPx = 140, heightPx = 140, cellSizePx = 14f)

        var totalDrawnOverTime = 0
        repeat(150) {
            sim.stepAndRender(50f) { charIndex, hue, alphaIndex, _, _ ->
                assertTrue(charIndex in AuracleMatrixSimulation.CHARACTER_STRINGS.indices)
                assertTrue(hue in 0 until AuracleMatrixSimulation.HUE_DEGREES)
                assertTrue(alphaIndex in 0..AuracleMatrixSimulation.ALPHA_STEPS)
                totalDrawnOverTime++
            }
        }

        assertTrue("Expected matrix drops to enter viewport and draw cells", totalDrawnOverTime > 0)
        assertTrue("Expected global hue to advance", sim.hue > 0f)
    }

    @Test
    fun `dt clamping protects against large pause spikes`() {
        val sim = AuracleMatrixSimulation(Random(7))
        sim.resize(widthPx = 140, heightPx = 140, cellSizePx = 14f)

        // dt > 500ms clamps to 16ms -> hue advances by 3 * (16 / 100) = 0.48
        sim.stepAndRender(10_000f) { _, _, _, _, _ -> }
        assertEquals(0.48f, sim.hue, 0.001f)
    }

    @Test
    fun `decayRateForSpeed doubles shortest trail length while preserving max trail length`() {
        val sim = AuracleMatrixSimulation(Random(1))
        val minDecay = sim.decayRateForSpeed(AuracleMatrixSimulation.MIN_SPEED)
        val maxDecay = sim.decayRateForSpeed(AuracleMatrixSimulation.MAX_SPEED)

        val minTrailCells = 0.99f * AuracleMatrixSimulation.MIN_SPEED / minDecay
        val maxTrailCells = 0.99f * AuracleMatrixSimulation.MAX_SPEED / maxDecay

        assertEquals(19.8f, minTrailCells, 0.01f)
        assertEquals(39.6f, maxTrailCells, 0.01f)
    }
}
