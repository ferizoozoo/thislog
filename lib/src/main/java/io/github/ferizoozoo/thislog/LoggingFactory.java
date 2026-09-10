package io.github.ferizoozoo.thislog;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class LoggingFactory {

    private static final Map<String, Loggable> LOGGERS = new ConcurrentHashMap<>();

    public static Loggable get(Class<?> type, LogOptions options) {
        Objects.requireNonNull(type, "type");
        return get(type.getName(), options);
    }

    public static Loggable get(String name, LogOptions options) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(options, "options");
        return LOGGERS.computeIfAbsent(name, n -> Logging.create(n, options));
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
