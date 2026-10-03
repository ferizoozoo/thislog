package io.github.ferizoozoo.thislog;

/**
 * Writes log events somewhere.
 *
 * <p>An appender handed to a logger, through {@link LogOptions#withAppender} or
 * {@link Loggable#addAppender}, belongs to that logger from then on: the logger
 * flushes it, and closes it when the logger is closed or moves off it. Do not
 * close it yourself, and do not hand the same instance to a second logger.
 *
 * <p>A logger calls an appender under its own lock, so an appender used by one
 * logger needs no locking of its own.
 */
public interface Appender extends AutoCloseable {

    /** Writes one event. Must not throw for a destination that fails. */
    void append(LogEvent event);

    /** Pushes anything buffered out to the destination. */
    void flush();

    /** Lets go of the destination. An appender is not used after this. */
    @Override
    void close();

    /**
     * Reports a failed destination, once.
     *
     * <p>Returns {@code true} the first time it is called after a write or flush
     * has failed, and {@code false} on every call after that. The logger prints
     * a notice whenever this returns {@code true}, so an appender that answered
     * {@code true} every time would repeat that notice on every severe line.
     */
    boolean checkFailure();
}
