plugins {
    kotlin("jvm") version "2.0.21"
    application
}

group = "org.leobrasileo"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("hoppl.MainKt")
}

tasks.named<JavaExec>("run") {
    (project.findProperty("p") as String?)?.let { args(it) }
    standardInput = System.`in`
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
