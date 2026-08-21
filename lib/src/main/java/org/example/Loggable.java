package org.example;

public interface Loggable {
    Loggable setOptions(LogOptions options);

    void info(String message);
    void warn(String message);
    void error(String message);
    void trace(String message);
    void fatal(String message);
    void debug(String message);
}