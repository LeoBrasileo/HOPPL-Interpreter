package hoppl.controllers

import hoppl.interpreter.HVal
import hoppl.interpreter.M
import hoppl.interpreter.StepResult
import hoppl.interpreter.initialMachine
import hoppl.logProb
import hoppl.interpreter.resume
import hoppl.interpreter.send
import hoppl.interpreter.softmax
import java.util.Random

fun advance(m: M): StepResult {
    var msg = resume(m)
    while (msg is StepResult.Sample) {
        send(msg.m, msg.dist.toDist().sample(msg.m.rng))
        msg = resume(msg.m)
    }
    return msg
}

private fun sampleCategorical(rng: Random, probs: DoubleArray): Int {
    var r = rng.nextDouble()
    for (i in probs.indices) {
        r -= probs[i]
        if (r <= 0.0) return i
    }
    return probs.size - 1
}

fun runSMC(program: String, n: Int, seed: Long): List<HVal> {
    val master = Random(seed)
    return runSMC(program, List(n) { Random(master.nextLong()) }, n)
}

/**
 * Run Sequential Monte Carlo with [n] particles, one rng per particle.
 */
fun runSMC(program: String, rngs: List<Random>, n: Int): List<HVal> {
    var particles = List(n) { initialMachine(program, rngs[it]) }
    while (true) {
        val messages = particles.map { advance(it) }

        if (messages.all { it is StepResult.Done })
            return messages.map { (it as StepResult.Done).value }

        if (!messages.all { it is StepResult.Observe })
            throw DivergentBreakpointException("particles reached different breakpoints")

        val logInc = DoubleArray(n)
        val paused = ArrayList<M>(n)
        for ((k, msg) in messages.withIndex()) {
            msg as StepResult.Observe
            val lp = logProb(msg.dist, msg.value)
            msg.m.logW += lp
            logInc[k] = lp
            send(msg.m, msg.value)
            paused.add(msg.m)
        }

        // Multinomial resampling
        val probs = softmax(logInc)
        val ancestors = IntArray(n) { sampleCategorical(rngs[0], probs) }
        particles = List(n) { j -> paused[ancestors[j]].fork(rngs[j]) }
    }
}

/** SMC requires every trace to share the same observe sequence */
class DivergentBreakpointException(message: String) : RuntimeException(message)