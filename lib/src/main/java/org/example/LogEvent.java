package org.example;

public class LogEvent {
    private final String message;
    private final long timestamp;
    private final LogLevel level;
    private final String threadName;

    public LogEvent(String message, long timestamp, LogLevel level, String threadName) {
        this.message = message;
        this.timestamp = timestamp;
        this.level = level;
        this.threadName = threadName;
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