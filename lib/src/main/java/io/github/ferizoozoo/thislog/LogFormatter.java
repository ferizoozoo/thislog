package io.github.ferizoozoo.thislog;

import java.util.Objects;

@FunctionalInterface
public interface LogFormatter {
    String format(LogEvent event);

    static LogFormatter colored(LogFormatter delegate) {
        Objects.requireNonNull(delegate, "delegate");
        return event -> LogLevel.coloredMessage(delegate.format(event), event.getLevel());
    }
}
