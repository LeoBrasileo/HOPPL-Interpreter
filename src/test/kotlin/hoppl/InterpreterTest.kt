package hoppl

import hoppl.interpreter.HVal
import hoppl.interpreter.hBool
import hoppl.interpreter.hInt
import hoppl.interpreter.initialMachine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deterministic evaluation tests for the interpreter.
 */
class InterpreterTest {

    private fun eval(src: String): HVal =
        run(initialMachine(src)).value

    @Test
    fun `literal integer`() {
        assertEquals(hInt(42), eval("42"))
    }

    @Test
    fun `arithmetic is variadic`() {
        assertEquals(hInt(6), eval("(+ 1 2 3)"))
    }

    @Test
    fun `nested arithmetic`() {
        assertEquals(hInt(14), eval("(+ (* 2 3) (* 2 4))"))
    }

    @Test
    fun `let binds sequentially`() {
        assertEquals(hInt(25), eval("(let [x 3 y 4] (+ (* x x) (* y y)))"))
    }

    @Test
    fun `let bindings see earlier bindings`() {
        assertEquals(hInt(30), eval("(let [x 5 y (* x 6)] y)"))
    }

    @Test
    fun `if takes the then branch`() {
        assertEquals(hInt(10), eval("(if (< 2 3) 10 20)"))
    }

    @Test
    fun `if takes the else branch`() {
        assertEquals(hInt(20), eval("(if (> 2 3) 10 20)"))
    }

    @Test
    fun `fn creates an applicable closure`() {
        assertEquals(hInt(25), eval("(let [sq (fn [x] (* x x))] (sq 5))"))
    }

    @Test
    fun `closures capture their lexical environment`() {
        assertEquals(hInt(15), eval("(let [a 10] (let [f (fn [x] (+ x a))] (f 5)))"))
    }

    @Test
    fun `functions can return closures that capture their argument`() {
        val src = "(let [make-shift (fn [mu] (fn [x] (+ x mu)))  f (make-shift 10)] (f 3))"
        assertEquals(hInt(13), eval(src))
    }

    @Test
    fun `higher order functions`() {
        val src = """
            (let [twice (fn [f x] (f (f x)))
                  inc   (fn [n] (+ n 1))]
              (twice inc 10))
        """.trimIndent()
        assertEquals(hInt(12), eval(src))
    }

    @Test
    fun `defn supports recursion`() {
        val src = """
            (defn fact (n) (if (< n 1) 1 (* n (fact (- n 1)))))
            (fact 5)
        """.trimIndent()
        assertEquals(hInt(120), eval(src))
    }

    @Test
    fun `defn supports mutual recursion`() {
        val src = """
            (defn even? (n) (if (= n 0) true  (odd?  (- n 1))))
            (defn odd?  (n) (if (= n 0) false (even? (- n 1))))
            (even? 10)
        """.trimIndent()
        assertEquals(hBool(true), eval(src))
    }
}
