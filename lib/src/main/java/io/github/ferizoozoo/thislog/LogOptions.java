package io.github.ferizoozoo.thislog;

public class LogOptions {
    private final LogDestination destination;
    private final LogFormatter formatter;

    private LogOptions(LogDestination destination, LogFormatter formatter) {
        this.destination = destination;
        this.formatter = formatter;
    }

    public static LogOptions createFromEnvironment() {
        var options = new LogOptions(
            LogDestination.create(System.getenv().getOrDefault("LOG_DESTINATION", "stdout")),
            PatternFormatter.create(System.getenv().getOrDefault("LOG_FORMATTER", PatternFormatter.DEFAULT_PATTERN))
        );
        return options;
    }

    public static LogOptions createFromParameter(LogDestination destination, LogFormatter formatter) {
        return new LogOptions(destination, formatter);
    }

    public LogOptions withDestination(LogDestination destination) {
        return new LogOptions(destination, this.formatter);
    }

    public LogDestination getDestination() {
        return this.destination;
    }

    public LogOptions withFormatter(LogFormatter formatter) {
        return new LogOptions(this.destination, formatter);
    }

    public LogFormatter getFormatter() {
        return this.formatter;
    }
}
