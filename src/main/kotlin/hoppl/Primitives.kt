package hoppl

import kotlin.math.*

/**
 * Deterministic primitive functions
 *
 * Each entry in [PRIMITIVES] takes a List<HVal> and returns an HVal.
 */

typealias Prim = (List<HVal>) -> HVal

private fun num(x: HVal): Double = x.toDouble()

private fun primAdd(args: List<HVal>): HVal {
    if (args.isEmpty()) return hInt(0)
    if (args[0] is HVal.HMatrix) {
        var result = args[0].toMatrix()
        for (a in args.drop(1)) result = primMatAdd(result, a.toMatrix())
        return HVal.HMatrix(result)
    }
    if (args.all { it is HVal.HInt })
        return hInt(args.sumOf { (it as HVal.HInt).v })
    return hFloat(args.sumOf { num(it) })
}

private fun primSub(args: List<HVal>): HVal {
    if (args.size == 1)
        return if (args[0] is HVal.HInt) hInt(-(args[0] as HVal.HInt).v)
        else hFloat(-num(args[0]))
    val head = num(args[0])
    val rest = args.drop(1).sumOf { num(it) }
    val result = head - rest
    return if (args.all { it is HVal.HInt }) hInt(result.toLong()) else hFloat(result)
}

private fun primMul(args: List<HVal>): HVal {
    if (args.all { it is HVal.HInt })
        return hInt(args.fold(1L) { acc, v -> acc * (v as HVal.HInt).v })
    return hFloat(args.fold(1.0) { acc, v -> acc * num(v) })
}

private fun primDiv(args: List<HVal>): HVal {
    if (args.size == 1) return hFloat(1.0 / num(args[0]))
    var result = num(args[0])
    for (a in args.drop(1)) result /= num(a)
    return hFloat(result)
}

private fun primEq(a: HVal, b: HVal): Boolean = when {
    a is HVal.HMatrix && b is HVal.HMatrix ->
        a.v.size == b.v.size && a.v.zip(b.v).all { (ra, rb) -> ra.contentEquals(rb) }
    a is HVal.HInt  && b is HVal.HInt  -> a.v == b.v
    a is HVal.HBool && b is HVal.HBool -> a.v == b.v
    else -> a == b
}

private fun primGet(args: List<HVal>): HVal {
    val coll = args[0]
    val key = args[1]
    val default = args.getOrNull(2) ?: HNil
    return when (coll) {
        is HVal.HMap -> coll.v[key] ?: default
        is HVal.HVec -> {
            val i = key.toLong().toInt()
            if (i in coll.v.indices) coll.v[i] else default
        }
        is HVal.HMatrix -> HVal.HMatrix(arrayOf(coll.v[key.toLong().toInt()]))
        else -> error("get: not a collection: $coll")
    }
}

private fun primPut(args: List<HVal>): HVal {
    val coll  = args[0]
    val key   = args[1]
    val value = args[2]
    return when (coll) {
        is HVal.HMap -> hMap(coll.v + mapOf(key to value))
        is HVal.HVec -> hVec(coll.v.toMutableList().also { it[key.toLong().toInt()] = value })
        else -> error("put: not a collection: $coll")
    }
}

private fun primHashMap(args: List<HVal>): HVal {
    require(args.size % 2 == 0) { "hash-map: odd number of arguments" }
    return hMap((args.indices step 2).associate { i -> args[i] to args[i + 1] })
}

private fun primRange(args: List<HVal>): HVal {
    val a = args.map { it.toLong().toInt() }
    val range = when (a.size) {
        1 -> 0 until a[0]
        2 -> a[0] until a[1]
        3 -> a[0] until a[1] step a[2]
        else -> error("range: wrong number of args")
    }
    return hVec(range.map { hInt(it.toLong()) })
}


private fun toMat(x: HVal): Array<DoubleArray> = x.toMatrix()

private fun primMatAdd(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> =
    Array(a.size) { i -> DoubleArray(a[i].size) { j -> a[i][j] + b[i][j] } }

private fun primMatMul(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
    val rows = a.size; val cols = b[0].size; val inner = b.size
    return Array(rows) { i ->
        DoubleArray(cols) { j -> (0 until inner).sumOf { k -> a[i][k] * b[k][j] } }
    }
}

private fun primMatTranspose(a: Array<DoubleArray>): Array<DoubleArray> {
    val rows = a.size; val cols = a[0].size
    return Array(cols) { j -> DoubleArray(rows) { i -> a[i][j] } }
}

private fun primMatTanh(a: Array<DoubleArray>): Array<DoubleArray> =
    Array(a.size) { i -> DoubleArray(a[i].size) { j -> tanh(a[i][j]) } }

private fun primMatRelu(a: Array<DoubleArray>): Array<DoubleArray> =
    Array(a.size) { i -> DoubleArray(a[i].size) { j -> max(a[i][j], 0.0) } }

private fun primMatRepmat(a: Array<DoubleArray>, r: Int, c: Int): Array<DoubleArray> =
    Array(a.size * r) { i ->
        DoubleArray(a[0].size * c) { j -> a[i % a.size][j % a[0].size] }
    }


val PRIMITIVES: Map<String, Prim> = mutableMapOf<String, Prim>().also { m ->
    m["+"] = { primAdd(it) }
    m["-"] = { primSub(it) }
    m["*"] = { primMul(it) }
    m["/"] = { primDiv(it) }
    m["sqrt"] = { hFloat(sqrt(num(it[0]))) }
    m["exp"] = { hFloat(exp(num(it[0]))) }
    m["log"] = { hFloat(ln(num(it[0]))) }
    m["pow"] = { hFloat(num(it[0]).pow(num(it[1]))) }
    m["abs"] = { hFloat(abs(num(it[0]))) }
    m["floor"] = { hInt(floor(num(it[0])).toLong()) }
    m["ceil"] = { hInt(ceil(num(it[0])).toLong()) }
    m["tanh"] = { hFloat(tanh(num(it[0]))) }
    m["max"] = { hFloat(it.maxOf { v -> num(v) }) }
    m["min"] = { hFloat(it.minOf { v -> num(v) }) }
    m["mod"] = { hFloat(num(it[0]) % num(it[1])) }

    m["="] = { hBool(primEq(it[0], it[1])) }
    m["=="] = { hBool(primEq(it[0], it[1])) }
    m["!="] = { hBool(!primEq(it[0], it[1])) }
    m["<"] = { hBool(num(it[0]) < num(it[1])) }
    m[">"] = { hBool(num(it[0]) > num(it[1])) }
    m["<="] = { hBool(num(it[0]) <= num(it[1])) }
    m[">="] = { hBool(num(it[0]) >= num(it[1])) }
    m["and"] = { hBool(it.all { v -> v.toBool() }) }
    m["or"] = { hBool(it.any { v -> v.toBool() }) }
    m["not"] = { hBool(!it[0].toBool()) }

    m["vector"] = { hVec(it) }
    m["list"] = { hVec(it) }
    m["first"] = { it[0].toList()[0] }
    m["second"] = { it[0].toList()[1] }
    m["last"] = { it[0].toList().last() }
    m["rest"] = { hVec(it[0].toList().drop(1)) }
    m["nth"] = { it[0].toList()[it[1].toLong().toInt()] }
    m["count"] = { hInt(it[0].toList().size.toLong()) }
    m["empty?"] = { hBool(it[0].toList().isEmpty()) }
    m["peek"] = { it[0].toList().last() }
    m["range"] = { primRange(it) }
    m["conj"] = { hVec(it[0].toList() + it.drop(1)) }
    m["cons"] = { hVec(listOf(it[0]) + it[1].toList()) }
    m["append"] = { hVec(it[0].toList() + it.drop(1)) }
    m["concat"] = { hVec(it.flatMap { v -> v.toList() }) }

    m["hash-map"] = { primHashMap(it) }
    m["get"] = { primGet(it) }
    m["put"] = { primPut(it) }
    m["assoc"] = { primPut(it) }

    m["vector?"] = { hBool(it[0] is HVal.HVec) }
    m["map?"] = { hBool(it[0] is HVal.HMap) }
    m["number?"] = { hBool(it[0] is HVal.HInt || it[0] is HVal.HFloat) }

    m["mat-mul"] = { HVal.HMatrix(primMatMul(toMat(it[0]), toMat(it[1]))) }
    m["mat-add"] = { HVal.HMatrix(primMatAdd(toMat(it[0]), toMat(it[1]))) }
    m["mat-transpose"] = { HVal.HMatrix(primMatTranspose(toMat(it[0]))) }
    m["mat-tanh"] = { HVal.HMatrix(primMatTanh(toMat(it[0]))) }
    m["mat-relu"] = { HVal.HMatrix(primMatRelu(toMat(it[0]))) }
    m["mat-repmat"] = { HVal.HMatrix(primMatRepmat(toMat(it[0]), it[1].toLong().toInt(), it[2].toLong().toInt())) }

    for ((name, ctor) in DISTRIBUTIONS) {
        m[name] = { args -> hDist(ctor(args)) }
    }
}

fun isPrimitive(name: String): Boolean = name in PRIMITIVES