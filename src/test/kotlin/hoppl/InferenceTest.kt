package hoppl

import hoppl.controllers.runSMC
import hoppl.interpreter.HVal
import hoppl.interpreter.hInt
import hoppl.interpreter.initialMachine
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Inference tests for probabilistic programs, use SMC and MH.
 */
class InferenceTest {

    // Prior mu ~ N(0,1), one observation y=2 from N(mu,1).
    // Posterior: N(mean = 1, var = 1/2).
    private val oneObservation = """
        (let [mu (sample (normal 0 1))]
          (observe (normal mu 1) 2)
          mu)
    """.trimIndent()

    // Prior mu ~ N(0,1), observations y=2 and y=3 from N(mu,1).
    // Posterior: N(mean = 5/3, var = 1/3).
    private val twoObservations = """
        (let [mu (sample (normal 0 1))]
          (observe (normal mu 1) 2)
          (observe (normal mu 1) 3)
          mu)
    """.trimIndent()

    private fun weightedMean(values: List<HVal>, weights: DoubleArray): Double =
        values.indices.sumOf { weights[it] * values[it].toDouble() }
    private fun mean(values: List<HVal>): Double =
        values.sumOf { it.toDouble() } / values.size

    @Test
    fun `likelihood weighting recovers the posterior mean`() {
        val (values, weights) = likelihoodWeighting(oneObservation, Random(42), 20_000)
        assertEquals(1.0, weightedMean(values, weights), 0.1)
    }

    @Test
    fun `a program without observe has zero log-weight`() {
        val (value, logW) = runLikelihoodWeighting(initialMachine("(+ 1 2)", Random(0)))
        assertEquals(hInt(3), value)
        assertEquals(0.0, logW)
    }

    @Test
    fun `smc recovers the posterior mean for a single observe`() {
        val n = 4_000
        val rngs = List(n) { Random((it + 1).toLong()) }
        val values = runSMC(oneObservation, rngs, n)
        assertEquals(1.0, mean(values), 0.2)
    }

    @Test
    fun `smc recovers the posterior mean across an observe sequence`() {
        val n = 4_000
        val rngs = List(n) { Random((it + 100).toLong()) }
        val values = runSMC(twoObservations, rngs, n)
        assertEquals(5.0 / 3.0, mean(values), 0.2)
    }
}
