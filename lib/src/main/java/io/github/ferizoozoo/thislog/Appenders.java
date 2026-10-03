package io.github.ferizoozoo.thislog;

import java.io.OutputStream;

final class Appenders {

    private static final Appender DISCARD =
            StreamAppender.wrapping(OutputStream.nullOutputStream(), event -> "");

    private Appenders() {
    }

    static Appender forDestination(LogDestination destination, LogFormatter formatter) {
        return switch (destination) {
            case LogDestination.Stdout ignored ->
                StreamAppender.wrapping(System.out, formatter);
            case LogDestination.Stderr ignored ->
                StreamAppender.wrapping(System.err, formatter);
            case LogDestination.LogFile logFile -> {
                var appender = FileAppender.create(logFile.path(), formatter);
                Flusher.destinationOpened(logFile);
                yield appender;
            }
        };
    }

    static Appender discardingAppender() {
        return DISCARD;
    }
}
