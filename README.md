# HOPPL-Interpreter
A programming language interpreter for a Lisp-like probabilistic language 

Pure Kotlin

---

## Prerequisites

**JDK 17+**

You do **not** need to install Kotlin separately - Gradle script downloads it automatically.

## Running the Interpreter

There is a sh script to instantly run the interpreter (it uses `./gradlew run` on the back):
```bash
./hoppl "program..."
```

Example programs:
```bash
./hoppl "(+ (1 2))"

./hoppl "(sample (normal 0 1))"

./hoppl "(defn geom [] (if (sample (bernoulli 0.3)) 0 (+ 1 (geom)))) (geom)"
```

## Run the tests

The project provides several tests, separated by section under `src/test/kotlin/hoppl/`.

Run all by executing:
```bash
./gradlew test
```
