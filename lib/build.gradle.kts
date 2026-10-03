plugins {
    `java-library`
    `maven-publish`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.junit)
}

base {
    archivesName = "thislog"
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
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
