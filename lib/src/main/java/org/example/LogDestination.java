package org.example;

public sealed interface LogDestination {

    record Stdout() implements LogDestination {}

    record Stderr() implements LogDestination {}

    record LogFile(String path) implements LogDestination {}

    LogDestination STDOUT = new Stdout();
    LogDestination STDERR = new Stderr();

    static LogDestination file(String path) {
        return new LogFile(path);
    }
}
