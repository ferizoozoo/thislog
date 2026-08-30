package org.example;

import java.util.Objects;

@FunctionalInterface
public interface LogFormatter {
    String format(LogEvent event);

    static LogFormatter colored(LogFormatter delegate) {
        Objects.requireNonNull(delegate, "delegate");
        return event -> LogLevel.color(event.getLevel())
                + delegate.format(event)
                + LogLevel.color(LogLevel.OFF);
    }
}
