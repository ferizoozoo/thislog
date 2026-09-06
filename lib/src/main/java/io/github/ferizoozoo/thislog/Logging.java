package io.github.ferizoozoo.thislog;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.function.Supplier;

public class Logging implements Loggable {

    private static final String LINE_SEPARATOR = System.lineSeparator();

    private static final LogLevel FLUSH_THRESHOLD = LogLevel.ERROR;

    private final String name;

    private volatile LogLevel currentLevel = LogLevel.TRACE;
    private LogFormatter formatter;

    private PrintStream printer;
    private boolean ownsPrinter;
    private boolean reportedWriteFailure;

    public Logging(String name) {
        this(name, LogOptions::fromEnvironment);
    }

    Logging(String name, Supplier<LogOptions> options) {
        this.name = Objects.requireNonNull(name, "name");

        this.setPrinter(System.out, false);
        this.formatter = PatternFormatter.create(PatternFormatter.DEFAULT_PATTERN);
        try {
            var resolved = options.get();
            this.formatter = resolved.getFormatter();
            var dest = resolved.getDestination();
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

    public static Logging create(String name) {
        return new Logging(name);
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
            var next = Utilities.LogDestinationToPrintStream(destination);
            if (this.ownsPrinter) {
                this.printer.close();
            }
            this.setPrinter(next, destination instanceof LogDestination.LogFile);
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
        this.setPrinter(System.out, false);
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
                flushAndReportWriteFailure();
            }
        }
    }

    /**
     * Flushes, then says something if the destination has been quietly refusing
     * writes: a PrintStream never throws, it records the failure and carries on,
     * so a full disk would otherwise lose lines in silence.
     *
     * <p>checkError() flushes before it reports, so this is the flush -- calling
     * both would flush twice. That is also why the check happens only where a
     * flush was going to happen anyway, rather than on every line.
     */
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
