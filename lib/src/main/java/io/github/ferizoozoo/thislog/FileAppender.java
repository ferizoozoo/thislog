package io.github.ferizoozoo.thislog;

public class FileAppender implements Appender {
    private final String path;
    private final Appender delegate;
    private volatile boolean closed;

    private FileAppender(String path, LogFormatter formatter) {
        this.path = path;
        this.delegate = StreamAppender.wrapping(FileStreams.acquire(path), formatter);
    }

    public static FileAppender create(String path, LogFormatter formatter) {
        return new FileAppender(path, formatter);
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
        FileStreams.release(this.path);
    }

    @Override
    public boolean checkFailure() {
        return this.delegate.checkFailure();
    }
}
