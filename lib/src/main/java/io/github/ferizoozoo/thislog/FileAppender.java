package io.github.ferizoozoo.thislog;

// Files are shared and reference-counted by FileStreams, so closing this
// appender lets go of its hold instead of closing the file outright.
public class FileAppender implements Appender {
    private final String path;
    private final Appender delegate;

    private FileAppender(String path, LogFormatter formatter) {
        this.path = path;
        this.delegate = StreamAppender.wrapping(FileStreams.acquire(path), formatter);
    }

    public static FileAppender create(String path, LogFormatter formatter) {
        return new FileAppender(path, formatter);
    }

    @Override
    public void append(LogEvent event) {
        this.delegate.append(event);
    }

    @Override
    public void flush() {
        this.delegate.flush();
    }

    @Override
    public void close() {
        this.delegate.close();
        FileStreams.release(this.path);
    }

    @Override
    public boolean checkFailure() {
        return this.delegate.checkFailure();
    }
}
