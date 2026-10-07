package io.github.ferizoozoo.thislog;

import java.util.Locale;
import java.util.Objects;

public enum LogLevel {
    TRACE(10), DEBUG(20), INFO(30), WARN(40), ERROR(50), FATAL(60);

    private final int severity;

    LogLevel(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return this.severity;
    }

    public static LogLevel create(String level) {
        Objects.requireNonNull(level, "level");
        return switch (level.trim().toUpperCase(Locale.ROOT)) {
            case "TRACE" -> TRACE;
            case "DEBUG" -> DEBUG;
            case "INFO" -> INFO;
            case "WARN" -> WARN;
            case "ERROR" -> ERROR;
            case "FATAL" -> FATAL;
            default -> throw new IllegalArgumentException("Invalid log level: " + level);
        };
    }
}
