package org.example;

public interface Loggable {
    Loggable setOptions(LogOptions options);

    Loggable setCurrentLevel(LogLevel level);

    void info(LogEvent logEvent);

    void warn(LogEvent logEvent);

    void error(LogEvent logEvent);

    void trace(LogEvent logEvent);

    void fatal(LogEvent logEvent);

    void debug(LogEvent logEvent);
}