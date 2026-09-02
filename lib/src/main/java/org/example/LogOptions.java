package org.example;

public class LogOptions {
    private LogDestination destination;
    private LogFormatter formatter;

    private LogOptions() {
    }

    /**
     * Options that carry nothing, so every field is the caller's to set.
     */
    public static LogOptions initiateOptions() {
        return new LogOptions();
    }

    /**
     * Options seeded from LOG_DESTINATION and LOG_FORMATTER, falling back to
     * stdout and the default pattern when the environment says nothing.
     */
    public static LogOptions fromEnvironment() {
        var options = new LogOptions();
        options.destination = LogDestination.create(System.getenv().getOrDefault("LOG_DESTINATION", "stdout"));
        options.formatter = PatternFormatter.create(System.getenv().getOrDefault("LOG_FORMATTER", PatternFormatter.DEFAULT_PATTERN));
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
