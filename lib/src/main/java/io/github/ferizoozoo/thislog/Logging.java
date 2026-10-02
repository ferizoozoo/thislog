package io.github.ferizoozoo.thislog;

import java.util.Objects;
import java.util.function.Supplier;

public class Logging implements Loggable {

    private static final LogLevel FLUSH_THRESHOLD = LogLevel.ERROR;

    private final String name;
    private volatile LogLevel currentLevel = LogLevel.TRACE;
    private volatile LogOptions options;

    private Appender appender;
    private volatile boolean closed;

    private Logging(String name, LogOptions options) {
        this.name = Objects.requireNonNull(name, "name");
        this.options = Objects.requireNonNull(options, "options");
        this.currentLevel = options.getLevel();
        this.setupAppender();
    }

    public static Logging create(String name, LogOptions options) {
        var logger = new Logging(name, options);
        LoggingFactory.track(logger);
        return logger;
    }

    private static Appender appenderFromOptions(LogOptions options) {
        return Appenders.logDestinationToAppender(options.getDestination(), options.getFormatter());
    }

    private void setupAppender() {
        var previous = this.appender;
        try {
            var dest = this.options.getDestination();
            this.appender = appenderFromOptions(this.options);
            // The new appender is open before the old one lets go, so a file
            // both point at stays open across the switch.
            if (previous != null) {
                previous.close();
            }
            // A buffered destination is the only reason to run a flush timer,
            // and changeOptions reaches one without going through the factory.
            Flusher.destinationOpened(dest);
        } catch (RuntimeException e) {
            System.err.println("thislog: cannot apply the logging configuration in the environment ("
                    + e + "); falling back to stdout");
            if (this.appender == null || this.appender == Appenders.discardingAppender()) {
                this.resetAppender();
            }
        }
    }

    private void resetAppender() {
        if (this.appender != null) {
            this.appender.close();
        }
        this.appender = Appenders.logDestinationToAppender(LogDestination.STDOUT, this.options.getFormatter());
    }

    @Override
    public synchronized void changeOptions(LogOptions options) {
        this.options = options;
        this.currentLevel = options.getLevel();
        this.closed = false;
        this.setupAppender();
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
        this.appender.flush();
        this.appender.close();
        this.appender = Appenders.discardingAppender();
        this.closed = true;
    }

    @Override
    public synchronized void flush() {
        this.appender.flush();
    }

    private synchronized void write(LogEvent logEvent) {
        this.appender.append(logEvent);
        if (logEvent.getLevel().severity() >= FLUSH_THRESHOLD.severity()) {
            flushAndReportWriteFailure();
        }
    }

    private void flushAndReportWriteFailure() {
        this.appender.flush();
        if (this.appender.checkFailure()) {
            System.err.println("thislog: the destination for '" + this.name
                    + "' is failing; log output may be lost");
        }
    }
}
