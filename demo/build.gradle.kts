plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":lib"))
}

application {
    mainClass = "io.github.ferizoozoo.thislog.demo.Demo"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
}

tasks.register("runDemo") {
    group = "application"
    description = "Prints one of every rendering so the visuals can be checked."
    dependsOn(tasks.named("run"))
}
