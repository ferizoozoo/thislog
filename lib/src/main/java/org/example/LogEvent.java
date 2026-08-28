package org.example;

public class LogEvent {
    private final String message;
    private final long timestamp;
    private final LogLevel level;
    private final String threadName;
    private final Throwable thrown;

    private LogEvent(String message, long timestamp, LogLevel level, String threadName, Throwable thrown) {
        this.message = message;
        this.timestamp = timestamp;
        this.level = level;
        this.threadName = threadName;
        this.thrown = thrown;
    }

    public static LogEvent create(String message, LogLevel level, long timestamp) {
        return create(message, level, timestamp, null);
    }

    public static LogEvent create(String message, LogLevel level, long timestamp,
                                  Throwable thrown) {
        return new LogEvent(message, timestamp, level,
                            Thread.currentThread().getName(), thrown);
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public LogLevel getLevel() {
        return level;
    }

    public String getThreadName() {
        return threadName;
    }

    public Throwable getThrown() {
        return thrown;
    }
}