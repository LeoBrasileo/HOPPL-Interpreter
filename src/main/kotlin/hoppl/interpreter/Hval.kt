package hoppl.interpreter

import hoppl.distributions.Distribution

// It is important for this class to be sealed given that allow us to match all possible expressions on the evaluator run
public sealed class HVal {
    data class HInt(val v: Long) : HVal()
    data class HFloat(val v: Double) : HVal()
    data class HBool(val v: Boolean) : HVal()
    data class HString(val v: String) : HVal()
    data class HVec(val v: List<HVal>) : HVal()
    data class HMap(val v: Map<HVal, HVal>) : HVal()
    data class HMatrix(val v: Array<DoubleArray>) : HVal()   // row-major 2-D
    data class HDist(val v: Distribution) : HVal()
    object HNil : HVal()

    fun toDouble(): Double = when (this) {
        is HInt -> v.toDouble()
        is HFloat -> v
        is HBool -> if (v) 1.0 else 0.0
        else -> error("expected number, got $this")
    }

    fun toLong(): Long = when (this) {
        is HInt -> v
        is HFloat -> v.toLong()
        is HBool -> if (v) 1L else 0L
        else -> error("expected integer, got $this")
    }

    fun toBool(): Boolean = when (this) {
        is HBool -> v
        is HNil -> false
        else -> true
    }

    fun toList(): List<HVal> = when (this) {
        is HVec -> v
        else -> error("expected vector, got $this")
    }

    fun toMap(): Map<HVal, HVal> = when (this) {
        is HMap -> v
        else -> error("expected hash-map, got $this")
    }

    fun toMatrix(): Array<DoubleArray> = when (this) {
        is HMatrix -> v
        is HVec -> arrayOf(v.map { it.toDouble() }.toDoubleArray())
        else -> error("expected matrix, got $this")
    }

    fun toDist(): Distribution = when (this) {
        is HDist -> v
        else -> error("expected distribution, got $this")
    }

    override fun toString(): String = when (this) {
        is HInt -> v.toString()
        is HFloat -> v.toString()
        is HBool -> if (v) "true" else "false"
        is HString -> "\"$v\""
        is HVec -> "[${v.joinToString(" ")}]"
        is HMap -> "{${v.entries.joinToString(", ") { "${it.key} ${it.value}" }}}"
        is HMatrix -> v.joinToString("\n") { row -> "[${row.joinToString(" ")}]" }
        is HDist -> v.toString()
        is HNil -> "nil"
        is Closure -> toString()
        is Primitive -> toString()
    }
}

// Convenience constructors
fun hInt(v: Long) = HVal.HInt(v)
fun hInt(v: Int) = HVal.HInt(v.toLong())
fun hFloat(v: Double) = HVal.HFloat(v)
fun hBool(v: Boolean) = HVal.HBool(v)
fun hVec(v: List<HVal>) = HVal.HVec(v)
fun hMap(v: Map<HVal, HVal>) = HVal.HMap(v)
fun hDist(d: Distribution) = HVal.HDist(d)
val HNil = HVal.HNil