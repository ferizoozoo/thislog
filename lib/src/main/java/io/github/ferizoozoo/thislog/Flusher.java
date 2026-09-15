package io.github.ferizoozoo.thislog;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class Flusher {

    private static final long DEFAULT_INTERVAL_MS = 2_000L;

    private static final Object LOCK = new Object();

    private static ScheduledExecutorService timer;
    private static long intervalMs = intervalFromEnvironment();
    private static boolean hookRegistered;

    static void destinationOpened(LogDestination destination) {
        if (!(destination instanceof LogDestination.LogFile)) {
            return;
        }
        synchronized (LOCK) {
            registerShutdownHookOnce();
            if (timer != null || intervalMs <= 0) {
                return;
            }
            timer = Executors.newSingleThreadScheduledExecutor(runnable -> {
                var thread = new Thread(runnable, "thislog-flush");
                thread.setDaemon(true);
                return thread;
            });
            timer.scheduleWithFixedDelay(LoggingFactory::flushAll,
                    intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        }
    }

    private static void registerShutdownHookOnce() {
        if (hookRegistered) {
            return;
        }
        hookRegistered = true;
        try {
            Runtime.getRuntime().addShutdownHook(
                    new Thread(LoggingFactory::flushAll, "thislog-shutdown"));
        } catch (IllegalStateException alreadyShuttingDown) {
        }
    }

    static void stop() {
        ScheduledExecutorService stopping;
        synchronized (LOCK) {
            stopping = timer;
            timer = null;
        }
        if (stopping == null) {
            return;
        }
        stopping.shutdown();
        try {
            stopping.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static long intervalFromEnvironment() {
        String raw = System.getenv("LOG_FLUSH_INTERVAL_MS");
        if (raw == null) {
            return DEFAULT_INTERVAL_MS;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            System.err.println("thislog: LOG_FLUSH_INTERVAL_MS is not a number (" + raw
                    + "); using " + DEFAULT_INTERVAL_MS + "ms");
            return DEFAULT_INTERVAL_MS;
        }
    }

    static void setIntervalForTesting(long millis) {
        stop();
        synchronized (LOCK) {
            intervalMs = millis;
        }
    }

    static void resetIntervalForTesting() {
        setIntervalForTesting(intervalFromEnvironment());
    }

    private Flusher() {
    }
}
