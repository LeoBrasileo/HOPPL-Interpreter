package hoppl

/**
 * S-expression reader.
 *
 * Forms are represented as Kotlin values
 * The return type of parse / parseOne is [Form] = Any?.
 */

@JvmInline
value class Symbol(val name: String) {
    override fun toString() = name
}

typealias Form = Any?

private const val DELIMITERS = " \t\n\r,"

/**
 * Tokenize [text] into a flat list of tokens.
 * Tokens are:
 *   "(" or ")"     for any of ( ) [ ]
 *   a [StringToken] for "..." literals
 *   a plain [String] for identifiers / numbers
 */
private class StringToken(val value: String)

private fun tokenize(text: String): List<Any> {
    val tokens = mutableListOf<Any>()
    var i = 0
    val n = text.length
    while (i < n) {
        when (text[i]) {
            in DELIMITERS -> i++
            ';' -> { while (i < n && text[i] != '\n') i++ }
            '(', '[' -> { tokens.add("("); i++ }
            ')', ']' -> { tokens.add(")"); i++ }
            '"' -> {
                val buf = StringBuilder()
                i++
                while (i < n && text[i] != '"') {
                    if (text[i] == '\\' && i + 1 < n) { i++ }
                    buf.append(text[i])
                    i++
                }
                if (i >= n) throw SyntaxError("unterminated string literal")
                i++
                tokens.add(StringToken(buf.toString()))
            }
            else -> {
                val start = i
                while (i < n && text[i] !in "$DELIMITERS()[];\"") i++
                tokens.add(text.substring(start, i))
            }
        }
    }
    return tokens
}

private fun atom(token: Any): Form = when (token) {
    is StringToken -> token.value
    is String -> when (token) {
        "true" -> true
        "false" -> false
        "nil" -> null
        else -> token.toLongOrNull()
            ?: token.toDoubleOrNull()
            ?: Symbol(token)
    }
    else -> error("unexpected token type: $token")
}

/** Returns (form, next_position). */
private fun readForm(tokens: List<Any>, pos: Int): Pair<Form, Int> {
    if (pos >= tokens.size) throw SyntaxError("unexpected end of input")
    return when (val tok = tokens[pos]) {
        "(" -> {
            val form = mutableListOf<Form>()
            var cur = pos + 1
            while (true) {
                if (cur >= tokens.size) throw SyntaxError("missing closing parenthesis")
                if (tokens[cur] == ")") return form to cur + 1
                val (sub, next) = readForm(tokens, cur)
                form.add(sub)
                cur = next
            }
            @Suppress("UNREACHABLE_CODE")
            form to pos
        }
        ")" -> throw SyntaxError("unexpected )")
        else -> atom(tok) to pos + 1
    }
}

/**
 * Parse [text] into a list of top-level forms.
 */
fun parse(text: String): List<Form> {
    val tokens = tokenize(text)
    val forms = mutableListOf<Form>()
    var pos = 0
    while (pos < tokens.size) {
        val (form, next) = readForm(tokens, pos)
        forms.add(form)
        pos = next
    }
    return forms
}

/**
 * Parse [text] that contains exactly one top-level form.
 */
fun parseOne(text: String): Form {
    val forms = parse(text)
    if (forms.size != 1)
        throw SyntaxError("expected exactly one form, got ${forms.size}")
    return forms[0]
}

/**
 * Render a [Form] back to approximate source text (useful for debugging).
 */
fun formToString(form: Form): String = when (form) {
    null -> "nil"
    is Boolean -> if (form) "true" else "false"
    is Symbol -> form.name
    is String -> "\"$form\""
    is List<*> -> "(${form.joinToString(" ") { formToString(it) }})"
    else -> form.toString()
}

class SyntaxError(message: String) : Exception(message)