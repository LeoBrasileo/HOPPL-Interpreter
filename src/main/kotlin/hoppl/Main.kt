package hoppl

import hoppl.controllers.run
import hoppl.interpreter.initialMachine

fun main(args: Array<String>) {
    val program = if (args.isNotEmpty()) {
        args.joinToString(" ")
    } else {
        System.`in`.bufferedReader().readText()
    }

    if (program.isBlank()) {
        System.err.println("usage: ./gradlew run --args=\"<program>\"")
        return
    }

    val trace = run(initialMachine(program))
    println(trace.value)
}
