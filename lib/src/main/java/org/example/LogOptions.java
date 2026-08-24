package org.example;

public class LogOptions {
    private LogDestination destination;
    private Formatable formatter;

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

    public LogOptions setFormatter(Formatable formatter) {
        this.formatter = formatter;
        return this;
    }

    public Formatable getFormatter() {
        return this.formatter;
    }
}
