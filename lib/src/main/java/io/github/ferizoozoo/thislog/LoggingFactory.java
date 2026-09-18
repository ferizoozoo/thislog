package io.github.ferizoozoo.thislog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public final class LoggingFactory {

    private static final Map<String, Loggable> LOGGERS = new ConcurrentHashMap<>();

    private static final Set<Loggable> LIVE =
            Collections.newSetFromMap(new WeakHashMap<Loggable, Boolean>());

    static void track(Loggable logger) {
        synchronized (LIVE) {
            LIVE.add(logger);
        }
    }

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
        track(logger);
    }

    public static boolean has(String name) {
        return LOGGERS.containsKey(name);
    }

    public static void clear() {
        Flusher.stop();
        for (Loggable logger : LOGGERS.values()) {
            try {
                logger.close();
            } catch (RuntimeException e) {
                System.err.println("thislog: could not close '" + logger.getName() + "' (" + e + ")");
            }
        }
        LOGGERS.clear();
    }

    static void flushAll() {
        List<Loggable> snapshot;
        synchronized (LIVE) {
            snapshot = new ArrayList<>(LIVE);
        }
        for (Loggable logger : snapshot) {
            try {
                logger.flush();
            } catch (RuntimeException e) {
                System.err.println("thislog: could not flush '" + logger.getName() + "' (" + e + ")");
            }
        }
    }

    private LoggingFactory() {
    }
}
