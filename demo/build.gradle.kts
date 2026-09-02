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
    mainClass = "org.example.Demo"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
}

// Kept so the command in Demo's javadoc keeps working.
tasks.register("runDemo") {
    group = "application"
    description = "Prints one of every rendering so the visuals can be checked."
    dependsOn(tasks.named("run"))
}
