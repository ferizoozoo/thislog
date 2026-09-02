package org.example;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;

public class Logging implements Loggable, AutoCloseable {

    private static final String LINE_SEPARATOR = System.lineSeparator();

    private static final LogLevel FLUSH_THRESHOLD = LogLevel.ERROR;

    private final String name;

    private volatile LogLevel currentLevel = LogLevel.TRACE;
    private LogFormatter formatter;

    private PrintStream printer = System.out;
    private boolean ownsPrinter = false;

    public Logging(String name) {
        var options = LogOptions.fromEnvironment();
        this.formatter = options.getFormatter();
        var dest = options.getDestination();
        this.printer = Utilities.LogDestinationToPrintStream(dest);
        this.name = Objects.requireNonNull(name, "name");
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public synchronized void setCurrentLevel(LogLevel level) {
        this.currentLevel = level;
    }

    @Override
    public synchronized void setFormatter(LogFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public synchronized void setOptions(LogOptions options) {
        var newFormatter = options.getFormatter();
        if (newFormatter != null) {
            this.formatter = newFormatter;
        }

        var destination = options.getDestination();
        if (destination == null) {
            return;
        }
        try {
            PrintStream next = switch (destination) {
                case LogDestination.Stdout ignored ->
                    System.out;
                case LogDestination.Stderr ignored ->
                    System.err;
                case LogDestination.LogFile logFile ->
                    // Buffered, so FLUSH_THRESHOLD is what decides when bytes reach
                    // the disk. A bare FileOutputStream has nothing to flush.
                    new PrintStream(
                            new BufferedOutputStream(new FileOutputStream(logFile.path(), true)),
                            false);
            };
            if (this.ownsPrinter) {
                this.printer.close();
            }
            this.printer = next;
            this.ownsPrinter = destination instanceof LogDestination.LogFile;
        } catch (Exception e) {
            this.printer.println("Failed to set log destination: " + e.getMessage());
        }
    }

    @Override
    public void log(LogLevel level, String message, Throwable thrown) {
        if (level == LogLevel.OFF || level.severity() < this.currentLevel.severity()) {
            return;
        }
        var event = LogEvent.create(message, level, System.currentTimeMillis(), this.name, thrown);
        write(event);
    }

    @Override
    public synchronized void close() {
        this.printer.flush();
        if (this.ownsPrinter) {
            this.printer.close();
        }
        this.printer = System.out;
        this.ownsPrinter = false;
    }

    private synchronized void write(LogEvent logEvent) {
        try {
            var line = new StringBuilder(this.formatter.format(logEvent));
            if (logEvent.getThrown() != null) {
                appendThrowable(line, logEvent.getThrown());
            }
            this.printer.write((line + LINE_SEPARATOR).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            reportFormattingFailure(e);
        } finally {
            if (logEvent.getLevel().severity() >= FLUSH_THRESHOLD.severity()) {
                this.printer.flush();
            }
        }
    }

    private static void appendThrowable(StringBuilder line, Throwable thrown) {
        line.append(LINE_SEPARATOR).append(thrown.toString().stripTrailing());

        // A cause chain can be cyclic, so walk it by identity and stop on a repeat.
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        seen.add(thrown);
        for (var cause = thrown.getCause(); cause != null && seen.add(cause); cause = cause.getCause()) {
            line.append(LINE_SEPARATOR)
                    .append("Caused by: ")
                    .append(cause.toString().stripTrailing());
        }
    }

    private void reportFormattingFailure(Exception failure) {
        try {
            var event = LogEvent.create("Failed to format log message", LogLevel.ERROR,
                    System.currentTimeMillis(), this.name, failure);
            this.printer.println(this.formatter.format(event));
        } catch (Exception alsoFailed) {
            this.printer.println(LogLevel.color(LogLevel.ERROR)
                    + "Failed to format log message: " + failure
                    + LogLevel.color(LogLevel.OFF));
        }
        // The caller only flushes for severe levels, but a dropped line is worth
        // seeing whatever level provoked it.
        this.printer.flush();
    }
}
