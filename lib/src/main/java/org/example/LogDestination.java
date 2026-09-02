package org.example;

public sealed interface LogDestination {

    record Stdout() implements LogDestination {}

    record Stderr() implements LogDestination {}

    record LogFile(String path) implements LogDestination {}

    LogDestination STDOUT = new Stdout();
    LogDestination STDERR = new Stderr();
    LogDestination FILE = new LogFile("log.txt");

    public static LogDestination create(String destination) {
        return switch (destination.toLowerCase()) {
            case "stdout" -> STDOUT;
            case "stderr" -> STDERR;
            case "file" -> FILE;
            default -> throw new IllegalArgumentException("Unknown log destination: " + destination);
        };
    }

    static LogDestination file(String path) {
        return new LogFile(path);
    }
}
