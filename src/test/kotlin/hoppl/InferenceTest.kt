package hoppl

import hoppl.controllers.DivergentBreakpointException
import hoppl.controllers.runLikelihoodWeighting
import hoppl.controllers.likelihoodWeighting
import hoppl.controllers.runSMC
import hoppl.controllers.runSingleSiteMH
import hoppl.interpreter.HVal
import hoppl.interpreter.hInt
import hoppl.interpreter.initialMachine
import java.util.Random
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    private val conj = """
        (let [mu (sample (normal 0 1))]
          (observe (normal mu 1) 2.3)
          mu)
    """.trimIndent()

    private val bits: String = buildString {
        append("(let [")
        append((1..8).joinToString(" ") { "b$it (if (sample (bernoulli 0.5)) 1 0)" })
        append(" total (+ ")
        append((1..8).joinToString(" ") { "b$it" })
        append(")]")
        append(" (observe (normal 7 2) total) total)")
    }

    private fun weightedMean(values: List<HVal>, weights: DoubleArray): Double =
        values.indices.sumOf { weights[it] * values[it].toDouble() }
    private fun mean(values: List<HVal>): Double = values.sumOf { it.toDouble() } / values.size
    private fun mean(xs: DoubleArray): Double = xs.average()
    private fun std(xs: DoubleArray): Double {
        val m = xs.average()
        return sqrt(xs.sumOf { (it - m) * (it - m) } / xs.size)
    }

    @Test
    fun `likelihood weighting recovers the posterior mean`() {
        val (values, weights) = runLikelihoodWeighting(oneObservation, Random(42), 20_000)
        assertEquals(1.0, weightedMean(values, weights), 0.1)
    }

    @Test
    fun `a program without observe has zero log-weight`() {
        val (value, logW) = likelihoodWeighting(initialMachine("(+ 1 2)", Random(0)))
        assertEquals(hInt(3), value)
        assertEquals(0.0, logW)
    }

    @Test
    fun `likelihood weighting estimates the geometric mean`() {
        // E[k] = (1 - p) / p = 0.7 / 0.3
        val geom = "(defn geom [] (if (sample (bernoulli 0.3)) 0 (+ 1 (geom)))) (geom)"
        val rng = Random(42)
        val n = 200_000
        var sum = 0.0
        repeat(n) { sum += likelihoodWeighting(initialMachine(geom, rng)).first.toDouble() }
        assertEquals(0.7 / 0.3, sum / n, 0.05)
    }

    @Test
    fun `smc recovers the posterior mean for a single observe`() {
        val values = runSMC(oneObservation, n = 4_000, seed = 1)
        assertEquals(1.0, mean(values), 0.2)
    }

    @Test
    fun `smc recovers the posterior mean across an observe sequence`() {
        val values = runSMC(twoObservations, n = 4_000, seed = 100)
        assertEquals(5.0 / 3.0, mean(values), 0.2)
    }

    @Test
    fun `single-site MH recovers the conjugate posterior mean and std`() {
        val chain = runSingleSiteMH(conj, Random(420), 60_000, warmup = 3_000)
        assertEquals(1.15, mean(chain), 0.05)
        assertEquals(sqrt(0.5), std(chain), 0.05)
    }

    @Test
    fun `single-site MH on a bits program matches the exact posterior`() {
        val n = 8.0
        val w = DoubleArray(9) { k ->
            // Binomial
            val z = (k - 7.0) / 2.0
            var c = 1.0
            for (i in 1..k) c = c * (n - k + i) / i
            c * exp(-0.5 * z * z)
        }
        val exact = (0..8).sumOf { it * w[it] } / w.sum()

        val chain = runSingleSiteMH(bits, Random(), 80_000, warmup = 3_000)
        assertEquals(exact, mean(chain), 0.05)
    }

    @Test
    fun `smc breaks when traces stop at different breakpoints`() {
        val divergent = """
            (let [b (sample (flip 0.5))]
              (if b (observe (normal 0 1) 0) 0)
              b)
        """.trimIndent()

        val ex = assertFailsWith<DivergentBreakpointException> { runSMC(divergent, n = 64, seed = 1) }
        assertContains(ex.message ?: "", "different breakpoints")
    }

    @Test
    fun `LW SMC and SSMH match posterior`() {
        val (values, weights) = runLikelihoodWeighting(conj, Random(), 100_000)

        val lwMean = weightedMean(values, weights)
        val smcMean = mean(runSMC(conj, n = 20_000, seed = 4521))
        val ssmhMean = mean(runSingleSiteMH(conj, Random(540), 60_000, warmup = 3_000))

        assertEquals(1.15, lwMean, 0.05)
        assertEquals(1.15, smcMean, 0.05)
        assertEquals(1.15, ssmhMean, 0.05)
    }
}
