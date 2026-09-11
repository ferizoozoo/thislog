package io.github.ferizoozoo.thislog.demo;

import io.github.ferizoozoo.thislog.LogFormatter;
import io.github.ferizoozoo.thislog.Loggable;
import io.github.ferizoozoo.thislog.LogOptions;
import io.github.ferizoozoo.thislog.LogDestination;
import io.github.ferizoozoo.thislog.LoggingFactory;
import io.github.ferizoozoo.thislog.PatternFormatter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Prints one of everything so the rendering can be eyeballed.
 *
 * <p>Run with {@code ./gradlew runDemo --console=plain}. The colour sections
 * only look right on a terminal that understands ANSI escapes.
 */
public final class Demo {

    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    public static void main(String[] args) throws Exception {
        plainIsTheDefault();
        colourIsOptedInto();
        aLayoutCanUseEverythingTheEventCarries();
        anExceptionRidesUnderItsLine();
        aFileGetsCleanText();
        oneNameIsOneLogger();
    }

    /**
     * A formatter takes effect through the options. The destination has to be
     * named too: changeOptions reads it unconditionally, so options without one
     * fail and report a fallback that never happened.
     */
    private static LogOptions using(LogFormatter formatter) {
        return LogOptions.createFromEnvironment()
                .setFormatter(formatter)
                .setDestination(LogDestination.STDOUT);
    }

    /**
     * The factory only builds a logger the first time a name is asked for, so
     * the options are applied again in case it already existed.
     */
    private static Loggable configured(String name, LogOptions options) {
        var log = LoggingFactory.get(name, options);
        log.changeOptions(options);
        return log;
    }

    private static Loggable configured(Class<?> type, LogOptions options) {
        return configured(type.getName(), options);
    }

    private static void plainIsTheDefault() {
        heading("1. The default pattern renders the message and nothing else");
        var plain = PatternFormatter.create(PatternFormatter.DEFAULT_PATTERN);
        var log = configured("com.acme.Bootstrap", using(plain));
        log.info(() -> "server started on port 8080");
        log.warn(() -> "cache is 91% full");
    }

    private static void colourIsOptedInto() {
        heading("2. LogFormatter.colored wraps any layout for a terminal");
        var coloured = LogFormatter.colored(PatternFormatter.create("%s"));
        var log = configured("com.acme.checkout.CheckoutFlow", using(coloured));
        log.trace(() -> "entering checkout flow");
        log.debug(() -> "resolved 3 candidate routes");
        log.info(() -> "payment authorised");
        log.warn(() -> "retrying upstream call");
        log.error(() -> "could not reach inventory service");
        log.fatal(() -> "shutting down");
    }

    private static void aLayoutCanUseEverythingTheEventCarries() {
        heading("3. A custom LogFormatter reaches for everything the event carries");
        LogFormatter detailed = event -> String.format("%s %-5s [%s] %s - %s",
                CLOCK.format(Instant.ofEpochMilli(event.getTimestamp())),
                event.getLevel(),
                event.getThreadName(),
                event.getLoggerName(),
                event.getMessage());

        var coloured = LogFormatter.colored(detailed);
        var log = configured(Demo.class, using(coloured));
        log.info(() -> "order 4711 accepted");
        log.warn(() -> "stock running low");
    }

    private static void anExceptionRidesUnderItsLine() {
        heading("4. A throwable and its causes ride under the line");
        var coloured = LogFormatter.colored(PatternFormatter.create("%s"));
        var log = configured("com.acme.billing.Pricing", using(coloured));
        var cause = new IllegalArgumentException("negative quantity: -3");
        log.error(() -> "could not price the basket", new IllegalStateException("pricing failed", cause));
    }

    private static void aFileGetsCleanText() throws Exception {
        heading("5. A file destination gets no escape sequences");

        Path sink = Files.createTempFile("thislog-demo", ".log");
        var plain = PatternFormatter.create(PatternFormatter.DEFAULT_PATTERN);
        var log = configured("com.acme.audit.AuditTrail",
                using(plain).setDestination(LogDestination.file(sink.toString())));
        log.info(() -> "user signed in");
        log.error(() -> "checkout failed");

        log.close();

        System.out.println("   " + sink);
        for (String line : Files.readAllLines(sink)) {
            System.out.println("   | " + line.replace(String.valueOf((char) 27), "<ESC>"));
        }
        Files.deleteIfExists(sink);
    }

    private static void oneNameIsOneLogger() {
        heading("6. A name resolves to one logger, wherever it is asked for");

        // A name this demo has not touched, so nothing is configured yet.
        var early = LoggingFactory.get("com.acme.orders.OrderRouter",
                LogOptions.createFromEnvironment());

        // Somewhere else entirely, the same name is configured.
        var coloured = LogFormatter.colored(PatternFormatter.create("[orders] %s"));
        configured("com.acme.orders.OrderRouter", using(coloured));

        // The handle taken before that already has the new configuration.
        early.info(() -> "configured from somewhere else");
        System.out.println("   same instance: "
                + (early == LoggingFactory.get("com.acme.orders.OrderRouter",
                        LogOptions.createFromEnvironment())));
    }

    private static void heading(String title) {
        System.out.println();
        System.out.println("== " + title + " ==");
    }

    private Demo() {
    }
}
