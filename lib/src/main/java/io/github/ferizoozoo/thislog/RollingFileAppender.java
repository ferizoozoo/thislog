package io.github.ferizoozoo.thislog;

import java.util.Objects;

public class RollingFileAppender implements Appender {
    private final Appender delegate;
    private volatile boolean closed;
    private volatile LogLevel logLevel = LogLevel.TRACE;

    private RollingFileAppender(String path, LogFormatter formatter, long intervalMs) {
        this.delegate = StreamAppender.create(new RollingFile(path, intervalMs), formatter);
    }

    public static RollingFileAppender create(String path, LogFormatter formatter, long intervalMs) {
        return new RollingFileAppender(path, formatter, intervalMs);
    }

    @Override
    public void append(LogEvent event) {
        if (this.closed) {
            return;
        }
        this.delegate.append(event);
    }

    @Override
    public void flush() {
        if (this.closed) {
            return;
        }
        this.delegate.flush();
    }

    @Override
    public synchronized void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.delegate.close();
    }

    @Override
    public boolean checkFailure() {
        return this.delegate.checkFailure();
    }

    @Override
    public LogLevel getLogLevel() {
        return this.logLevel;
    }

    @Override
    public void setLogLevel(LogLevel level) {
        this.logLevel = Objects.requireNonNull(level, "level");
    }
}
