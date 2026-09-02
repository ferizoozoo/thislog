package org.example;

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
        theLevelDecidesWhatSurvives();
        anExceptionRidesUnderItsLine();
        aFileGetsCleanText();
        oneNameIsOneLogger();
    }

    private static void plainIsTheDefault() {
        heading("1. The default pattern renders the message and nothing else");
        var log = LoggingFactory.get("com.acme.Bootstrap",
                new PatternFormatter(PatternFormatter.DEFAULT_PATTERN), LogOptions.initiateOptions());
        log.info("server started on port 8080");
        log.warn("cache is 91% full");
    }

    private static void colourIsOptedInto() {
        heading("2. LogFormatter.colored wraps any layout for a terminal");
        var log = LoggingFactory.get("com.acme.checkout.CheckoutFlow",
                LogFormatter.colored(new PatternFormatter("%s")), LogOptions.initiateOptions());
        log.trace("entering checkout flow");
        log.debug("resolved 3 candidate routes");
        log.info("payment authorised");
        log.warn("retrying upstream call");
        log.error("could not reach inventory service");
        log.fatal("shutting down");
    }

    private static void aLayoutCanUseEverythingTheEventCarries() {
        heading("3. A custom LogFormatter reaches for everything the event carries");
        LogFormatter detailed = event -> String.format("%s %-5s [%s] %s - %s",
                CLOCK.format(Instant.ofEpochMilli(event.getTimestamp())),
                event.getLevel(),
                event.getThreadName(),
                event.getLoggerName(),
                event.getMessage());

        var log = LoggingFactory.get(Demo.class, LogFormatter.colored(detailed),
                LogOptions.initiateOptions());
        log.info("order 4711 accepted");
        log.warn("stock running low");
    }

    private static void theLevelDecidesWhatSurvives() {
        heading("4. setCurrentLevel(WARN) suppresses everything below it");
        var log = LoggingFactory.get("com.acme.inventory.StockMonitor",
                LogFormatter.colored(new PatternFormatter("%s")), LogOptions.initiateOptions());
        log.setCurrentLevel(LogLevel.WARN);

        log.trace("you should not see this");
        log.debug("nor this");
        log.info("nor this");
        log.warn("but this survives");
        log.error("and this");

        System.out.println("   (three lines were suppressed above)");
    }

    private static void anExceptionRidesUnderItsLine() {
        heading("5. A throwable and its causes ride under the line");
        var log = LoggingFactory.get("com.acme.billing.Pricing",
                LogFormatter.colored(new PatternFormatter("%s")), LogOptions.initiateOptions());
        var cause = new IllegalArgumentException("negative quantity: -3");
        log.error("could not price the basket", new IllegalStateException("pricing failed", cause));
    }

    private static void aFileGetsCleanText() throws Exception {
        heading("6. A file destination gets no escape sequences");
        Path sink = Files.createTempFile("thislog-demo", ".log");
        var log = LoggingFactory.get("com.acme.audit.AuditTrail",
                new PatternFormatter(PatternFormatter.DEFAULT_PATTERN),
                LogOptions.initiateOptions().setDestination(LogDestination.file(sink.toString())));
        log.info("user signed in");
        log.error("checkout failed");

        log.close();

        System.out.println("   " + sink);
        for (String line : Files.readAllLines(sink)) {
            System.out.println("   | " + line.replace(String.valueOf((char) 27), "<ESC>"));
        }
        Files.deleteIfExists(sink);
    }

    private static void oneNameIsOneLogger() {
        heading("7. A name resolves to one logger, wherever it is asked for");

        // A name this demo has not touched, so nothing is configured yet.
        var early = LoggingFactory.get("com.acme.orders.OrderRouter");

        // Somewhere else entirely, the same name is configured.
        LoggingFactory.get("com.acme.orders.OrderRouter",
                LogFormatter.colored(new PatternFormatter("[orders] %s")),
                LogOptions.initiateOptions());

        // The handle taken before that already has the new configuration.
        early.info("configured from somewhere else");
        System.out.println("   same instance: "
                + (early == LoggingFactory.get("com.acme.orders.OrderRouter")));
    }

    private static void heading(String title) {
        System.out.println();
        System.out.println("== " + title + " ==");
    }

    private Demo() {
    }
}
