# HOPPL-Interpreter
A programming interpreter for a Lisp-like language - pure Kotlin

---

## Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| JDK  | 17+     | `java -version` |

You do **not** need to install Kotlin separately — Gradle downloads it automatically.

---

## Project Structure

```
hoppl-interpreter/
├── build.gradle.kts          # Build config (Kotlin JVM + application)
├── settings.gradle.kts       # Project info
├── gradlew                   # Gradle script (Linux)
├── gradlew.bat               # Gradle script (Windows)
└── src/
    └── main/
        └── kotlin/
            └── hoppl/
                └── Main.kt   # Entry point
```

## Running the Project

```bash
./gradlew run
```
