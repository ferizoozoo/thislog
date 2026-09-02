package org.example;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class LoggingFactory {

    private static final Map<String, Loggable> LOGGERS = new ConcurrentHashMap<>();

    public static Loggable get(String name) {
        Objects.requireNonNull(name, "name");
        return LOGGERS.computeIfAbsent(name, Logging::new);
    }

    public static Loggable get(Class<?> type) {
        Objects.requireNonNull(type, "type");
        return get(type.getName());
    }

    public static Loggable get(String name, LogFormatter formatter, LogOptions options) {
        var logger = get(name);
        logger.setFormatter(formatter);
        logger.setOptions(options);
        return logger;
    }

    public static Loggable get(Class<?> type, LogFormatter formatter, LogOptions options) {
        Objects.requireNonNull(type, "type");
        return get(type.getName(), formatter, options);
    }

    public static void add(String name, Loggable logger) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(logger, "logger");
        if (!name.equals(logger.getName())) {
            throw new IllegalArgumentException(
                    "logger is named %s, cannot register it as %s".formatted(logger.getName(), name));
        }
        LOGGERS.put(name, logger);
    }

    public static boolean has(String name) {
        return LOGGERS.containsKey(name);
    }

    public static void clear() {
        for (Loggable logger : LOGGERS.values()) {
            logger.close();
        }
        LOGGERS.clear();
    }

    private LoggingFactory() {
    }
}
