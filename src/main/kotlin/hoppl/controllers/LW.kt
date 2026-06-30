package hoppl.controllers

import hoppl.distributions.softmax
import hoppl.interpreter.*
import java.util.*

fun runLikelihoodWeighting(program: String, rng: Random, n: Int): Pair<List<HVal>, DoubleArray> {
    val values = ArrayList<HVal>(n)
    val logW = DoubleArray(n)
    for (i in 0 until n) {
        val (value, w) = likelihoodWeighting(initialMachine(program, rng))
        values.add(value)
        logW[i] = w
    }
    return values to softmax(logW)
}

fun likelihoodWeighting(m: M): Pair<HVal, Double> {
    val t = run(m)
    return t.value to t.o.values.sum()
}
