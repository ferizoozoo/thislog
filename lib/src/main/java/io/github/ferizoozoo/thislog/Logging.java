package io.github.ferizoozoo.thislog;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;

public class Logging implements Loggable {

    private static final String LINE_SEPARATOR = System.lineSeparator();

    private static final LogLevel FLUSH_THRESHOLD = LogLevel.ERROR;

    private final String name;
    private volatile LogLevel currentLevel = LogLevel.TRACE;
    private volatile LogOptions options;

    private PrintStream printer;
    private boolean ownsPrinter;
    private boolean reportedWriteFailure;

    private Logging(String name, LogOptions options) {
        this.name = Objects.requireNonNull(name, "name");
        this.options = Objects.requireNonNull(options, "options");

        this.setPrinter(System.out, false);
        this.options = LogOptions.initiateOptions();
    }

    public static Logging create(String name, LogOptions options) {
        return new Logging(name, options);
    }

    @Override 
    public void addOptions(LogOptions options) {
        try {
            this.options = options;
            var dest = options.getDestination();
            this.setPrinter(Utilities.LogDestinationToPrintStream(dest),
                    dest instanceof LogDestination.LogFile);
        } catch (RuntimeException e) {
            System.err.println("thislog: cannot apply the logging configuration in the environment ("
                    + e + "); falling back to stdout");
        }
    }

    private void setPrinter(PrintStream printer, boolean ownsPrinter) {
        this.printer = printer;
        this.ownsPrinter = ownsPrinter;
        this.reportedWriteFailure = false;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void log(LogLevel level, String message, Throwable thrown) {
        if (level.severity() < this.currentLevel.severity()) {
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
        this.setPrinter(System.out, false);
    }

    private synchronized void write(LogEvent logEvent) {
        try {
            var line = new StringBuilder(this.options.getFormatter().format(logEvent));
            if (logEvent.getThrown() != null) {
                appendThrowable(line, logEvent.getThrown());
            }
            this.printer.write((line + LINE_SEPARATOR).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            reportFormattingFailure(e);
        } finally {
            if (logEvent.getLevel().severity() >= FLUSH_THRESHOLD.severity()) {
                flushAndReportWriteFailure();
            }
        }
    }

    private void flushAndReportWriteFailure() {
        if (this.printer.checkError() && !this.reportedWriteFailure) {
            this.reportedWriteFailure = true;
            System.err.println("thislog: the destination for '" + this.name
                    + "' is failing; log output may be lost");
        }
    }

    private static void appendThrowable(StringBuilder line, Throwable thrown) {
        line.append(LINE_SEPARATOR).append(thrown.toString().stripTrailing());

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
            this.printer.println(this.options.getFormatter().format(event));
        } catch (Exception alsoFailed) {
            this.printer.println(LogLevel.coloredMessage("Failed to format log message: " + failure, LogLevel.ERROR));
        }
        // The caller only flushes for severe levels, but a dropped line is worth
        // seeing whatever level provoked it.
        this.printer.flush();
    }
}
