package io.github.ferizoozoo.thislog;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;

public class Logging implements Loggable {

    private static final String LINE_SEPARATOR = System.lineSeparator();

    private static final LogLevel FLUSH_THRESHOLD = LogLevel.ERROR;

    private static final PrintStream DISCARD = new PrintStream(OutputStream.nullOutputStream(), false);

    private final String name;
    private volatile LogLevel currentLevel = LogLevel.TRACE;
    private volatile LogOptions options;

    private PrintStream printer;
    private String ownedPath;
    private boolean reportedWriteFailure;
    private volatile boolean closed;

    private Logging(String name, LogOptions options) {
        this.name = Objects.requireNonNull(name, "name");
        this.options = Objects.requireNonNull(options, "options");
        this.currentLevel = options.getLevel();
        this.setupPrinter();
    }

    public static Logging create(String name, LogOptions options) {
        var logger = new Logging(name, options);
        LoggingFactory.track(logger);
        return logger;
    }

    private void setupPrinter() {
        var previousPath = this.ownedPath;
        try {
            var dest = this.options.getDestination();
            this.printer = Utilities.logDestinationToPrintStream(dest);
            this.ownedPath = dest instanceof LogDestination.LogFile file ? file.path() : null;
            this.reportedWriteFailure = false;
            if (previousPath != null) {
                FileStreams.release(previousPath);
            }
            // A buffered destination is the only reason to run a flush timer,
            // and changeOptions reaches one without going through the factory.
            Flusher.destinationOpened(dest);
        } catch (RuntimeException e) {
            System.err.println("thislog: cannot apply the logging configuration in the environment ("
                    + e + "); falling back to stdout");
            if (this.printer == null || this.printer == DISCARD) {
                this.resetPrinter();
            }
        }
    }

    private void resetPrinter() {
        if (this.ownedPath != null) {
            FileStreams.release(this.ownedPath);
        }
        this.printer = System.out;
        this.ownedPath = null;
        this.reportedWriteFailure = false;
    }

    @Override
    public synchronized void changeOptions(LogOptions options) {
        this.options = options;
        this.currentLevel = options.getLevel();
        this.closed = false;
        this.setupPrinter();
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public LogOptions getOptions() {
        return this.options;
    }

    @Override
    public LogLevel getCurrentLogLevel() {
        return this.currentLevel;
    }

    @Override
    public void setCurrentLogLevel(LogLevel level) {
        this.currentLevel = Objects.requireNonNull(level, "level");
    }

    @Override
    public boolean isEnabled(LogLevel level) {
        return !this.closed && level.severity() >= this.currentLevel.severity();
    }

    @Override
    public void log(LogLevel level, Supplier<String> message, Throwable thrown) {
        if (!isEnabled(level)) {
            return;
        }

        String text;
        try {
            text = message.get();
        } catch (RuntimeException e) {
            text = "[message supplier failed: " + e + "]";
        }

        var event = LogEvent.create(text, level, System.currentTimeMillis(), this.name, thrown);
        write(event);
    }

    @Override
    public synchronized void close() {
        if (this.closed) {
            return;
        }
        this.printer.flush();
        if (this.ownedPath != null) {
            FileStreams.release(this.ownedPath);
        }
        this.ownedPath = null;
        this.printer = DISCARD;
        this.reportedWriteFailure = false;
        this.closed = true;
    }

    @Override
    public synchronized void flush() {
        this.printer.flush();
    }

    private synchronized void write(LogEvent logEvent) {
        try {
            var line = new StringBuilder(this.options.getFormatter().format(logEvent));
            if (logEvent.getThrown() != null) {
                line.append(LINE_SEPARATOR).append(StackTraces.render(logEvent.getThrown()));
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
