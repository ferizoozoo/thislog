package io.github.ferizoozoo.thislog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Logging implements Loggable {

    private static final LogLevel FLUSH_THRESHOLD = LogLevel.ERROR;

    private final String name;
    private volatile LogLevel currentLevel = LogLevel.TRACE;
    private volatile LogOptions options;

    private List<Appender> appenders;
    private volatile boolean closed;
    private boolean appenderFailureReported;

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
        this.appenders = List.of(opened);
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
        // The options describe the first appender only; the ones handed to
        // addAppender are not theirs to replace.
        var updated = new ArrayList<>(this.appenders);
        var previous = updated.set(0, replacement);
        this.appenders = List.copyOf(updated);
        guarded(previous, Appender::close);
        this.options = options;
        this.currentLevel = options.getLevel();
        this.closed = false;
    }

    @Override
    public synchronized void addAppender(Appender appender) {
        Objects.requireNonNull(appender, "appender");
        if (this.closed) {
            throw new IllegalStateException("logger '" + this.name + "' is closed");
        }
        if (this.appenders.contains(appender)) {
            return;
        }
        var updated = new ArrayList<>(this.appenders);
        updated.add(appender);
        this.appenders = List.copyOf(updated);
        // Whether an appender buffers is its own business, so every added one
        // gets the flush timer and the flush on exit.
        Flusher.appenderAdded();
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
        for (Appender appender : this.appenders) {
            guarded(appender, Appender::flush);
            guarded(appender, Appender::close);
        }
        this.appenders = List.of(Appenders.discardingAppender());
        this.closed = true;
    }

    @Override
    public synchronized void flush() {
        for (Appender appender : this.appenders) {
            guarded(appender, Appender::flush);
        }
    }

    private synchronized void write(LogEvent logEvent) {
        for (Appender appender : this.appenders) {
            guarded(appender, each -> each.append(logEvent));
        }
        if (logEvent.getLevel().severity() >= FLUSH_THRESHOLD.severity()) {
            flushAndReportWriteFailure();
        }
    }

    private void flushAndReportWriteFailure() {
        for (Appender appender : this.appenders) {
            guarded(appender, each -> {
                each.flush();
                if (each.checkFailure()) {
                    System.err.println("thislog: the destination for '" + this.name
                            + "' is failing; log output may be lost");
                }
            });
        }
    }

    // An appender can be anyone's code now, and one that throws must neither
    // escape a logging call nor cost the other appenders their turn.
    private void guarded(Appender appender, Consumer<Appender> action) {
        try {
            action.accept(appender);
        } catch (RuntimeException e) {
            if (!this.appenderFailureReported) {
                this.appenderFailureReported = true;
                System.err.println("thislog: an appender for '" + this.name + "' threw (" + e
                        + "); log output may be lost");
            }
        }
    }
}
