package io.github.ferizoozoo.thislog;

public class LogOptions {
    private LogDestination destination;
    private LogFormatter formatter;

    private LogOptions() {
    }

    public static LogOptions initiateOptions() {
        return fromEnvironment();
    }

    private static LogOptions fromEnvironment() {
        var options = new LogOptions();
        options.destination = LogDestination.create(System.getenv().getOrDefault("LOG_DESTINATION", "stdout"));
        options.formatter = PatternFormatter
                .create(System.getenv().getOrDefault("LOG_FORMATTER", PatternFormatter.DEFAULT_PATTERN));
        return options;
    }

    public LogOptions setDestination(LogDestination destination) {
        this.destination = destination;
        return this;
    }

    public LogDestination getDestination() {
        return this.destination;
    }

    public LogOptions setFormatter(LogFormatter formatter) {
        this.formatter = formatter;
        return this;
    }

    public LogFormatter getFormatter() {
        return this.formatter;
    }
}
