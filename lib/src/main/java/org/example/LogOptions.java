package org.example;

public class LogOptions {
    private LogDestination destination;
    private LogFormatter formatter;

    public static LogOptions initiateOptions() {
        return new LogOptions();
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
