package io.github.ferizoozoo.thislog;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

final class FileStreams {

    private static final Map<String, Entry> OPEN = new HashMap<>();

    private static final class Entry {
        final PrintStream stream;
        int holders = 1;

        Entry(PrintStream stream) {
            this.stream = stream;
        }
    }

    static synchronized PrintStream acquire(String path) {
        var key = canonical(path);
        var entry = OPEN.get(key);
        if (entry != null) {
            entry.holders++;
            return entry.stream;
        }
        var opened = open(path);
        OPEN.put(key, new Entry(opened));
        return opened;
    }

    static synchronized void release(String path) {
        var key = canonical(path);
        var entry = OPEN.get(key);
        if (entry == null) {
            return;
        }
        if (--entry.holders == 0) {
            OPEN.remove(key);
            try {
                entry.stream.flush();  
            } catch (Exception e) {
            } finally {
                entry.stream.close();
            }
        }
    }

    private static String canonical(String path) {
        try {
            return new File(path).getCanonicalPath();
        } catch (IOException e) {
            return new File(path).getAbsolutePath();
        }
    }

    private static PrintStream open(String path) {
        try {
            return new PrintStream(
                    new BufferedOutputStream(new FileOutputStream(path, true)), false);
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
    }

    private FileStreams() {
    }
}
