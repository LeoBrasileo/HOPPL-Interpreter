package hoppl.controllers

import hoppl.interpreter.*

data class Trace(
    val value: HVal,
    val x: Map<Address, HVal>,
    val s: Map<Address, Double>,
    val o: Map<Address, Double> )

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
                o[msg.addr] = msg.dist.toDist().logProb(msg.value)
                send(msg.m, msg.value)
            }
            is StepResult.Done -> return Trace(msg.value, x, s, o)
        }
    }
}