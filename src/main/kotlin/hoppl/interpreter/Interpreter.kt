package hoppl.interpreter

import hoppl.Form
import hoppl.Symbol
import hoppl.parse
import java.util.Random

typealias Address = List<Any?>

private fun Address.ext(vararg parts: Any?): Address = this + parts.asList()

/** A built-in deterministic function, wrapped so it can live on the value stack as an [HVal]. */
class Primitive(val name: String, val fn: Prim) : HVal() {
    override fun toString() = "(primitive $name)"
}

class NameError(name: String) : Exception("unbound variable: $name")

/** Evaluate [expr] in [env] */
private class Ev(val expr: Form, val env: Map<String, HVal>, val addr: Address) : Frame()

private object Discard : Frame()

private class LetK(
    val binds: List<Form>,
    val i: Int,
    val body: List<Form>,
    val env: Map<String, HVal>,
    val addr: Address,
) : Frame()

private class IfK(
    val then: Form,
    val els: Form,
    val env: Map<String, HVal>,
    val addr: Address,
) : Frame()

private class CallK(val n: Int, val addr: Address) : Frame()

private class SampleK(val addr: Address) : Frame()

private class ObserveK(val addr: Address) : Frame()

sealed class StepResult {
    abstract val m: M

    data class Sample(val addr: Address, val dist: HVal, override val m: M) : StepResult()
    data class Observe(val addr: Address, val dist: HVal, val value: HVal, override val m: M) : StepResult()
    data class Done(val value: HVal, override val m: M) : StepResult()
}


fun resume(m: M): StepResult {
    while (m.C.isNotEmpty()) {
        when (val f = m.popC()) {
            is Ev -> eval(m, f.expr, f.env, f.addr)
            is Discard -> m.popV()
            is LetK -> stepLet(m, f)
            is IfK -> {
                val (branch, tag) = if (m.popV().toBool()) f.then to "then" else f.els to "else"
                m.pushC(Ev(branch, f.env, f.addr.ext(tag)))
            }
            is CallK -> stepCall(m, f)
            is SampleK -> return StepResult.Sample(f.addr, m.popV(), m)
            is ObserveK -> {
                val y = m.popV()
                val d = m.popV()
                return StepResult.Observe(f.addr, d, y, m)
            }
        }
    }
    return StepResult.Done(m.peekV(), m)
}

fun send(m: M, value: HVal) = m.pushV(value)

private fun eval(m: M, e: Form, env: Map<String, HVal>, addr: Address) {
    when (e) {
        is Symbol -> {
            val name = e.name
            when {
                env.containsKey(name) -> m.pushV(env.getValue(name))
                isPrimitive(name) -> m.pushV(Primitive(name, PRIMITIVES.getValue(name)))
                else -> throw NameError(name)
            }
        }
        is List<*> -> {
            evalList(m, e, env, addr)
        }
        else -> m.pushV(literal(e))
    }
}

private fun evalList(m: M, e: List<Form>, env: Map<String, HVal>, addr: Address) {
    when ((e[0] as? Symbol)?.name) {
        "let" -> {
            val binds = e[1] as List<Form>
            val body = e.subList(2, e.size)
            if (binds.isNotEmpty()) {
                m.pushC(LetK(binds, 0, body, env, addr))
                m.pushC(Ev(binds[1], env, addr.ext("let", 0)))
            } else {
                pushBody(m, body, env, addr)
            }
        }
        "if" -> {
            m.pushC(IfK(e[2], e[3], env, addr))
            m.pushC(Ev(e[1], env, addr.ext("test")))
        }
        "fn" -> {
            val params = paramNames(e[1])
            m.pushV(Closure(params, e.subList(2, e.size), env))
        }
        "sample" -> {
            m.pushC(SampleK(addr))
            m.pushC(Ev(e[1], env, addr.ext("d")))
        }
        "observe" -> {
            m.pushC(ObserveK(addr))
            m.pushC(Ev(e[2], env, addr.ext("v")))
            m.pushC(Ev(e[1], env, addr.ext("d")))
        }
        else -> {
            m.pushC(CallK(e.size - 1, addr))
            for (i in e.size - 1 downTo 1) m.pushC(Ev(e[i], env, addr.ext(i - 1)))
            m.pushC(Ev(e[0], env, addr.ext("fn")))
        }
    }
}

private fun stepLet(m: M, f: LetK) {
    val name = (f.binds[2 * f.i] as Symbol).name
    val env = f.env + (name to m.popV())
    val next = f.i + 1
    if (2 * next < f.binds.size) {
        m.pushC(LetK(f.binds, next, f.body, env, f.addr))
        m.pushC(Ev(f.binds[2 * next + 1], env, f.addr.ext("let", 2 * next)))
    } else {
        pushBody(m, f.body, env, f.addr)
    }
}

private fun stepCall(m: M, f: CallK) {
    val args = ArrayList<HVal>(f.n)
    repeat(f.n) { args.add(m.popV()) }
    args.reverse()
    when (val fn = m.popV()) {
        is Closure -> pushBody(m, fn.body, fn.env + fn.params.zip(args), f.addr)
        is Primitive -> m.pushV(fn.fn(args))
        else -> error("cannot call non-function: $fn")
    }
}

private fun pushBody(m: M, body: List<Form>, env: Map<String, HVal>, addr: Address) {
    val seq = ArrayList<Frame>(2 * body.size)
    for (n in 0 until body.size - 1) {
        seq.add(Ev(body[n], env, addr.ext("body", n)))
        seq.add(Discard)
    }
    seq.add(Ev(body.last(), env, addr.ext("body", body.size - 1)))
    for (i in seq.indices.reversed()) m.pushC(seq[i])
}

private fun literal(e: Form): HVal = when (e) {
    is HVal -> e
    is Long -> hInt(e)
    is Int -> hInt(e)
    is Double -> hFloat(e)
    is Boolean -> hBool(e)
    is String -> HVal.HString(e)
    null -> HNil
    else -> error("cannot evaluate literal: $e")
}

private fun paramNames(params: Form): List<String> = (params as List<*>).map { (it as Symbol).name }

/**
 * Parse [program], build a machine ready to evaluate the single trailing main expression.
 */
fun initialMachine(program: String, rng: Random = Random()): M {
    val genv = LinkedHashMap<String, HVal>()
    var main: Form = null
    for (form in parse(program)) {
        if (form is List<*> && form.isNotEmpty() && (form[0] as? Symbol)?.name == "defn") {
            val name = (form[1] as Symbol).name
            val params = paramNames(form[2])
            @Suppress("UNCHECKED_CAST")
            val body = form.subList(3, form.size) as List<Form>
            genv[name] = Closure(params, body, genv)
        } else {
            main = form
        }
    }
    return M(C = listOf(Ev(main, genv, emptyList())), env = genv, rng = rng)
}
