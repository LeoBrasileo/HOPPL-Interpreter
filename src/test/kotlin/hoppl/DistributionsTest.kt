package hoppl

import hoppl.distributions.Bernoulli
import hoppl.distributions.DISTRIBUTIONS
import hoppl.distributions.Dirichlet
import hoppl.distributions.Discrete
import hoppl.distributions.Exponential
import hoppl.distributions.Gamma
import hoppl.interpreter.HVal
import hoppl.distributions.LogNormal
import hoppl.distributions.Normal
import hoppl.distributions.Poisson
import hoppl.distributions.Uniform
import hoppl.distributions.UniformDiscrete
import hoppl.interpreter.hBool
import hoppl.interpreter.hFloat
import hoppl.interpreter.hInt
import hoppl.interpreter.hVec
import hoppl.distributions.makeGuide
import kotlin.math.abs
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DistributionsTest {

    // make samples deterministic
    private val rng = java.util.Random(85484)

    private fun assertClose(expected: Double, actual: Double, tol: Double = 1e-9) {
        assertTrue(
            abs(expected - actual) < tol,
            "expected $expected but got $actual (tol=$tol)"
        )
    }

    @Test
    fun `Normal logProb at mean is correct`() {
        val n = Normal(0.0, 1.0)
        // log N(0 | 0, 1) = -0.5 * ln(2*pi)
        assertClose(-0.9189385332046727, n.logProb(hFloat(0.0)))
    }

    @Test
    fun `Normal logProb is symmetric`() {
        val n = Normal(0.0, 1.0)
        assertClose(n.logProb(hFloat(1.0)), n.logProb(hFloat(-1.0)))
    }

    @Test
    fun `Normal sample returns HFloat`() {
        val sample = Normal(0.0, 1.0).sample(rng)
        assertTrue(sample is HVal.HFloat)
    }

    @Test
    fun `Normal rejects non-positive sigma`() {
        assertFailsWith<IllegalArgumentException> { Normal(0.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { Normal(0.0, -1.0) }
    }

    @Test
    fun `Normal unconstrained params round-trip`() {
        val n = Normal(2.0, 3.0)
        val n2 = n.withParams(n.params())
        assertClose(n.mu,    n2.mu)
        assertClose(n.sigma, n2.sigma)
    }

    @Test
    fun `Bernoulli logProb of true`() {
        val b = Bernoulli(0.7)
        assertClose(ln(0.7), b.logProb(hBool(true)))
    }

    @Test
    fun `Bernoulli logProb of false`() {
        val b = Bernoulli(0.7)
        assertClose(ln(0.3), b.logProb(hBool(false)))
    }

    @Test
    fun `Bernoulli p=0 gives -Inf for true`() {
        val b = Bernoulli(0.0)
        assertEquals(Double.NEGATIVE_INFINITY, b.logProb(hBool(true)))
    }

    @Test
    fun `Bernoulli sample returns HBool`() {
        assertTrue(Bernoulli(0.5).sample(rng) is HVal.HBool)
    }

    @Test
    fun `Discrete normalises probabilities`() {
        val d = Discrete(listOf(1.0, 1.0, 2.0))
        assertClose(0.25, d.probs[0])
        assertClose(0.25, d.probs[1])
        assertClose(0.50, d.probs[2])
    }

    @Test
    fun `Discrete logProb is log of normalised prob`() {
        val d = Discrete(listOf(0.1, 0.3, 0.6))
        assertClose(ln(0.6), d.logProb(hInt(2)))
    }

    @Test
    fun `Discrete logProb out of range is -Inf`() {
        val d = Discrete(listOf(0.5, 0.5))
        assertEquals(Double.NEGATIVE_INFINITY, d.logProb(hInt(5)))
    }

    @Test
    fun `Discrete sample is within range`() {
        val d = Discrete(listOf(0.1, 0.3, 0.6))
        repeat(20) {
            val s = (d.sample(rng) as HVal.HInt).v
            assertTrue(s in 0..2)
        }
    }

    @Test
    fun `Uniform logProb inside support`() {
        val u = Uniform(0.0, 4.0)
        assertClose(-ln(4.0), u.logProb(hFloat(2.0)))
    }

    @Test
    fun `Uniform logProb outside support is -Inf`() {
        val u = Uniform(0.0, 1.0)
        assertEquals(Double.NEGATIVE_INFINITY, u.logProb(hFloat(2.0)))
        assertEquals(Double.NEGATIVE_INFINITY, u.logProb(hFloat(-1.0)))
    }

    @Test
    fun `Uniform rejects a ge b`() {
        assertFailsWith<IllegalArgumentException> { Uniform(1.0, 1.0) }
        assertFailsWith<IllegalArgumentException> { Uniform(2.0, 1.0) }
    }

    @Test
    fun `Exponential logProb at 1 with rate 1`() {
        val e = Exponential(1.0)
        assertClose(-1.0, e.logProb(hFloat(1.0)))
    }

    @Test
    fun `Exponential logProb negative x is -Inf`() {
        assertEquals(Double.NEGATIVE_INFINITY, Exponential(1.0).logProb(hFloat(-0.1)))
    }

    @Test
    fun `Gamma sample is positive`() {
        val g = Gamma(2.0, 1.0)
        repeat(20) {
            val s = (g.sample(rng) as HVal.HFloat).v
            assertTrue(s > 0.0)
        }
    }

    @Test
    fun `Gamma logProb negative x is -Inf`() {
        assertEquals(Double.NEGATIVE_INFINITY, Gamma(2.0, 1.0).logProb(hFloat(-1.0)))
    }

    @Test
    fun `Poisson sample is non-negative integer`() {
        val p = Poisson(3.0)
        repeat(20) {
            val s = (p.sample(rng) as HVal.HInt).v
            assertTrue(s >= 0)
        }
    }

    @Test
    fun `Poisson logProb negative k is -Inf`() {
        assertEquals(Double.NEGATIVE_INFINITY, Poisson(1.0).logProb(hInt(-1)))
    }

    @Test
    fun `UniformDiscrete sample within range`() {
        val u = UniformDiscrete(3, 7)
        repeat(20) {
            val s = (u.sample(rng) as HVal.HInt).v
            assertTrue(s in 3..6)
        }
    }

    @Test
    fun `UniformDiscrete logProb outside range is -Inf`() {
        val u = UniformDiscrete(0, 3)
        assertEquals(Double.NEGATIVE_INFINITY, u.logProb(hInt(3)))
        assertEquals(Double.NEGATIVE_INFINITY, u.logProb(hInt(-1)))
    }


    @Test
    fun `Dirichlet sample sums to 1`() {
        val d = Dirichlet(doubleArrayOf(1.0, 2.0, 3.0))
        repeat(10) {
            val s = (d.sample(rng) as HVal.HVec).v.sumOf { it.toDouble() }
            assertClose(1.0, s, tol = 1e-10)
        }
    }

    @Test
    fun `Dirichlet logProb rejects wrong dimension`() {
        val d = Dirichlet(doubleArrayOf(1.0, 1.0))
        assertEquals(Double.NEGATIVE_INFINITY, d.logProb(hVec(listOf(hFloat(1.0)))))
    }


    @Test
    fun `DISTRIBUTIONS table constructs Normal`() {
        val d = DISTRIBUTIONS["normal"]!!(listOf(hFloat(0.0), hFloat(1.0)))
        assertTrue(d is Normal)
    }

    @Test
    fun `DISTRIBUTIONS table constructs flip`() {
        val d = DISTRIBUTIONS["flip"]!!(listOf(hFloat(0.5)))
        assertTrue(d is Bernoulli)
    }

    @Test
    fun `makeGuide for Normal returns Normal`() {
        assertTrue(makeGuide(Normal(1.0, 2.0)) is Normal)
    }

    @Test
    fun `makeGuide for Bernoulli returns Bernoulli`() {
        assertTrue(makeGuide(Bernoulli(0.3)) is Bernoulli)
    }

    @Test
    fun `makeGuide for Gamma returns LogNormal`() {
        assertTrue(makeGuide(Gamma(1.0, 1.0)) is LogNormal)
    }
}