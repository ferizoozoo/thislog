# thislog

A small, dependency-free logging library for the JVM.

`thislog` is built around four pieces: a **logger** you take by name, a **level**
that decides what survives, a **formatter** that turns an event into a line, and
a **destination** that line is written to. Nothing else is required, and there is
nothing to configure before the first call works.

> **Status: early.** The core (levels, named loggers, formatters, console and
> file destinations, exception rendering) is implemented and covered by tests.
> The pieces a mature logging library is expected to have — parameterized
> messages, a real pattern language, appenders, rolling files, MDC, an SLF4J
> binding — are not there yet. See [Roadmap](#roadmap).

## Requirements

Java 21 or newer. No runtime dependencies.

## Installing

Published as `io.github.ferizoozoo:thislog`.

**Gradle**

```kotlin
dependencies {
    implementation("io.github.ferizoozoo:thislog:0.1.0-SNAPSHOT")
}
```

**Maven**

```xml
<dependency>
  <groupId>io.github.ferizoozoo</groupId>
  <artifactId>thislog</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Snapshots are published to GitHub Packages, so you will need that repository
configured. To build against a local copy instead, run `./gradlew
:lib:publishToMavenLocal` and add `mavenLocal()` to your repositories.

On the module path, the module name is `io.github.ferizoozoo.thislog`.

## Quick start

```java
import io.github.ferizoozoo.thislog.LoggingFactory;

var log = LoggingFactory.get(OrderRouter.class);

log.info("order 4711 accepted");
log.warn("stock running low");
log.error("could not price the basket", new IllegalStateException("pricing failed"));
```

A name resolves to exactly one logger, wherever it is asked for. Taking the same
name twice — from different classes, on different threads — hands back the same
instance, so configuring it in one place reaches every holder.

## Levels

`TRACE < DEBUG < INFO < WARN < ERROR < FATAL`, plus `OFF`.

Every level has a one-argument form and a form that takes a throwable. A logger
starts at `TRACE` and writes everything; raising its level drops anything below
the threshold before the event is even built.

```java
log.setCurrentLevel(LogLevel.WARN);

log.info("dropped");     // never formatted, never written
log.warn("kept");
```

## Formatters

A `LogFormatter` is a function from a `LogEvent` to a line. The event carries the
message, the level, the timestamp, the thread name, the logger name, and the
throwable if there was one.

```java
LogFormatter detailed = event -> String.format("%s %-5s [%s] %s - %s",
        CLOCK.format(Instant.ofEpochMilli(event.getTimestamp())),
        event.getLevel(),
        event.getThreadName(),
        event.getLoggerName(),
        event.getMessage());

var log = LoggingFactory.get(OrderRouter.class, detailed, LogOptions.initiateOptions());
```

`PatternFormatter` is the one that ships. Today its pattern is a
`String.format` template whose only argument is the message —
`PatternFormatter.create("[orders] %s")` — so anything richer needs a lambda like
the one above. A real pattern language is the next thing planned for it.

`LogFormatter.colored(...)` wraps any formatter and tints the line by level using
ANSI escapes. It is opt-in, because it is only right on a terminal.

## Destinations

```java
LogOptions.initiateOptions().setDestination(LogDestination.STDOUT)
LogOptions.initiateOptions().setDestination(LogDestination.STDERR)
LogOptions.initiateOptions().setDestination(LogDestination.file("app.log"))
```

Files are opened in append mode and buffered; `ERROR` and above force a flush.
Call `close()` on a logger to flush and release a file it owns.

> Two loggers pointed at the same path each open their own buffered stream and
> will interleave. Until a shared writer lands, give each file one logger.

## Configuration from the environment

A logger taken before anything is configured seeds itself from:

| Variable          | Default    | Meaning                        |
| ----------------- | ---------- | ------------------------------ |
| `LOG_DESTINATION` | `stdout`   | `stdout`, `stderr`, or `file`  |
| `LOG_FORMATTER`   | `%s`       | Pattern for `PatternFormatter` |

`file` writes to `log.txt` in the working directory. Anything richer than this
belongs in code for now.

## Building

```bash
./gradlew build          # compile, test, javadoc, jars
./gradlew :demo:run      # print one of every rendering
./gradlew :lib:publishToMavenLocal
```

The demo in [`demo/`](demo/src/main/java/io/github/ferizoozoo/thislog/demo/Demo.java)
prints one example of each behaviour and is the fastest way to see what the
library currently does.

## Roadmap

Roughly in the order it makes sense to build:

1. **Parameterized and lazy messages** — `log.info("user {} did {}", id, action)`
   and `log.info(() -> expensive())`, plus `isEnabled(level)`.
2. **A real pattern language** — `%d{HH:mm:ss} %-5level [%thread] %logger - %msg`,
   compiled once rather than parsed per event.
3. **Stack frames** — exceptions currently render as `toString()` per cause, with
   no frames and no suppressed exceptions.
4. **Appenders** — one logger writing to many destinations, each with its own
   formatter, level, and filters; a shared writer per file path; a shutdown hook
   so buffered lines are not lost at exit.
5. **Rolling files** — size and time based, with retention and compression.
6. **Logger hierarchy** — `com.acme.checkout.Flow` inheriting from `com.acme` and
   a root logger.
7. **Configuration files** — with a defined precedence over system properties and
   the environment.
8. **MDC** — [`Context`](lib/src/main/java/io/github/ferizoozoo/thislog/Context.java)
   exists but is not yet wired into events or formatters.
9. **Structured output** — a JSON formatter and key-value pairs on the event.
10. **An SLF4J provider**, so existing applications can swap it in unchanged.

## Contributing

Issues and pull requests are welcome. Please keep the test suite green
(`./gradlew build`) and follow the naming style already in
[`lib/src/test`](lib/src/test/java/io/github/ferizoozoo/thislog) — tests are
named after the behaviour they pin down, in a sentence.

## License

Not yet chosen.
