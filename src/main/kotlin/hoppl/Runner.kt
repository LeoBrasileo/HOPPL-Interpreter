package hoppl

import hoppl.interpreter.Address
import hoppl.interpreter.HVal
import hoppl.interpreter.M
import hoppl.interpreter.StepResult
import hoppl.interpreter.initialMachine
import hoppl.interpreter.resume
import hoppl.interpreter.send
import hoppl.interpreter.softmax
import java.util.Random

fun logProb(dist: HVal, value: HVal): Double = dist.toDist().logProb(value)

fun likelihoodWeighting(program: String, rng: Random, n: Int): Pair<List<HVal>, DoubleArray> {
    val values = ArrayList<HVal>(n)
    val logW = DoubleArray(n)
    for (i in 0 until n) {
        val (value, w) = runLikelihoodWeighting(initialMachine(program, rng))
        values.add(value)
        logW[i] = w
    }
    return values to softmax(logW)
}

fun runLikelihoodWeighting(m: M): Pair<HVal, Double> {
    while (true) {
        when (val msg = resume(m)) {
            is StepResult.Done -> return msg.value to msg.m.logW
            is StepResult.Sample -> send(msg.m, msg.dist.toDist().sample(msg.m.rng))
            is StepResult.Observe -> {
                msg.m.logW += logProb(msg.dist, msg.value)
                send(msg.m, msg.value)
            }
        }
    }
}


data class Trace(
    val value: HVal,
    val x: Map<Address, HVal>,
    val s: Map<Address, Double>,
    val o: Map<Address, Double>
) {
    val logJoint: Double get() = s.values.sum() + o.values.sum()
}
fun run(m: M, x0: Address? = null, cache: Map<Address, HVal> = emptyMap()): Trace {
    val x = LinkedHashMap<Address, HVal>()
    val s = LinkedHashMap<Address, Double>()
    val o = LinkedHashMap<Address, Double>()
    while (true) {
        when (val msg = resume(m)) {
            is StepResult.Sample -> {
                val a = msg.addr
                val d = msg.dist.toDist()
                val value = if (a == x0 || a !in cache) d.sample(msg.m.rng) else cache.getValue(a)
                x[a] = value
                s[a] = d.logProb(value)
                send(msg.m, value)
            }
            is StepResult.Observe -> {
                o[msg.addr] = logProb(msg.dist, msg.value)
                send(msg.m, msg.value)
            }
            is StepResult.Done -> return Trace(msg.value, x, s, o)
        }
    }
}