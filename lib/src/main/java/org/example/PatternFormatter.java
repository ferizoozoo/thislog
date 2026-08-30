package org.example;

import java.util.Objects;

public class PatternFormatter implements LogFormatter {
    public static final String DEFAULT_PATTERN = "%s";

    private final String pattern;

    public PatternFormatter(String pattern) {
        this.pattern = Objects.requireNonNull(pattern, "pattern");
    }

    @Override
    public String format(LogEvent event) {
        return String.format(this.pattern, event.getMessage());
    }
}
