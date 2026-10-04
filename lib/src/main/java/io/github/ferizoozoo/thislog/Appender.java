package io.github.ferizoozoo.thislog;

public interface Appender extends AutoCloseable {
    void append(LogEvent event);

    void flush();

    @Override
    void close();

    boolean checkFailure();

    LogLevel getLogLevel();

    void setLogLevel(LogLevel level);
}
