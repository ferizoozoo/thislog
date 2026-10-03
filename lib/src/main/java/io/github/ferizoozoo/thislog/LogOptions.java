package io.github.ferizoozoo.thislog;

import java.util.Objects;

public class LogOptions {
    public static final LogLevel DEFAULT_LEVEL = LogLevel.INFO;

    private final LogDestination destination;
    private final LogFormatter formatter;
    private final LogLevel level;
    private final Appender appender;

    private LogOptions(LogDestination destination, LogFormatter formatter, LogLevel level, Appender appender) {
        this.destination = destination;
        this.formatter = formatter;
        this.level = Objects.requireNonNull(level, "level");
        this.appender = appender;
    }

    public static LogOptions createFromEnvironment() {
        try {
            var destination = LogDestination.create(System.getenv().getOrDefault("LOG_DESTINATION", "stdout"));
            var formatter = PatternFormatter
                    .create(System.getenv().getOrDefault("LOG_FORMATTER", PatternFormatter.DEFAULT_PATTERN));
            var level = LogLevel.create(System.getenv().getOrDefault("LOG_LEVEL", DEFAULT_LEVEL.name()));
            var options = new LogOptions(destination, formatter, level, null);
            return options;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to create LogOptions from environment variables", e);
        }

    }

    public static LogOptions createFromParameter(LogDestination destination, LogFormatter formatter, LogLevel level) {
        return new LogOptions(destination, formatter, level, null);
    }

    public LogOptions withDestination(LogDestination destination) {
        return new LogOptions(destination, this.formatter, this.level, this.appender);
    }

    public LogDestination getDestination() {
        return this.destination;
    }

    public LogOptions withFormatter(LogFormatter formatter) {
        return new LogOptions(this.destination, formatter, this.level, this.appender);
    }

    public LogFormatter getFormatter() {
        return this.formatter;
    }

    public LogOptions withLevel(LogLevel level) {
        return new LogOptions(this.destination, this.formatter, level, this.appender);
    }

    public LogLevel getLevel() {
        return this.level;
    }

    public LogOptions withAppender(Appender appender) {
        return new LogOptions(this.destination, this.formatter, this.level, appender);
    }

    public Appender getAppender() {
        return this.appender;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LogOptions that)) {
            return false;
        }
        return this.level == that.level
                && Objects.equals(this.destination, that.destination)
                && Objects.equals(this.formatter, that.formatter)
                && Objects.equals(this.appender, that.appender);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.destination, this.formatter, this.level, this.appender);
    }
}
