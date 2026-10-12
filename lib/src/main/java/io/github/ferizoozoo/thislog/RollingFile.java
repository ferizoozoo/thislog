package io.github.ferizoozoo.thislog;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.zip.GZIPOutputStream;

final class RollingFile extends OutputStream {
    private final String baseFilename;
    private OutputStream out;
    private String currentFilename;
    private final long intervalMs;
    private Instant lastRollAt;

    public RollingFile(String filename, long intervalMs) {
        this.baseFilename = filename;
        this.currentFilename = this.baseFilename;
        this.intervalMs = intervalMs;
        this.lastRollAt = Instant.now();
        try {
            this.out = new BufferedOutputStream(new FileOutputStream(this.baseFilename));
        } catch (Exception e) {
            System.out.println("Could not open the file");
        }
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int len) throws IOException {
        if (shouldRoll()) {
            roll();
        }

        this.out.write(bytes, offset, len);
    }

    @Override
    public synchronized void write(int b) throws IOException {
        write(new byte[] { (byte) b }, 0, 1);
    }

    @Override
    public synchronized void flush() throws IOException {
        this.out.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        this.out.close();
    }

    private boolean shouldRoll() {
        return Duration.between(this.lastRollAt, Instant.now()).toMillis() >= intervalMs;
    }

    public synchronized void roll() throws IOException {
        this.out.close();
        var finishedFilename = this.currentFilename;
        this.currentFilename = this.generateFilename();
        this.out = new BufferedOutputStream(new FileOutputStream(this.currentFilename));
        this.lastRollAt = Instant.now();
        gzip(Path.of(finishedFilename));
    }

    private String generateFilename() throws IOException {
        int dot = baseFilename.lastIndexOf('.');
        if (dot == -1) {
            throw new IOException("Could not split the filename by a dot, because dot is not present");
        }
        var name = dot > 0 ? baseFilename.substring(0, dot) : baseFilename;
        var stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmssSSSSSS")
                .withZone(ZoneId.systemDefault())
                .format(lastRollAt);
        return name + stamp;
    }

    private void gzip(Path source) throws IOException {
        var target = source.resolveSibling(source.getFileName() + ".gz");
        while (Files.exists(target)) {
            target = source.resolveSibling(this.generateFilename() + ".gz");
        }
        var partial = source.resolveSibling(source.getFileName() + ".gz.tmp");
        try (var in = Files.newInputStream(source);
                var out = new GZIPOutputStream(Files.newOutputStream(partial))) {
            in.transferTo(out);
        }
        Files.move(partial, target);
        Files.delete(source);
    }

}
