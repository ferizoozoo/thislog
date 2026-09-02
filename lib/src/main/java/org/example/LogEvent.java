package org.example;

public class LogEvent {
    private final String message;
    private final long timestamp;
    private final LogLevel level;
    private final String threadName;
    private final Throwable thrown;
    private final String loggerName;

    private LogEvent(String message, long timestamp, LogLevel level, String threadName, Throwable thrown,
            String loggerName) {
        this.message = message;
        this.timestamp = timestamp;
        this.level = level;
        this.threadName = threadName;
        this.thrown = thrown;
        this.loggerName = loggerName;
    }

    public static LogEvent create(String message, LogLevel level, long timestamp, String loggerName) {
        return create(message, level, timestamp, loggerName, null);
    }

    public static LogEvent create(String message, LogLevel level, long timestamp, String loggerName,
            Throwable thrown) {
        return new LogEvent(message, timestamp, level,
                Thread.currentThread().getName(), thrown, loggerName);
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

    public String getLoggerName() {
        return loggerName;
    }
}
