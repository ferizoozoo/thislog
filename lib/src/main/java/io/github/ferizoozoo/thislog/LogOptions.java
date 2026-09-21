package io.github.ferizoozoo.thislog;

import java.util.Objects;

public class LogOptions {
    public static final LogLevel DEFAULT_LEVEL = LogLevel.INFO;

    private final LogDestination destination;
    private final LogFormatter formatter;
    private final LogLevel level;

    private LogOptions(LogDestination destination, LogFormatter formatter, LogLevel level) {
        this.destination = destination;
        this.formatter = formatter;
        this.level = Objects.requireNonNull(level, "level");
    }

    public static LogOptions createFromEnvironment() {
        var options = new LogOptions(
                LogDestination.create(System.getenv().getOrDefault("LOG_DESTINATION", "stdout")),
                PatternFormatter
                        .create(System.getenv().getOrDefault("LOG_FORMATTER", PatternFormatter.DEFAULT_PATTERN)),
                LogLevel.create(System.getenv().getOrDefault("LOG_LEVEL", DEFAULT_LEVEL.name())));
        return options;
    }

    public static LogOptions createFromParameter(LogDestination destination, LogFormatter formatter, LogLevel level) {
        return new LogOptions(destination, formatter, level);
    }

    public LogOptions withDestination(LogDestination destination) {
        return new LogOptions(destination, this.formatter, this.level);
    }

    public LogDestination getDestination() {
        return this.destination;
    }

    public LogOptions withFormatter(LogFormatter formatter) {
        return new LogOptions(this.destination, formatter, this.level);
    }

    public LogFormatter getFormatter() {
        return this.formatter;
    }

    public LogOptions withLevel(LogLevel level) {
        return new LogOptions(this.destination, this.formatter, level);
    }

    public LogLevel getLevel() {
        return this.level;
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
                && Objects.equals(this.formatter, that.formatter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.destination, this.formatter, this.level);
    }
}
