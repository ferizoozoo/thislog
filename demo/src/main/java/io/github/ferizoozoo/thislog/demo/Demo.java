package io.github.ferizoozoo.thislog.demo;

import io.github.ferizoozoo.thislog.LogFormatter;
import io.github.ferizoozoo.thislog.Loggable;
import io.github.ferizoozoo.thislog.LogOptions;
import io.github.ferizoozoo.thislog.LogDestination;
import io.github.ferizoozoo.thislog.LoggingFactory;
import io.github.ferizoozoo.thislog.PatternFormatter;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Demo {

    public static void main(String[] args) throws Exception {
        plainIsTheDefault();
        colourIsOptedInto();
        aLayoutCanUseEverythingTheEventCarries();
        aLambdaIsStillThereForWhatAPatternCannotSay();
        aPercentThatNamesNothingIsJustAPercent();
        anExceptionRidesUnderItsLine();
        aFileGetsCleanText();
        oneNameIsOneLogger();
    }

    private static LogOptions using(LogFormatter formatter) {
        return LogOptions.createFromEnvironment()
                .withFormatter(formatter)
                .withDestination(LogDestination.STDOUT);
    }

    private static Loggable configured(String name, LogOptions options) {
        return LoggingFactory.get(name, options);
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
        heading("3. A pattern reaches everything the event carries");
        var detailed = PatternFormatter.create("%date %level [%thread] %logger - %m");

        var coloured = LogFormatter.colored(detailed);
        var log = configured(Demo.class, using(coloured));
        log.info(() -> "order 4711 accepted");
        log.warn(() -> "stock running low");
    }

    private static void aLambdaIsStillThereForWhatAPatternCannotSay() {
        heading("4. A LogFormatter is still a function, for layouts no pattern covers");
        LogFormatter withCause = event -> event.getMessage()
                + (event.getThrown() == null ? "" : " (" + event.getThrown().getMessage() + ")");

        var log = configured("com.acme.orders.Reconciler", using(withCause));
        log.warn(() -> "retrying", new IllegalStateException("upstream timed out"));
    }

    private static void aPercentThatNamesNothingIsJustAPercent() {
        heading("5. A % the table does not name is literal, and never an error");
        var log = configured("com.acme.index.Reindex",
                using(PatternFormatter.create("50% done (%nonsense): %m")));
        log.info(() -> "reindexing");
    }

    private static void anExceptionRidesUnderItsLine() {
        heading("6. A throwable and its causes ride under the line");
        var coloured = LogFormatter.colored(PatternFormatter.create("%s"));
        var log = configured("com.acme.billing.Pricing", using(coloured));
        var cause = new IllegalArgumentException("negative quantity: -3");
        log.error(() -> "could not price the basket", new IllegalStateException("pricing failed", cause));
    }

    private static void aFileGetsCleanText() throws Exception {
        heading("7. A file destination gets no escape sequences");

        Path sink = Files.createTempFile("thislog-demo", ".log");
        var plain = PatternFormatter.create(PatternFormatter.DEFAULT_PATTERN);
        var log = configured("com.acme.audit.AuditTrail",
                using(plain).withDestination(LogDestination.file(sink.toString())));
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
        heading("8. A name resolves to one logger, wherever it is asked for");

        var early = LoggingFactory.get("com.acme.orders.OrderRouter",
                LogOptions.createFromEnvironment());

        var coloured = LogFormatter.colored(PatternFormatter.create("[orders] %s"));
        LoggingFactory.get("com.acme.orders.OrderRouter", LogOptions.createFromEnvironment())
                .changeOptions(using(coloured));

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
