plugins {
    // Apply the java-library plugin for API and implementation separation.
    `java-library`
    `maven-publish`
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // Use JUnit test framework.
    testImplementation(libs.junit)
}

// The Gradle project is called "lib"; the artifact people depend on is "thislog".
base {
    archivesName = "thislog"
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
        // Print the full assertion message and stack trace, so a failed
        // assertEquals shows both the expected and the actual value.
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    // Consumers get sources and javadoc alongside the jar.
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    // Keep doclint's real errors (bad references, malformed HTML) but do not
    // fail the build over undocumented members while the API is still moving.
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "thislog",
            "Implementation-Version" to project.version,
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "thislog"
            from(components["java"])

            pom {
                name = "thislog"
                description = "A small, dependency-free logging library for the JVM."
                url = "https://github.com/ferizoozoo/thislog"

                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/licenses/MIT"
                        distribution = "repo"
                    }
                }

                developers {
                    developer {
                        id = "ferizoozoo"
                        name = "Farhad Zohoor"
                    }
                }

                scm {
                    connection = "scm:git:https://github.com/ferizoozoo/thislog.git"
                    developerConnection = "scm:git:ssh://git@github.com/ferizoozoo/thislog.git"
                    url = "https://github.com/ferizoozoo/thislog"
                }
            }
        }
    }

    repositories {
        // Publishing target for CI. Credentials come from the environment, so
        // `publishToMavenLocal` keeps working locally without any setup.
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/ferizoozoo/thislog")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }
    }
}
