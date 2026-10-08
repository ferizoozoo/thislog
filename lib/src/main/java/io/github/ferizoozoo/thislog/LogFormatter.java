package io.github.ferizoozoo.thislog;

import java.util.Objects;

@FunctionalInterface
public interface LogFormatter {
    String format(LogEvent event);

    default boolean rendersThrown() {
        return false;
    }

    static LogFormatter colored(LogFormatter delegate) {
        Objects.requireNonNull(delegate, "delegate");
        return new LogFormatter() {
            @Override
            public String format(LogEvent event) {
                return AsciiColors.coloredMessage(delegate.format(event), event.getLevel());
            }

            @Override
            public boolean rendersThrown() {
                return delegate.rendersThrown();
            }
        };
    }
}
