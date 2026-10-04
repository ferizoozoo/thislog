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

        var applied = options;
        Appender appender;
        try {
            appender = openAppender(applied);
        } catch (RuntimeException e) {
            System.err.println("thislog: cannot apply the logging configuration for '" + name + "' ("
                    + e + "); falling back to stdout");
            applied = options.withDestination(LogDestination.STDOUT);
            appender = openAppender(applied);
        }

        this.options = applied;
        this.currentLevel = applied.getLevel();
        this.appenders = List.of(appender);
    }

    public static Logging create(String name, LogOptions options) {
        var logger = new Logging(name, options);
        LoggingFactory.track(logger);
        return logger;
    }

    private static Appender openAppender(LogOptions options) {
        var given = options.getAppender();
        if (given == null) {
            return Appenders.forDestination(options.getDestination(), options.getFormatter());
        }
        Flusher.appenderAdded();
        return given;
    }

    @Override
    public synchronized void changeOptions(LogOptions options) {
        Objects.requireNonNull(options, "options");
        Appender replacement;

        try {
            replacement = openAppender(options);
        } catch (RuntimeException e) {
            System.err.println("thislog: cannot apply the logging configuration for '" + this.name + "' ("
                    + e + "); keeping the previous configuration");
            return;
        }

        var updated = new ArrayList<>(this.appenders);
        var previous = updated.set(0, replacement);
        this.appenders = List.copyOf(updated);
        if (previous != replacement) {
            guarded(previous, Appender::close);
        }
        this.options = options;
        this.currentLevel = options.getLevel();
        this.closed = false;
        this.appenderFailureReported = false;
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
        Flusher.appenderAdded();
    }

    @Override
    public synchronized void removeAppender(Appender appender) {
        Objects.requireNonNull(appender, "appender");
        if (this.closed) {
            throw new IllegalStateException("logger '" + this.name + "' is closed");
        }

        var updated = new ArrayList<>(this.appenders);
        int index = updated.indexOf(appender);
        if (index < 0) {
            return;
        }
        if (index == 0) {
            throw new IllegalArgumentException("the appender from the options of '" + this.name
                    + "' is replaced through changeOptions, not removed");
        }
        updated.remove(index);
        this.appenders = List.copyOf(updated);

        guarded(appender, Appender::flush);
        guarded(appender, Appender::close);
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
            guarded(appender, each -> {
                if (accepts(each, logEvent.getLevel())) {
                    each.append(logEvent);
                }
            });
        }
        if (logEvent.getLevel().severity() >= FLUSH_THRESHOLD.severity()) {
            flushAndReportWriteFailure();
        }
    }

    private static boolean accepts(Appender appender, LogLevel level) {
        var threshold = appender.getLogLevel();
        return threshold == null || level.severity() >= threshold.severity();
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
