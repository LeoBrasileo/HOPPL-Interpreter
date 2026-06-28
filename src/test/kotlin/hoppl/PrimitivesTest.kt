package hoppl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrimitivesTest {

    private fun call(name: String, vararg args: HVal): HVal =
        PRIMITIVES[name]!!(args.toList())

    @Test
    fun `+ sums integers`() {
        assertEquals(hInt(6), call("+", hInt(1), hInt(2), hInt(3)))
    }

    @Test
    fun `+ sums floats`() {
        val result = call("+", hFloat(1.5), hFloat(2.5))
        assertEquals(hFloat(4.0), result)
    }

    @Test
    fun `- negates single value`() {
        assertEquals(hInt(-5), call("-", hInt(5)))
    }

    @Test
    fun `- subtracts`() {
        assertEquals(hInt(3), call("-", hInt(10), hInt(4), hInt(3)))
    }

    @Test
    fun `* multiplies integers`() {
        assertEquals(hInt(24), call("*", hInt(2), hInt(3), hInt(4)))
    }

    @Test
    fun `div divides`() {
        assertEquals(hFloat(2.5), call("/", hFloat(5.0), hFloat(2.0)))
    }

    @Test
    fun `sqrt returns correct value`() {
        assertEquals(hFloat(3.0), call("sqrt", hFloat(9.0)))
    }

    @Test
    fun `exp is inverse of log`() {
        val x = hFloat(2.0)
        val result = (call("exp", call("log", x)) as HVal.HFloat).v
        assertTrue(kotlin.math.abs(result - 2.0) < 1e-10)
    }

    @Test
    fun `floor rounds down`() {
        assertEquals(hInt(3), call("floor", hFloat(3.9)))
    }

    @Test
    fun `ceil rounds up`() {
        assertEquals(hInt(4), call("ceil", hFloat(3.1)))
    }

    @Test
    fun `mod returns remainder`() {
        assertEquals(hFloat(1.0), call("mod", hFloat(7.0), hFloat(3.0)))
    }

    @Test
    fun `max returns largest`() {
        assertEquals(hFloat(9.0), call("max", hFloat(3.0), hFloat(9.0), hFloat(1.0)))
    }

    @Test
    fun `min returns smallest`() {
        assertEquals(hFloat(1.0), call("min", hFloat(3.0), hFloat(9.0), hFloat(1.0)))
    }


    @Test
    fun `= returns true for equal ints`() {
        assertEquals(hBool(true), call("=", hInt(5), hInt(5)))
    }

    @Test
    fun `= returns false for unequal ints`() {
        assertEquals(hBool(false), call("=", hInt(5), hInt(6)))
    }

    @Test
    fun `lt and gt work`() {
        assertEquals(hBool(true),  call("<", hInt(1), hInt(2)))
        assertEquals(hBool(false), call(">", hInt(1), hInt(2)))
    }

    @Test
    fun `not negates`() {
        assertEquals(hBool(false), call("not", hBool(true)))
        assertEquals(hBool(true),  call("not", hBool(false)))
    }

    @Test
    fun `and is true only when all truthy`() {
        assertEquals(hBool(true),  call("and", hBool(true),  hBool(true)))
        assertEquals(hBool(false), call("and", hBool(true),  hBool(false)))
    }

    @Test
    fun `or is true when any truthy`() {
        assertEquals(hBool(true),  call("or", hBool(false), hBool(true)))
        assertEquals(hBool(false), call("or", hBool(false), hBool(false)))
    }


    @Test
    fun `vector creates HVec`() {
        val v = call("vector", hInt(1), hInt(2), hInt(3))
        assertEquals(hVec(listOf(hInt(1), hInt(2), hInt(3))), v)
    }

    @Test
    fun `first returns first element`() {
        val v = call("vector", hInt(10), hInt(20))
        assertEquals(hInt(10), call("first", v))
    }

    @Test
    fun `last returns last element`() {
        val v = call("vector", hInt(10), hInt(20), hInt(30))
        assertEquals(hInt(30), call("last", v))
    }

    @Test
    fun `rest drops first element`() {
        val v = call("vector", hInt(1), hInt(2), hInt(3))
        assertEquals(hVec(listOf(hInt(2), hInt(3))), call("rest", v))
    }

    @Test
    fun `nth returns element at index`() {
        val v = call("vector", hInt(10), hInt(20), hInt(30))
        assertEquals(hInt(20), call("nth", v, hInt(1)))
    }

    @Test
    fun `count returns length`() {
        val v = call("vector", hInt(1), hInt(2))
        assertEquals(hInt(2), call("count", v))
    }

    @Test
    fun `empty? is true for empty vector`() {
        assertEquals(hBool(true), call("empty?", hVec(emptyList())))
    }

    @Test
    fun `conj appends element`() {
        val v = call("vector", hInt(1), hInt(2))
        val result = call("conj", v, hInt(3))
        assertEquals(hVec(listOf(hInt(1), hInt(2), hInt(3))), result)
    }

    @Test
    fun `cons prepends element`() {
        val v = call("vector", hInt(2), hInt(3))
        assertEquals(hVec(listOf(hInt(1), hInt(2), hInt(3))), call("cons", hInt(1), v))
    }

    @Test
    fun `concat merges vectors`() {
        val a = call("vector", hInt(1), hInt(2))
        val b = call("vector", hInt(3), hInt(4))
        assertEquals(hVec(listOf(hInt(1), hInt(2), hInt(3), hInt(4))), call("concat", a, b))
    }

    @Test
    fun `range with one arg produces 0 to n-1`() {
        assertEquals(
            hVec(listOf(hInt(0), hInt(1), hInt(2))),
            call("range", hInt(3))
        )
    }

    @Test
    fun `range with two args produces lo to hi-1`() {
        assertEquals(
            hVec(listOf(hInt(2), hInt(3), hInt(4))),
            call("range", hInt(2), hInt(5))
        )
    }



    @Test
    fun `hash-map creates HMap`() {
        val m = call("hash-map", hInt(1), hInt(10), hInt(2), hInt(20))
        assertEquals(hMap(mapOf(hInt(1) to hInt(10), hInt(2) to hInt(20))), m)
    }

    @Test
    fun `get retrieves value from map`() {
        val m = call("hash-map", hInt(1), hInt(42))
        assertEquals(hInt(42), call("get", m, hInt(1)))
    }

    @Test
    fun `get returns default when key missing`() {
        val m = call("hash-map", hInt(1), hInt(42))
        assertEquals(HNil, call("get", m, hInt(99)))
    }

    @Test
    fun `put updates value in map`() {
        val m  = call("hash-map", hInt(1), hInt(10))
        val m2 = call("put", m, hInt(1), hInt(99))
        assertEquals(hInt(99), call("get", m2, hInt(1)))
    }

    @Test
    fun `put on vector replaces element`() {
        val v  = call("vector", hInt(1), hInt(2), hInt(3))
        val v2 = call("put", v, hInt(1), hInt(99))
        assertEquals(hVec(listOf(hInt(1), hInt(99), hInt(3))), v2)
    }


    @Test
    fun `vector? is true for HVec`() {
        assertEquals(hBool(true),  call("vector?", hVec(emptyList())))
        assertEquals(hBool(false), call("vector?", hInt(1)))
    }

    @Test
    fun `map? is true for HMap`() {
        assertEquals(hBool(true),  call("map?", hMap(emptyMap())))
        assertEquals(hBool(false), call("map?", hInt(1)))
    }

    @Test
    fun `number? is true for int and float`() {
        assertEquals(hBool(true),  call("number?", hInt(1)))
        assertEquals(hBool(true),  call("number?", hFloat(1.0)))
        assertEquals(hBool(false), call("number?", hBool(true)))
    }


    @Test
    fun `normal constructor in PRIMITIVES returns HDist`() {
        val d = call("normal", hFloat(0.0), hFloat(1.0))
        assertTrue(d is HVal.HDist)
        assertTrue(d.v is Normal)
    }

    @Test
    fun `flip constructor in PRIMITIVES returns HDist`() {
        val d = call("flip", hFloat(0.5))
        assertTrue((d as HVal.HDist).v is Bernoulli)
    }


    @Test
    fun `isPrimitive returns true for known names`() {
        assertTrue(isPrimitive("+"))
        assertTrue(isPrimitive("normal"))
        assertTrue(isPrimitive("hash-map"))
    }

    @Test
    fun `isPrimitive returns false for unknown names`() {
        assertFalse(isPrimitive("hoppl-magic"))
    }
}