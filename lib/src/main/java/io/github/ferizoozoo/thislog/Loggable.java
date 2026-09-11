package io.github.ferizoozoo.thislog;

import java.util.function.Supplier;

public interface Loggable extends AutoCloseable {
    String getName();

    @Override
    void close();

    void changeOptions(LogOptions options);

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
}
