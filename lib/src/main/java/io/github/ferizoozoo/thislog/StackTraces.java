package io.github.ferizoozoo.thislog;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class StackTraces {

    private static final String LINE_SEPARATOR = System.lineSeparator();

    private static final StackTraceElement[] NO_FRAMES = new StackTraceElement[0];

    static String render(Throwable thrown) {
        var trace = new StringBuilder();
        append(trace, thrown, NO_FRAMES, "", "",
                Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>()));
        return trace.substring(LINE_SEPARATOR.length());
    }

    private static void append(StringBuilder trace, Throwable thrown, StackTraceElement[] enclosing,
            String caption, String prefix, Set<Throwable> seen) {
        var newline = LINE_SEPARATOR + prefix;
        if (!seen.add(thrown)) {
            trace.append(newline).append(caption).append("[CIRCULAR REFERENCE: ").append(thrown).append(']');
            return;
        }

        var frames = thrown.getStackTrace();
        var mine = frames.length - 1;
        var theirs = enclosing.length - 1;
        while (mine >= 0 && theirs >= 0 && frames[mine].equals(enclosing[theirs])) {
            mine--;
            theirs--;
        }

        trace.append(newline).append(caption).append(thrown);
        for (var i = 0; i <= mine; i++) {
            trace.append(newline).append("\tat ").append(frames[i]);
        }
        if (mine < frames.length - 1) {
            trace.append(newline).append("\t... ").append(frames.length - 1 - mine).append(" more");
        }

        for (var suppressed : thrown.getSuppressed()) {
            append(trace, suppressed, frames, "Suppressed: ", prefix + "\t", seen);
        }
        if (thrown.getCause() != null) {
            append(trace, thrown.getCause(), frames, "Caused by: ", prefix, seen);
        }
    }

    private StackTraces() {
    }
}
