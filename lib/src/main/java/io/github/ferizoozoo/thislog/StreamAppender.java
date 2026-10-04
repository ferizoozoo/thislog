package io.github.ferizoozoo.thislog;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class StreamAppender implements Appender {
    private static final String LINE_SEPARATOR = System.lineSeparator();

    private final OutputStream outputStream;
    private final LogFormatter formatter;
    private final boolean ownsStream;
    private boolean hasFailed = false;
    private boolean failureReported = false;

    private volatile LogLevel logLevel = LogLevel.TRACE;

    private StreamAppender(OutputStream outputStream, LogFormatter formatter, boolean ownsStream) {
        this.outputStream = outputStream;
        this.formatter = formatter;
        this.ownsStream = ownsStream;
    }

    public static StreamAppender create(OutputStream outputStream, LogFormatter formatter) {
        return new StreamAppender(outputStream, formatter, true);
    }

    public static StreamAppender wrapping(OutputStream outputStream, LogFormatter formatter) {
        return new StreamAppender(outputStream, formatter, false);
    }

    @Override
    public void append(LogEvent event) {
        String line;
        try {
            var logMessageBuilder = new StringBuilder(this.formatter.format(event));
            if (event.getThrown() != null) {
                logMessageBuilder.append(LINE_SEPARATOR)
                        .append(StackTraces.render(event.getThrown()));
            }
            line = logMessageBuilder.toString();
        } catch (Exception e) {
            reportFormattingFailure(event, e);
            return;
        }
        writeLine(line);
    }

    @Override
    public void flush() {
        if (this.outputStream instanceof PrintStream printStream) {
            if (printStream.checkError()) {
                this.hasFailed = true;
            }
            return;
        }
        try {
            this.outputStream.flush();
        } catch (Exception e) {
            this.hasFailed = true;
        }
    }

    @Override
    public void close() {
        if (!this.ownsStream) {
            return;
        }
        try {
            this.outputStream.close();
        } catch (Exception e) {
            this.hasFailed = true;
        }
    }

    @Override
    public boolean checkFailure() {
        if (!this.hasFailed || this.failureReported) {
            return false;
        }
        this.failureReported = true;
        return true;
    }

    private void writeLine(String line) {
        try {
            this.outputStream.write((line + LINE_SEPARATOR).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            this.hasFailed = true;
        }
    }

    private void reportFormattingFailure(LogEvent event, Exception failure) {
        String line;
        try {
            var recovery = LogEvent.create("Failed to format log message", LogLevel.ERROR,
                    System.currentTimeMillis(), event.getLoggerName(), failure);
            line = this.formatter.format(recovery);
        } catch (Exception alsoFailed) {
            line = LogLevel.coloredMessage("Failed to format log message: " + failure, LogLevel.ERROR);
        }
        writeLine(line);
        this.flush();
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
