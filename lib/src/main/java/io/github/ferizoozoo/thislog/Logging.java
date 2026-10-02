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
        Objects.requireNonNull(options, "options");
        Appender opened;
        try {
            opened = openAppenderFor(options);
        } catch (RuntimeException e) {
            System.err.println("thislog: cannot apply the logging configuration for '" + name + "' ("
                    + e + "); falling back to stdout");
            options = options.withDestination(LogDestination.STDOUT);
            opened = openAppenderFor(options);
        }
        this.options = options;
        this.currentLevel = options.getLevel();
        this.appender = opened;
    }

    public static Logging create(String name, LogOptions options) {
        var logger = new Logging(name, options);
        LoggingFactory.track(logger);
        return logger;
    }

    private static Appender appenderFromOptions(LogOptions options) {
        return Appenders.logDestinationToAppender(options.getDestination(), options.getFormatter());
    }

    private static Appender openAppenderFor(LogOptions options) {
        var appender = appenderFromOptions(options);
        Flusher.destinationOpened(options.getDestination());
        return appender;
    }

    @Override
    public synchronized void changeOptions(LogOptions options) {
        Objects.requireNonNull(options, "options");
        Appender replacement;
        try {
            replacement = openAppenderFor(options);
        } catch (RuntimeException e) {
            System.err.println("thislog: cannot apply the logging configuration for '" + this.name + "' ("
                    + e + "); keeping the previous configuration");
            return;
        }
        this.appender.close();
        this.appender = replacement;
        this.options = options;
        this.currentLevel = options.getLevel();
        this.closed = false;
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
