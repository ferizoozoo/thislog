package org.example;

public class LogEvent {
    private final String message;
    private final long timestamp;
    private final LogLevel level;
    private final String threadName;

    private LogEvent(String message, long timestamp, LogLevel level, String threadName) {
        this.message = message;
        this.timestamp = timestamp;
        this.level = level;
        this.threadName = threadName;
    }

    public static LogEvent create(String message, LogLevel level, long timestamp) {
        return new LogEvent(message, timestamp, level, Thread.currentThread().getName());
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
}