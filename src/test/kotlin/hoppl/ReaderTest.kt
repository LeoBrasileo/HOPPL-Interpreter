package hoppl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Parser tests.
 */
class ReaderTest {

    @Test
    fun `parses integer`() {
        assertEquals(42L, parseOne("42"))
    }

    @Test
    fun `parses negative integer`() {
        assertEquals(-7L, parseOne("-7"))
    }

    @Test
    fun `parses float`() {
        assertEquals(3.14, parseOne("3.14"))
    }

    @Test
    fun `parses true`() {
        assertEquals(true, parseOne("true"))
    }

    @Test
    fun `parses false`() {
        assertEquals(false, parseOne("false"))
    }

    @Test
    fun `parses nil`() {
        assertNull(parseOne("nil"))
    }

    @Test
    fun `parses symbol`() {
        assertEquals(Symbol("+"), parseOne("+"))
    }

    @Test
    fun `parses string literal`() {
        assertEquals("hello", parseOne("\"hello\""))
    }

    @Test
    fun `parses simple list`() {
        val form = parseOne("(+ 1 2)") as List<*>
        assertEquals(3, form.size)
        assertEquals(Symbol("+"), form[0])
        assertEquals(1L, form[1])
        assertEquals(2L, form[2])
    }

    @Test
    fun `parses nested list`() {
        val form = parseOne("(+ 1 (* 2 3))") as List<*>
        assertEquals(Symbol("+"), form[0])
        assertEquals(1L, form[1])
        val inner = form[2] as List<*>
        assertEquals(Symbol("*"), inner[0])
        assertEquals(2L, inner[1])
        assertEquals(3L, inner[2])
    }

    @Test
    fun `square brackets treated as parens`() {
        val round  = parseOne("(+ 1 2)") as List<*>
        val square = parseOne("[+ 1 2]") as List<*>
        assertEquals(round, square)
    }

    @Test
    fun `parses multiple top-level forms`() {
        val forms = parse("1 2 3")
        assertEquals(listOf(1L, 2L, 3L), forms)
    }

    @Test
    fun `semicolon comment is ignored`() {
        assertEquals(1L, parseOne("; this is a comment\n1"))
    }

    @Test
    fun `formToString round-trips a nested form`() {
        val src  = "(+ 1 (* 2 3))"
        val form = parseOne(src)
        assertEquals(src, formToString(form))
    }

    @Test
    fun `formToString renders nil`() {
        assertEquals("nil", formToString(null))
    }

    @Test
    fun `formToString renders booleans`() {
        assertEquals("true", formToString(true))
        assertEquals("false", formToString(false))
    }

    @Test
    fun `unmatched open paren throws`() {
        assertFailsWith<SyntaxError> { parseOne("(+ 1 2") }
    }

    @Test
    fun `unexpected close paren throws`() {
        assertFailsWith<SyntaxError> { parseOne(")") }
    }

    @Test
    fun `unterminated string throws`() {
        assertFailsWith<SyntaxError> { parseOne("\"hello") }
    }

    @Test
    fun `multiple top-level forms in parseOne throws`() {
        assertFailsWith<SyntaxError> { parseOne("1 2") }
    }
}