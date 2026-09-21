package io.github.ferizoozoo.thislog;

import java.util.function.Supplier;

public interface Loggable extends AutoCloseable {
    String getName();

    @Override
    void close();

    void flush();

    void changeOptions(LogOptions options);

    LogOptions getOptions();

    LogLevel getCurrentLogLevel();

    void setCurrentLogLevel(LogLevel level);

    boolean isEnabled(LogLevel level);

    void log(LogLevel level, Supplier<String> message, Throwable thrown);

    default void trace(Supplier<String> message, Throwable thrown) {
        log(LogLevel.TRACE, message, thrown);
    }

    default void debug(Supplier<String> message, Throwable thrown) {
        log(LogLevel.DEBUG, message, thrown);
    }

    default void info(Supplier<String> message, Throwable thrown) {
        log(LogLevel.INFO, message, thrown);
    }

    default void warn(Supplier<String> message, Throwable thrown) {
        log(LogLevel.WARN, message, thrown);
    }

    default void error(Supplier<String> message, Throwable thrown) {
        log(LogLevel.ERROR, message, thrown);
    }

    default void fatal(Supplier<String> message, Throwable thrown) {
        log(LogLevel.FATAL, message, thrown);
    }

    default void trace(Supplier<String> message) {
        log(LogLevel.TRACE, message, null);
    }

    default void debug(Supplier<String> message) {
        log(LogLevel.DEBUG, message, null);
    }

    default void info(Supplier<String> message) {
        log(LogLevel.INFO, message, null);
    }

    default void warn(Supplier<String> message) {
        log(LogLevel.WARN, message, null);
    }

    default void error(Supplier<String> message) {
        log(LogLevel.ERROR, message, null);
    }

    default void fatal(Supplier<String> message) {
        log(LogLevel.FATAL, message, null);
    }

    default void log(LogLevel level, String message, Throwable thrown) {
        log(level, () -> message, thrown);
    }

    default void trace(String message, Throwable thrown) {
        log(LogLevel.TRACE, message, thrown);
    }

    default void debug(String message, Throwable thrown) {
        log(LogLevel.DEBUG, message, thrown);
    }

    default void info(String message, Throwable thrown) {
        log(LogLevel.INFO, message, thrown);
    }

    default void warn(String message, Throwable thrown) {
        log(LogLevel.WARN, message, thrown);
    }

    default void error(String message, Throwable thrown) {
        log(LogLevel.ERROR, message, thrown);
    }

    default void fatal(String message, Throwable thrown) {
        log(LogLevel.FATAL, message, thrown);
    }

    default void trace(String message) {
        log(LogLevel.TRACE, message, null);
    }

    default void debug(String message) {
        log(LogLevel.DEBUG, message, null);
    }

    default void info(String message) {
        log(LogLevel.INFO, message, null);
    }

    default void warn(String message) {
        log(LogLevel.WARN, message, null);
    }

    default void error(String message) {
        log(LogLevel.ERROR, message, null);
    }

    default void fatal(String message) {
        log(LogLevel.FATAL, message, null);
    }
}
