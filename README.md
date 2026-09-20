# thislog

A small, dependency-free logging library for the JVM.

`thislog` is built around four pieces: a **logger** you take by name, a **level**
that decides what survives, a **formatter** that turns an event into a line, and
a **destination** that line is written to. Nothing else is required, and there is
nothing to configure before the first call works.

> **Status: early.** The core (levels, named loggers, deferred messages, the
> pattern language, console and file destinations, full stack traces, one
> shared writer per file, and a flush on exit) is implemented and covered by
> tests. The pieces a mature logging library is expected to have —
> parameterized `{}` messages, appenders, rolling files, MDC, an SLF4J binding —
> are not there yet. See [Roadmap](#roadmap).

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
import io.github.ferizoozoo.thislog.LogOptions;
import io.github.ferizoozoo.thislog.LoggingFactory;

var log = LoggingFactory.get(OrderRouter.class, LogOptions.createFromEnvironment());

log.info(() -> "order 4711 accepted");
log.warn(() -> "stock running low");
log.error(() -> "could not price the basket", new IllegalStateException("pricing failed"));
```

A name resolves to exactly one logger, wherever it is asked for. Taking the same
name twice — from different classes, on different threads — hands back the same
instance, so configuring it in one place reaches every holder.

## Levels

`TRACE < DEBUG < INFO < WARN < ERROR < FATAL`.

A message is a `Supplier<String>`, so nothing is built for a line that will not
be written. Every level has that one-argument form and a form that also takes a
throwable. A logger starts at `TRACE` and writes everything; raising its level
drops anything below the threshold before the supplier is ever called.

```java
log.setCurrentLogLevel(LogLevel.WARN);

log.info(() -> "dropped");   // the supplier is never called
log.warn(() -> "kept");
```

`isEnabled` is public, for guarding a block that costs more than one string:

```java
if (log.isEnabled(LogLevel.DEBUG)) {
    log.debug(() -> describe(everyCandidateRoute()));
}
```

A supplier that throws is reported in the line rather than escaping, so a broken
message never takes down the call site.

## Formatters

A `LogFormatter` is a function from a `LogEvent` to a line. The event carries the
message, the level, the timestamp, the thread name, the logger name, and the
throwable if there was one.

`PatternFormatter` is the one that ships. Its pattern is literal text with
conversions in it, compiled once when the formatter is built:

```java
var detailed = PatternFormatter.create("%date %level [%thread] %logger - %m");

var log = LoggingFactory.get(OrderRouter.class,
        LogOptions.createFromEnvironment().withFormatter(detailed));
// 2026-09-12 14:22:01.337 INFO [main] com.acme.OrderRouter - order 4711 accepted
```

| Conversion                     | Renders                               |
| ------------------------------ | ------------------------------------- |
| `%m`, `%msg`, `%message`, `%s` | the message                           |
| `%date`                        | the timestamp, `yyyy-MM-dd HH:mm:ss.SSS` |
| `%level`                       | the level                             |
| `%thread`                      | the thread name                       |
| `%logger`                      | the logger name                       |
| `%n`                           | the platform line separator           |

**A conversion costs nothing unless the pattern names it.** The default pattern
is `%s`, which renders the message and nothing else — so someone who wants to log
one word gets one word, with no timestamp and no level in front of it. Everything
above is opt-in, one conversion at a time.

There is nothing else to learn: no widths, no arguments, and no escape. A `%` the
table above does not name is literal, so `"50% done: %m"` is a valid pattern and
`create` never throws over one. A conversion has to end at a non-letter, so
`%nonsense` is nine literal characters rather than `%n` followed by `onsense`.

The message is appended into the line rather than substituted into the pattern,
so a `%` inside a logged message is never read as a conversion.

A formatter is still just a function, so a layout the pattern language does not
cover is a lambda:

```java
LogFormatter withCause = event -> event.getMessage()
        + (event.getThrown() == null ? "" : " (" + event.getThrown().getMessage() + ")");
```

`LogFormatter.colored(...)` wraps any formatter and tints the line by level using
ANSI escapes. It is opt-in, because it is only right on a terminal.

## Exceptions

Every level method has a form that takes a throwable alongside the message. It
is rendered under the line it belongs to, exactly as `printStackTrace` would
render it — the same format every Java reader already knows:

```java
log.error(() -> "could not complete the checkout", e);
```

```
could not complete the checkout
java.lang.RuntimeException: checkout failed for order 4711
	at com.acme.Checkout.complete(Checkout.java:18)
Caused by: java.lang.IllegalStateException: could not price the basket
	at com.acme.Pricing.price(Pricing.java:66)
	Suppressed: java.lang.IllegalStateException: and the session would not close
		at com.acme.Session.close(Session.java:41)
		... 3 more
	... 2 more
```

Causes, suppressed exceptions, and the `... n more` elision of frames already
shown above all behave as the JDK's does, and a cause chain that loops back on
itself ends in `[CIRCULAR REFERENCE: ...]` rather than spinning.

The whole trace is built into the line and written once, so a trace never
interleaves with another thread's — see [Destinations](#destinations).

The trace is appended by the logger rather than by the formatter, so a custom
`LogFormatter` cannot currently suppress it or move it. A `%ex` conversion is
the natural home for that, and is not there yet.

## Destinations

```java
LogOptions.createFromEnvironment().withDestination(LogDestination.STDOUT)
LogOptions.createFromEnvironment().withDestination(LogDestination.STDERR)
LogOptions.createFromEnvironment().withDestination(LogDestination.file("app.log"))
```

Files are opened in append mode and buffered. `ERROR` and above force a flush
immediately; everything below it is flushed on a timer, and again by a shutdown
hook when the JVM exits. Nothing has to be closed by hand for a buffered `INFO`
line to survive a normal exit.

`Runtime.halt()`, `SIGKILL` and a JVM crash run no shutdown hooks, so buffering
always loses its tail there. That is inherent, not a gap.

`close()` stops a logger for good: it flushes, releases the file, and from then
on discards anything logged to it — a logging call never throws, before or after.
`changeOptions(...)` puts a closed logger back to work.

> Two loggers pointed at the same path share one stream, so every line arrives
> whole. They still interleave in order: a line from one, then a line from the
> other.

## Configuration from the environment

A logger taken before anything is configured seeds itself from:

| Variable                | Default  | Meaning                                    |
| ----------------------- | -------- | ------------------------------------------ |
| `LOG_DESTINATION`       | `stdout` | `stdout`, `stderr`, or `file`              |
| `LOG_FORMATTER`         | `%s`     | Pattern for `PatternFormatter`             |
| `LOG_FLUSH_INTERVAL_MS` | `2000`   | Background flush interval; `0` disables it |

`file` writes to `log.txt` in the working directory. Anything richer than this
belongs in code for now.

A `LOG_FORMATTER` that names no conversion is not an error — it is literal text,
so a typo costs you a wrong-looking line rather than a crash.

## Building

```bash
./gradlew build          # compile, test, javadoc, jars
./gradlew runDemo --console=plain   # print one of every rendering
./gradlew :lib:publishToMavenLocal
```

The demo in [`demo/`](demo/src/main/java/io/github/ferizoozoo/thislog/demo/Demo.java)
prints one example of each behaviour and is the fastest way to see what the
library currently does.

## Roadmap

Roughly in the order it makes sense to build:

1. **Parameterized messages** — `log.info("user {} did {}", id, action)`, for the
   fixed-arity call SLF4J users expect. Deferral is already covered by the
   supplier form.
2. **More of the pattern language** — column widths (`%-5level`), a date format
   per pattern (`%date{HH:mm:ss}`), `%F`/`%L` for the call site.
3. **Appenders** — one logger writing to many destinations, each with its own
   formatter, level, and filters.
4. **Rolling files** — size and time based, with retention and compression.
5. **Logger hierarchy** — `com.acme.checkout.Flow` inheriting from `com.acme` and
   a root logger.
6. **Configuration files** — with a defined precedence over system properties and
   the environment.
7. **MDC** — [`Context`](lib/src/main/java/io/github/ferizoozoo/thislog/Context.java)
   exists but is not yet wired into events or formatters.
8. **Structured output** — a JSON formatter and key-value pairs on the event.
9. **An SLF4J provider**, so existing applications can swap it in unchanged.

## Contributing

Issues and pull requests are welcome. Please keep the test suite green
(`./gradlew build`) and follow the naming style already in
[`lib/src/test`](lib/src/test/java/io/github/ferizoozoo/thislog) — tests are
named after the behaviour they pin down, in a sentence.

## License

[MIT](LICENSE).
