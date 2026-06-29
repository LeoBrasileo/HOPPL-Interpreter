package hoppl.interpreter

import hoppl.Form
import java.util.Random

/**
 * Stack machine for the HOPPL interpreter.
 *
 * The control stack [C] and value stack [V] together form the continuation:
 *   - pausing = returning from [resume]
 *   - resuming = calling [resume] again
 *   - forking = copying both stacks
 */

/**
 * A first-class function value.
 *
 * [params] list of parameter names
 * [body] the unevaluated body forms (evaluated in sequence, last is the result)
 * [env] the lexical environment captured at the point of definition
 *
 * A [Closure] is itself an [HVal]
 */
class Closure(
    val params: List<String>,
    val body: List<Form>,
    val env: Map<String, HVal>,
) : HVal() {
    override fun toString() = "(fn [${params.joinToString(" ")}] ...)"
}

sealed class Frame

class M(
    C: List<Frame>,
    V: List<HVal> = emptyList(),
    env:  Map<String, HVal> = emptyMap(),
    val rng:  Random = Random(),
    var logW: Double = 0.0,
) {
    val C: ArrayDeque<Frame> = ArrayDeque(C)
    val V: ArrayDeque<HVal> = ArrayDeque(V)
    val env: MutableMap<String, HVal> = env.toMutableMap()

    fun fork(rng: Random? = null): M = M(
        C = C.toList(),
        V = V.toList(),
        env = env.toMap(),
        rng = rng ?: this.rng,
        logW = this.logW,
    )

    /** Push a frame onto the control stack. */
    fun pushC(frame: Frame) { C.addLast(frame) }

    /** Pop the next frame to execute. */
    fun popC(): Frame = C.removeLast()

    /** Push a value onto the value stack. */
    fun pushV(v: HVal) { V.addLast(v) }

    /** Pop the top value. */
    fun popV(): HVal = V.removeLast()

    /** Peek at the top value without removing it. */
    fun peekV(): HVal = V.last()

    /** Bind [name] -> [value] in the current environment. */
    fun bind(name: String, value: HVal) { env[name] = value }

    /** Look up [name], walking up via lexical scope stored in [env]. */
    fun lookup(name: String): HVal = env[name] ?: error("unbound variable: $name")

    override fun toString(): String = "M(C=${C.size} frames, V=${V.size} values, logW=$logW)"
}