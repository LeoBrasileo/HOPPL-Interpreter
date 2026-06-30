package hoppl.interpreter

import java.util.Random
import kotlin.math.*

// It is important for this class to be sealed given that allow us to match all possible expressions on the evaluator run
sealed class HVal {
    data class HInt(val v: Long) : HVal()
    data class HFloat(val v: Double) : HVal()
    data class HBool(val v: Boolean) : HVal()
    data class HString(val v: String) : HVal()
    data class HVec(val v: List<HVal>) : HVal()
    data class HMap(val v: Map<HVal, HVal>) : HVal()
    data class HMatrix(val v: Array<DoubleArray>) : HVal()   // row-major 2-D
    data class HDist(val v: Distribution) : HVal()
    object HNil : HVal()

    fun toDouble(): Double = when (this) {
        is HInt -> v.toDouble()
        is HFloat -> v
        is HBool -> if (v) 1.0 else 0.0
        else -> error("expected number, got $this")
    }

    fun toLong(): Long = when (this) {
        is HInt -> v
        is HFloat -> v.toLong()
        is HBool -> if (v) 1L else 0L
        else -> error("expected integer, got $this")
    }

    fun toBool(): Boolean = when (this) {
        is HBool -> v
        is HNil -> false
        else -> true
    }

    fun toList(): List<HVal> = when (this) {
        is HVec -> v
        else -> error("expected vector, got $this")
    }

    fun toMap(): Map<HVal, HVal> = when (this) {
        is HMap -> v
        else -> error("expected hash-map, got $this")
    }

    fun toMatrix(): Array<DoubleArray> = when (this) {
        is HMatrix -> v
        is HVec -> arrayOf(v.map { it.toDouble() }.toDoubleArray())
        else -> error("expected matrix, got $this")
    }

    fun toDist(): Distribution = when (this) {
        is HDist -> v
        else -> error("expected distribution, got $this")
    }

    override fun toString(): String = when (this) {
        is HInt -> v.toString()
        is HFloat -> v.toString()
        is HBool -> if (v) "true" else "false"
        is HString -> "\"$v\""
        is HVec -> "[${v.joinToString(" ")}]"
        is HMap -> "{${v.entries.joinToString(", ") { "${it.key} ${it.value}" }}}"
        is HMatrix -> v.joinToString("\n") { row -> "[${row.joinToString(" ")}]" }
        is HDist -> v.toString()
        is HNil -> "nil"
        is Closure -> toString()
        is Primitive -> toString()
    }
}

// Convenience constructors
fun hInt(v: Long)  = HVal.HInt(v)
fun hInt(v: Int)  = HVal.HInt(v.toLong())
fun hFloat(v: Double) = HVal.HFloat(v)
fun hBool(v: Boolean) = HVal.HBool(v)
fun hVec(v: List<HVal>) = HVal.HVec(v)
fun hMap(v: Map<HVal, HVal>) = HVal.HMap(v)
fun hDist(d: Distribution) = HVal.HDist(d)
val HNil = HVal.HNil

/**
 * Base class for all probability distributions.
 *
 * [sample]       draw a value using a [Random]-based RNG
 * [logProb]      log density / log mass at x
 * [params]       unconstrained parameter vector
 * [withParams]   reconstruct from unconstrained vector
 * [gradLogProb]  delta log p(x) / delta unconstrained params
 */
abstract class Distribution {
    abstract val name: String
    abstract fun sample(rng: Random): HVal
    abstract fun logProb(x: HVal): Double

    open fun params(): DoubleArray =
        error("$name is not an optimizable guide")
    open fun withParams(theta: DoubleArray): Distribution =
        error("$name is not an optimizable guide")
    open fun gradLogProb(x: HVal): DoubleArray =
        error("$name is not an optimizable guide")
}

private fun sigmoid(x: Double) = 1.0 / (1.0 + exp(-x))

fun softmax(v: DoubleArray): DoubleArray {
    val m = v.max()
    val e = DoubleArray(v.size) { exp(v[it] - m) }
    val s = e.sum()
    return DoubleArray(v.size) { e[it] / s }
}

private const val LOG2PI = 1.8378770664093454

class Normal(val mu: Double, val sigma: Double) : Distribution() {
    init { require(sigma > 0) { "normal: sigma must be > 0" } }

    override val name = "normal"

    override fun sample(rng: Random): HVal =
        hFloat(mu + sigma * rng.nextGaussian())

    override fun logProb(x: HVal): Double {
        val z = (x.toDouble() - mu) / sigma
        return -0.5 * (LOG2PI + z * z) - ln(sigma)
    }

    override fun params() = doubleArrayOf(mu, ln(sigma))
    override fun withParams(theta: DoubleArray) = Normal(theta[0], exp(theta[1]))
    override fun gradLogProb(x: HVal): DoubleArray {
        val z = (x.toDouble() - mu) / sigma
        return doubleArrayOf(z / sigma, z * z - 1.0)
    }

    override fun toString() = "(normal $mu $sigma)"
}

class LogNormal(val mu: Double, val sigma: Double) : Distribution() {
    init { require(sigma > 0) { "log-normal: sigma must be > 0" } }

    override val name = "log-normal"

    override fun sample(rng: Random): HVal =
        hFloat(exp(mu + sigma * rng.nextGaussian()))

    override fun logProb(x: HVal): Double {
        val xd = x.toDouble()
        if (xd <= 0.0) return Double.NEGATIVE_INFINITY
        val z = (ln(xd) - mu) / sigma
        return -0.5 * (LOG2PI + z * z) - ln(sigma) - ln(xd)
    }

    override fun params() = doubleArrayOf(mu, ln(sigma))
    override fun withParams(theta: DoubleArray) = LogNormal(theta[0], exp(theta[1]))
    override fun gradLogProb(x: HVal): DoubleArray {
        val z = (ln(x.toDouble()) - mu) / sigma
        return doubleArrayOf(z / sigma, z * z - 1.0)
    }

    override fun toString() = "(log-normal $mu $sigma)"
}

class Uniform(val a: Double, val b: Double) : Distribution() {
    init { require(b > a) { "uniform-continuous: requires b > a" } }

    override val name = "uniform-continuous"

    override fun sample(rng: Random): HVal =
        hFloat(a + rng.nextDouble() * (b - a))

    override fun logProb(x: HVal): Double {
        val xd = x.toDouble()
        return if (xd in a..b) -ln(b - a) else Double.NEGATIVE_INFINITY
    }

    override fun toString() = "(uniform-continuous $a $b)"
}

class Exponential(val rate: Double) : Distribution() {
    init { require(rate > 0) { "exponential: rate must be > 0" } }

    override val name = "exponential"

    override fun sample(rng: Random): HVal =
        hFloat(-ln(rng.nextDouble()) / rate)

    override fun logProb(x: HVal): Double {
        val xd = x.toDouble()
        return if (xd < 0.0) Double.NEGATIVE_INFINITY
        else ln(rate) - rate * xd
    }

    override fun toString() = "(exponential $rate)"
}

class Beta(val alpha: Double, val beta: Double) : Distribution() {
    init {
        require(alpha > 0 && beta > 0) { "beta: parameters must be > 0" }
    }

    override val name = "beta"

    // Box-Muller method
    override fun sample(rng: Random): HVal {
        val ga = sampleGamma(rng, alpha)
        val gb = sampleGamma(rng, beta)
        return hFloat(ga / (ga + gb))
    }

    override fun logProb(x: HVal): Double {
        val xd = x.toDouble()
        if (xd <= 0.0 || xd >= 1.0) return Double.NEGATIVE_INFINITY
        val logB = lgamma(alpha) + lgamma(beta) - lgamma(alpha + beta)
        return (alpha - 1) * ln(xd) + (beta - 1) * ln(1.0 - xd) - logB
    }

    override fun toString() = "(beta $alpha $beta)"
}

class Gamma(val shape: Double, val rate: Double) : Distribution() {
    init { require(shape > 0 && rate > 0) { "gamma: parameters must be > 0" } }

    override val name = "gamma"

    override fun sample(rng: Random): HVal =
        hFloat(sampleGamma(rng, shape) / rate)

    override fun logProb(x: HVal): Double {
        val xd = x.toDouble()
        if (xd <= 0.0) return Double.NEGATIVE_INFINITY
        return shape * ln(rate) - lgamma(shape) + (shape - 1) * ln(xd) - rate * xd
    }

    override fun toString() = "(gamma $shape $rate)"
}

class Poisson(val lam: Double) : Distribution() {
    init { require(lam > 0) { "poisson: rate must be > 0" } }

    override val name = "poisson"

    override fun sample(rng: Random): HVal {
        var k = 0
        var p = rng.nextDouble()
        val target = exp(-lam)
        while (p > target) { p *= rng.nextDouble(); k++ }
        return hInt(k.toLong())
    }

    override fun logProb(x: HVal): Double {
        val k = x.toLong()
        if (k < 0) return Double.NEGATIVE_INFINITY
        return k * ln(lam) - lam - lgamma((k + 1).toDouble())
    }

    override fun toString() = "(poisson $lam)"
}

class Bernoulli(val p: Double) : Distribution() {
    init { require(p in 0.0..1.0) { "flip: p must be in [0,1]" } }

    override val name = "flip"

    override fun sample(rng: Random): HVal =
        hBool(rng.nextDouble() < p)

    override fun logProb(x: HVal): Double {
        val xb = x.toBool()
        return if (xb) {
            if (p > 0) ln(p) else Double.NEGATIVE_INFINITY
        } else {
            if (p < 1) ln(1.0 - p) else Double.NEGATIVE_INFINITY
        }
    }

    override fun params(): DoubleArray {
        val pc = p.coerceIn(1e-12, 1.0 - 1e-12)
        return doubleArrayOf(ln(pc / (1.0 - pc)))
    }
    override fun withParams(theta: DoubleArray) = Bernoulli(sigmoid(theta[0]))
    override fun gradLogProb(x: HVal): DoubleArray {
        val indicator = if (x.toBool()) 1.0 else 0.0
        return doubleArrayOf(indicator - p)
    }

    override fun toString() = "(flip $p)"
}

class Discrete(probs: List<Double>) : Distribution() {
    val probs: DoubleArray

    init {
        val arr = probs.toDoubleArray()
        require(arr.all { it >= 0 } && arr.sum() > 0) { "discrete: invalid probability vector" }
        val s = arr.sum()
        this.probs = DoubleArray(arr.size) { arr[it] / s }
    }

    override val name = "discrete"

    override fun sample(rng: Random): HVal {
        var r = rng.nextDouble()
        for (i in probs.indices) {
            r -= probs[i]
            if (r <= 0.0) return hInt(i.toLong())
        }
        return hInt((probs.size - 1).toLong())
    }

    override fun logProb(x: HVal): Double {
        val k = x.toLong().toInt()
        return if (k in probs.indices && probs[k] > 0) ln(probs[k])
        else Double.NEGATIVE_INFINITY
    }

    override fun params() = DoubleArray(probs.size) { ln(probs[it].coerceAtLeast(1e-12)) }
    override fun withParams(theta: DoubleArray) = Discrete(softmax(theta).toList())
    override fun gradLogProb(x: HVal): DoubleArray {
        val k = x.toLong().toInt()
        return DoubleArray(probs.size) { i -> (if (i == k) 1.0 else 0.0) - probs[i] }
    }

    override fun toString() = "(discrete ${probs.toList()})"
}

class UniformDiscrete(val lo: Int, val hi: Int) : Distribution() {
    init { require(hi > lo) { "uniform-discrete: requires hi > lo" } }

    override val name = "uniform-discrete"

    override fun sample(rng: Random): HVal =
        hInt((lo + rng.nextInt(hi - lo)).toLong())

    override fun logProb(x: HVal): Double {
        val k = x.toLong().toInt()
        return if (k in lo until hi) -ln((hi - lo).toDouble())
        else Double.NEGATIVE_INFINITY
    }

    override fun toString() = "(uniform-discrete $lo $hi)"
}

class Dirichlet(val alphas: DoubleArray) : Distribution() {
    init { require(alphas.all { it > 0 }) { "dirichlet: alphas must be > 0" } }

    override val name = "dirichlet"

    override fun sample(rng: Random): HVal {
        val gs = DoubleArray(alphas.size) { sampleGamma(rng, alphas[it]) }
        val s = gs.sum()
        return hVec(gs.map { hFloat(it / s) })
    }

    override fun logProb(x: HVal): Double {
        val xs = x.toList().map { it.toDouble() }.toDoubleArray()
        if (xs.size != alphas.size || xs.any { it <= 0 }) return Double.NEGATIVE_INFINITY
        val logB = alphas.sumOf { lgamma(it) } - lgamma(alphas.sum())
        return xs.indices.sumOf { (alphas[it] - 1) * ln(xs[it]) } - logB
    }

    override fun toString() = "(dirichlet ${alphas.toList()})"
}

private fun sampleGamma(rng: Random, shape: Double): Double {
    if (shape < 1.0) {
        return sampleGamma(rng, shape + 1.0) * rng.nextDouble().pow(1.0 / shape)
    }
    val d = shape - 1.0 / 3.0
    val c = 1.0 / sqrt(9.0 * d)
    while (true) {
        var x: Double
        var v: Double
        do {
            x = rng.nextGaussian()
            v = 1.0 + c * x
        } while (v <= 0.0)
        v = v * v * v
        val u = rng.nextDouble()
        if (u < 1.0 - 0.0331 * (x * x) * (x * x)) return d * v
        if (ln(u) < 0.5 * x * x + d * (1.0 - v + ln(v))) return d * v
    }
}

private fun lgamma(x: Double): Double = ln(gamma(x))

private fun gamma(x: Double): Double {
    if (x < 0.5) return Math.PI / (sin(Math.PI * x) * gamma(1.0 - x))
    val n = x - 1.0
    val g = 7
    val c = doubleArrayOf(
        0.99999999999980993,
        676.5203681218851, -1259.1392167224028,
        771.32342877765313, -176.61502916214059,
        12.507343278686905, -0.13857109526572012,
        9.9843695780195716e-6, 1.5056327351493116e-7
    )
    var x2 = n
    var sum = c[0]
    for (i in 1..g + 1) sum += c[i] / (x2 + i)
    val t = x2 + g + 0.5
    return sqrt(2.0 * Math.PI) * t.pow(x2 + 0.5) * exp(-t) * sum
}

val DISTRIBUTIONS: Map<String, (List<HVal>) -> Distribution> = mapOf(
    "normal" to { a -> Normal(a[0].toDouble(), a[1].toDouble()) },
    "log-normal" to { a -> LogNormal(a[0].toDouble(), a[1].toDouble()) },
    "beta" to { a -> Beta(a[0].toDouble(), a[1].toDouble()) },
    "gamma" to { a -> Gamma(a[0].toDouble(), a[1].toDouble()) },
    "exponential" to { a -> Exponential(a[0].toDouble()) },
    "uniform-continuous" to { a -> Uniform(a[0].toDouble(), a[1].toDouble()) },
    "uniform" to { a -> Uniform(a[0].toDouble(), a[1].toDouble()) },
    "poisson" to { a -> Poisson(a[0].toDouble()) },
    "bernoulli" to { a -> Bernoulli(a[0].toDouble()) },
    "flip" to { a -> Bernoulli(a[0].toDouble()) },
    "discrete" to { a ->
        val probs = if (a.size == 1 && a[0] is HVal.HVec)
            (a[0] as HVal.HVec).v.map { it.toDouble() }
        else
            a.map { it.toDouble() }
        Discrete(probs)
    },
    "categorical" to { a ->
        val probs = if (a.size == 1 && a[0] is HVal.HVec)
            (a[0] as HVal.HVec).v.map { it.toDouble() }
        else
            a.map { it.toDouble() }
        Discrete(probs)
    },
    "uniform-discrete" to { a -> UniformDiscrete(a[0].toLong().toInt(), a[1].toLong().toInt()) },
    "dirichlet" to { a ->
        val alphas = if (a.size == 1 && a[0] is HVal.HVec)
            (a[0] as HVal.HVec).v.map { it.toDouble() }.toDoubleArray()
        else
            a.map { it.toDouble() }.toDoubleArray()
        Dirichlet(alphas)
    },
)

fun makeGuide(d: Distribution): Distribution = when (d) {
    is Normal -> Normal(d.mu, d.sigma)
    is LogNormal -> LogNormal(d.mu, d.sigma)
    is Gamma, is Exponential, is Beta -> LogNormal(0.0, 1.0)
    is Bernoulli -> Bernoulli(d.p)
    is Discrete -> Discrete(d.probs.toList())
    else -> error("no optimizable guide family for distribution ${d.name}")
}