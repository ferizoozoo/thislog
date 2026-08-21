package org.example;

public class LogOptions {
    private LogDestination destination;

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
}
