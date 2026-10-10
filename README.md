# thislog

A small, dependency-free logging library for the JVM.

`thislog` is built around four pieces: a **logger** you take by name, a **level**
that decides what survives, a **formatter** that turns an event into a line, and
a **destination** that line is written to. Nothing else is required, and there is
nothing to configure before the first call works. When one destination is not
enough, **appenders** add more — see [More than one destination](#more-than-one-destination).

> **Status: early.** The core (levels, named loggers, deferred and
> parameterized `{}` messages, the pattern language, console and file
> destinations, appenders, full stack traces, one shared writer per file, a
> flush on exit, and files that roll over on a fixed interval) is implemented
> and covered by tests. The pieces a mature logging library is expected to
> have — size-based rolling with retention, MDC, an SLF4J binding — are not
> there yet. See [Roadmap](#roadmap).

## Requirements

Java 21 or newer. No runtime dependencies.

## Installing

Published as `io.github.ferizoozoo:thislog`.

**Gradle**

```kotlin
dependencies {
    implementation("io.github.ferizoozoo:thislog:0.3.0")
}
```

**Maven**

```xml
<dependency>
  <groupId>io.github.ferizoozoo</groupId>
  <artifactId>thislog</artifactId>
  <version>0.3.0</version>
</dependency>
```

Releases are published to GitHub Packages, so you will need that repository
configured:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/ferizoozoo/thislog")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull
            password = providers.gradleProperty("gpr.key").orNull
        }
    }
}
```

GitHub Packages asks for a login even to read a public package: `gpr.user` is
your GitHub username and `gpr.key` a personal access token with the
`read:packages` scope, both kept in `~/.gradle/gradle.properties` rather than
in the build. To build against a local copy instead, run `./gradlew
:lib:publishToMavenLocal` and add `mavenLocal()` to your repositories.

### Releasing

Publishing a GitHub release whose tag is a version — `v0.1.0`, `v1.2.3-rc.1` —
builds, tests, and publishes that version. The `version` in `gradle.properties`
is only what local builds and manual runs of the publish workflow use.

On the module path, the module name is `io.github.ferizoozoo.thislog`.

## Quick start

```java
import io.github.ferizoozoo.thislog.LogOptions;
import io.github.ferizoozoo.thislog.LoggingFactory;

var log = LoggingFactory.get(OrderRouter.class, LogOptions.createFromEnvironment());

log.info("order 4711 accepted");
log.info("user {} added {} items", userId, count);
log.warn("stock running low");
log.error("could not price the basket", new IllegalStateException("pricing failed"));
```

A name resolves to exactly one logger, wherever it is asked for. Taking the same
name twice — from different classes, on different threads — hands back the same
instance, so configuring it in one place reaches every holder.

A logger built by hand joins the same registry through
`LoggingFactory.add(name, logger)`. A name that is already taken by another
logger is not replaced: `add` throws `IllegalArgumentException`, and the name
keeps resolving to the logger it had.

## Levels

`TRACE < DEBUG < INFO < WARN < ERROR < FATAL`.

A message comes in three forms:

```java
log.info("order 4711 accepted");                   // already a string
log.info("user {} did {}", userId, action);        // filled in only if it is written
log.debug(() -> describe(everyCandidateRoute()));  // built only if it is written
```

Take the `String` form when the message is a literal, the `{}` form when it
carries values, and the supplier form when building it costs something. Each of
the six levels has all three, and the `String` and supplier forms each have a
second version that also takes a throwable.

The difference is when the work happens. Java evaluates arguments before the
call, so a plain string is built whether or not the line is written. The `{}`
form passes its arguments as they are and substitutes them only for a line that
will be written, so a dropped line never calls their `toString`. The arguments
themselves are still evaluated at the call, though: `log.debug("{}",
describe(routes))` runs `describe` either way. When the expensive part is the
argument, reach for the supplier — it is never called for a line that will not
be written, which is the whole reason to defer.

### Placeholders

Each `{}` takes the next argument, in order:

- An argument with no `{}` left for it is left out; a `{}` with no argument left
  stays `{}`.
- `null` renders as `null`, and an array renders its contents — `[1, 2, 3]`
  rather than `[I@1b6d3586`.
- A throwable in last place is also the line's cause, and its stack trace is
  rendered under the line:

  ```java
  log.error("order {} failed", orderId, e);
  ```

- There is no escape: a literal `{}` in a message with arguments is always a
  placeholder.

A throwable as the only argument, as in `log.info("failed: {}", e)`, picks the
`(String, Throwable)` form, which substitutes nothing: the line reads
`failed: {}` with the stack trace under it, as it would in SLF4J. And since
`log.info("x", null)` matches both that form and the `{}` form, Java rejects it
as ambiguous; write `(Object) null`.

A logger starts at `INFO`, so `TRACE` and `DEBUG` are dropped until you lower
it.

The threshold comes from the options the logger was built with, so it can be set
before the first line is written:

```java
var log = LoggingFactory.get(OrderRouter.class,
        LogOptions.createFromEnvironment().withLevel(LogLevel.DEBUG));
```

or moved afterwards, on a logger already in use:

```java
log.setCurrentLogLevel(LogLevel.WARN);

log.info(() -> "dropped");   // the supplier is never called
log.warn("kept");
```

`changeOptions` carries a level like any other option, so it resets a threshold
set by `setCurrentLogLevel` — the options are the source of truth.

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
| `%date{HH:mm:ss}`              | the timestamp, in the format given    |
| `%level`                       | the level                             |
| `%thread`                      | the thread name                       |
| `%logger`                      | the logger name                       |
| `%ex`                          | the stack trace, on the lines below   |
| `%nopex`                       | nothing — the stack trace is dropped  |
| `%n`                           | the platform line separator           |

**A conversion costs nothing unless the pattern names it.** The default pattern
is `%s`, which renders the message and nothing else — so someone who wants to log
one word gets one word, with no timestamp and no level in front of it. Everything
above is opt-in, one conversion at a time.

Any conversion can be given a width between the `%` and its name. It works
exactly as it does for `%s` in `String.format`:

| Modifier         | Effect                                                  |
| ---------------- | ------------------------------------------------------- |
| `%5level`        | at least 5 wide, padded on the left                     |
| `%-5level`       | at least 5 wide, padded on the right                    |
| `%.20logger`     | at most 20 wide, keeping the start                      |
| `%-5.5level`     | exactly 5 wide                                          |

A `%` the tables above do not name is literal, so `"50% done: %m"` is a valid
pattern and `create` never throws over one. The same goes for a width with no
conversion after it (`"100%5"`). The one thing `create` does reject is a
`%date{...}` whose format the JDK's `DateTimeFormatter` cannot read. A brace
that is never closed is not a format, so `%date{HH:mm` is the default date
followed by `{HH:mm`, and only `%date` takes a brace. A conversion has to
end at a non-letter, so `%nonsense` is nine literal characters rather than `%n`
followed by `onsense`. There is no escape for a literal `%m`.

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
log.error("could not complete the checkout", e);
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

By default the appender puts the trace under the formatted line. A pattern that
names `%ex` places it itself — `"%m%ex [%level]"` puts the level after the
trace — and one that names `%nopex` drops it. `%ex` renders nothing for an event
without a throwable, so `"%m%ex"` is exactly the default. A formatter of your own
takes the trace over the same way, by overriding `rendersThrown()` to return
`true`.

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

A destination that will not open — a file in a directory that does not exist,
say — is reported on stderr and never thrown. A new logger falls back to
stdout, and `getOptions()` says so. A `changeOptions(...)` that fails changes
nothing at all: the logger keeps the destination, formatter, and level it had,
and a closed logger stays closed.

> Two loggers pointed at the same path share one stream, so every line arrives
> whole. They still interleave in order: a line from one, then a line from the
> other.

### More than one destination

The options describe one destination. `addAppender` gives a logger another,
with a formatter of its own:

```java
log.addAppender(FileAppender.create("audit.log",
        PatternFormatter.create("%date %level %m")));
log.addAppender(StreamAppender.wrapping(System.err, PatternFormatter.create("%m")));
```

Every line the logger's level lets through goes to each appender. An added
appender belongs to the logger from then on: it is flushed with it and closed
with it, and `changeOptions(...)` leaves it in place — the options only replace
the destination they describe. `StreamAppender.create` closes the stream it is
given when the logger closes; `StreamAppender.wrapping` leaves it open, which
is the one to use for a stream you do not own.

Each appender also has a level of its own, `TRACE` until you move it. A line
reaches an appender only when it gets past both the logger's level and the
appender's, so a file can keep the detail the console leaves out:

```java
log.setCurrentLogLevel(LogLevel.DEBUG);

var console = StreamAppender.wrapping(System.out, PatternFormatter.create("%m"));
console.setLogLevel(LogLevel.INFO);
log.addAppender(console);
log.addAppender(FileAppender.create("debug.log", PatternFormatter.create("%date %level %m")));
```

The logger's level is checked first, so an appender's level can only narrow
what it receives, never widen it. It can be moved at any time, on an appender
already in use.

An appender can also stand in for the destination itself, by putting it in the
options:

```java
var log = LoggingFactory.get(OrderRouter.class, LogOptions.createFromEnvironment()
        .withAppender(FileAppender.create("orders.log", PatternFormatter.create("%date %m"))));
```

An appender in the options takes the place of the destination and formatter
beside it, which are then not opened or used. It belongs to the logger the same
way an added one does: a `changeOptions(...)` that carries the same appender
forward keeps it, and one that moves off it closes it.

### Rolling files

`RollingFileAppender` writes to a file and moves on to a new one each time an
interval has passed:

```java
var daily = RollingFileAppender.create("logs/app.log",
        PatternFormatter.create("%date %level %m"), Duration.ofDays(1).toMillis());
log.addAppender(daily);
```

The interval is counted from when the appender is created, then from each
roll. The check happens when a line is written, so a quiet logger does not
roll until it has something to write, and the line that finds the interval
over is the first one in the new file. A line is never split between two
files.

The first period goes to the path you give. Each one after it goes to a new
file in the same directory, named after that path with a timestamp added, so
the files sort in the order they were written. Earlier files are left as they
are: nothing is deleted or compressed.

This is the first version, and it is deliberately small:

- **Starting the appender empties the file at that path.** It does not append
  to what an earlier run left there, so move that file aside first if you need
  it.
- **Intervals are not aligned to the clock.** A daily appender created at 15:20
  rolls at 15:20, not at midnight, and a restart starts the count again.
- **The timestamp has one-second resolution.** Two rolls in the same second
  write to the same file, and the second one replaces the first. Keep
  intervals well above a second.
- **One appender per file.** Unlike `FileAppender`, two rolling appenders on the
  same path do not share a writer, and neither knows about the other's files.
- **The path needs an extension.** `app.log` works; a bare `app` cannot roll,
  and the appender stops writing at the first roll — reported through
  `checkFailure()` like any write error.

Closing the appender — or the logger it was added to — flushes and closes the
current file.

### Writing your own

`Appender` is an interface, so a destination this library does not ship is a
class of your own:

```java
final class ListAppender implements Appender {
    final List<String> lines = new ArrayList<>();

    @Override public void append(LogEvent event) { lines.add(event.getMessage()); }
    @Override public void flush() {}
    @Override public void close() {}
    @Override public boolean checkFailure() { return false; }
    @Override public LogLevel getLogLevel() { return LogLevel.TRACE; }
    @Override public void setLogLevel(LogLevel level) {}
}
```

Three things to know before writing one:

- **No locking is needed.** A logger calls its appenders under its own lock, so
  an appender used by one logger is only ever called by one thread at a time.
  Do not hand the same instance to two loggers.
- **`checkFailure()` answers once.** It should return `true` the first time it
  is asked after a write or flush failed, and `false` after that. The logger
  prints a notice on stderr every time it gets `true`, so an appender that keeps
  answering `true` repeats that notice on every `ERROR` line.
- **Throwing is survivable, not free.** An appender that throws is reported on
  stderr once per configuration and does not cost the call site or the other
  appenders their line — but the line it threw on is lost to it.

`removeAppender` takes an added appender off again. The logger flushes it and
closes it on the way out, so it is not used again after that. Removing an
appender the logger does not have does nothing. The appender from the options
cannot be removed — it is replaced through `changeOptions(...)` instead — and
asking to remove it throws `IllegalArgumentException`.

A closed logger refuses a new appender, and a removal, with
`IllegalStateException`.

## Configuration from the environment

A logger taken before anything is configured seeds itself from:

| Variable                | Default  | Meaning                                    |
| ----------------------- | -------- | ------------------------------------------ |
| `LOG_DESTINATION`       | `stdout` | `stdout`, `stderr`, or `file`              |
| `LOG_LEVEL`             | `INFO`   | Threshold: `TRACE`…`FATAL`, any case       |
| `LOG_FORMATTER`         | `%s`     | Pattern for `PatternFormatter`             |
| `LOG_FLUSH_INTERVAL_MS` | `2000`   | Background flush interval; `0` disables it |

`file` writes to `log.txt` in the working directory. Anything richer than this
belongs in code for now.

The three are not equally forgiving, which is worth knowing before you set one
in a container that has to come up. A `LOG_FORMATTER` that names no conversion
is not an error — it is literal text, so a typo costs you a wrong-looking line
rather than a crash. A bad `LOG_FLUSH_INTERVAL_MS` warns and falls back. But a
`LOG_DESTINATION` or `LOG_LEVEL` that names nothing throws out of
`LogOptions.createFromEnvironment()`, so a misspelled level fails startup rather
than logging at the wrong one.

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

1. **More of the pattern language** — `%F`/`%L` for the call site, once the
   event carries it.
2. **Rolling files, the rest** — size-based rolling, periods aligned to the
   clock (midnight, the top of the hour), appending to the file a previous run
   left, retention, and compression.
3. **Logger hierarchy** — `com.acme.checkout.Flow` inheriting from `com.acme` and
   a root logger.
4. **Configuration files** — with a defined precedence over system properties and
   the environment.
5. **MDC** — per-thread context carried on the event and reachable from a
   pattern.
6. **Structured output** — a JSON formatter and key-value pairs on the event.
7. **An SLF4J provider**, so existing applications can swap it in unchanged.

## Contributing

Issues and pull requests are welcome. Please keep the test suite green
(`./gradlew build`) and follow the naming style already in
[`lib/src/test`](lib/src/test/java/io/github/ferizoozoo/thislog) — tests are
named after the behaviour they pin down, in a sentence.

## License

[MIT](LICENSE).
