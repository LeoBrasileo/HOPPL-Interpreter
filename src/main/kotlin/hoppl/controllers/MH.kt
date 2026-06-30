package hoppl.controllers

import hoppl.Trace
import hoppl.run
import hoppl.interpreter.Address
import hoppl.interpreter.initialMachine
import java.util.Random
import kotlin.math.ln


fun mhLogAlpha(cur: Trace, prop: Trace, a0: Address): Double {
    val fwd = setOf(a0) + (prop.x.keys - cur.x.keys) // resampled and newly required sites
    val rev = setOf(a0) + (cur.x.keys - prop.x.keys) // resampled and dropped sites

    val num = prop.s.filterKeys { it !in fwd }.values.sum() + prop.o.values.sum()
    val den = cur.s.filterKeys { it !in rev }.values.sum() + cur.o.values.sum()

    return (ln(cur.x.size.toDouble()) - ln(prop.x.size.toDouble())) + (num - den)
}

fun runSingleSiteMH(program: String, rng: Random, steps: Int, warmup: Int = 2000): DoubleArray {
    var cur = run(initialMachine(program, rng))

    if (cur.x.isEmpty()) return DoubleArray(steps) { cur.value.toDouble() }

    val chain = ArrayList<Double>(steps)
    for (i in 0 until steps + warmup) {
        val addrs = cur.x.keys.toList()
        val a0 = addrs[rng.nextInt(addrs.size)]

        val prop = run(initialMachine(program, rng), a0, cur.x)
        if (ln(rng.nextDouble()) < mhLogAlpha(cur, prop, a0)) {
            cur = prop
        }

        if (i >= warmup) chain.add(cur.value.toDouble())
    }
    return chain.toDoubleArray()
}
