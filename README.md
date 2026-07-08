# HOPPL-Interpreter
An interpreter for HOPPL (Higher-Order Probabilistic Programming Language), a Lisp-like language for writing generative probabilistic models. Written in pure Kotlin.

A program is read into an AST, then evaluated by a suspendable stack machine that pauses every time it hits a sample or observe. Different inference algorithms are just different ways of driving that machine; deciding what to do at each pause point (checkpoint).


## The pipeline

```
 program text
     |
     ▼
 reader / parser       -->  turns text into an abstract syntax tree
     │
     ▼
 interpreter           -->  evaluates the AST, pausing at every checkpoint
     │
     ▼
 runner + inference    -->  drives the interpreter, to approximate the distribution the program describes.
 algorithm                   
                            
```

The runner stage are defined on the `controllers`.

## The probabilistic primitives

HOPPL extends an ordinary functional language (`let`, `if`, functions) with exactly two special forms, and everything about inference is built around them:

- **`sample`** — draw a random value from a distribution. This is a random *choice*.
- **`observe`** — score a value against a distribution."

A single run of the program produces one **trace**: the sequence of choices it made at each `sample`, and the total log-likelihood it accumulated at each `observe`.

## The inference algorithms

Three different strategies for turning traces into an approximation of the program's distribution:

- **Likelihood Weighting (LW)**: Run the program forward many times, making fresh random choices every time, and weight each resulting trace by how well it agrees with the observed data (its total `observe` log-likelihood). Runs that better explain the data count for more.
- **Single-site Metropolis-Hastings (MH)**: Start from one trace, then repeatedly propose a small, local change: pick one past random choice, redraw just that one, and replay everything else. Accept or reject the resulting trace given a probability, so that over many iterations the accepted traces converge to the target distribution.
- **Sequential Monte Carlo (SMC)**: Advance many independent copies of the program (particles) together, up to each `observe`. At every such point, weight each particle by how well it matches the data so far, then resample the population before continuing repeating the cycle.

# How to use

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
