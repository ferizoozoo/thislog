package org.example;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import org.junit.Before;
import org.junit.Test;

/**
 * What the logger carries alongside the message.
 *
 * <p>The thread name is the one piece of context the logger renders today, so
 * it is also the only handle these tests have on the context's lifetime. Two
 * rules govern it: it names the thread that made the call, not the one that
 * built the logger; and it is still there on the hundredth message, because
 * logging a message reads the context rather than consuming it.
 *
 * <p>{@link ContextTest} covers {@link Context} on its own. This covers the
 * logger's use of it. A caller cannot put keys of their own into the context
 * today, so the thread name is all there is to assert; the rest arrives when
 * the formatter takes a log event instead of a pre-flattened string.
 */
public class LoggingContextTest {

    private static final long TIMEOUT_MS = 5_000;

    private record Call(LogLevel level, String message, List<Object> args) {
        /** The thread name, or null when the logger passed nothing at all. */
        Object threadName() {
            return args.isEmpty() ? null : args.get(0);
        }
    }

    /** Shared across threads by several tests, so the list must be too. */
    private static final class Recorder implements Formatable {
        private final List<Call> calls = Collections.synchronizedList(new ArrayList<>());

        @Override
        public String getFormattedString(LogLevel level, String message, Object... args) {
            calls.add(new Call(level, message, Arrays.asList(args)));
            return level.name() + " " + message;
        }

        Call only() {
            assertEquals("expected exactly one formatted line", 1, calls.size());
            return calls.get(0);
        }

        List<Object> threadNames() {
            return calls.stream().map(Call::threadName).toList();
        }

        Call forMessage(String message) {
            return calls.stream()
                    .filter(call -> call.message().equals(message))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no line was formatted for " + message));
        }
    }

    /** Throws on its first call only, so the recovery path runs mid-sequence. */
    private static final class FailsOnce implements Formatable {
        private final Recorder delegate = new Recorder();
        private boolean armed = true;

        @Override
        public String getFormattedString(LogLevel level, String message, Object... args) {
            if (armed) {
                armed = false;
                throw new IllegalStateException("formatter exploded");
            }
            return delegate.getFormattedString(level, message, args);
        }
    }

    // ---------------------------------------------------------------------
    // Logging goes to stdout unless told otherwise; keep it out of the build
    // output, since these tests read the formatter rather than the sink.
    // ---------------------------------------------------------------------

    private final PrintStream realStdout = System.out;

    @Before
    public void silenceStandardOut() {
        System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    @After
    public void restoreStandardOut() {
        System.setOut(realStdout);
    }

    private static Loggable logger(Formatable formatter) {
        return Logging.getLogger(formatter, LogOptions.initiateOptions());
    }

    /** Runs {@code work} to completion on a fresh thread with the given name. */
    private static void onThreadNamed(String name, Runnable work) throws Exception {
        var failure = new AtomicReference<Throwable>();

        var thread = new Thread(() -> {
            try {
                work.run();
            } catch (Throwable t) {
                failure.set(t);
            }
        }, name);

        thread.start();
        thread.join(TIMEOUT_MS);

        assertFalse(name + " did not finish within " + TIMEOUT_MS + "ms", thread.isAlive());
        if (failure.get() != null) {
            throw new AssertionError("work on " + name + " failed", failure.get());
        }
    }

    private static String thisThread() {
        return Thread.currentThread().getName();
    }

    // ---------------------------------------------------------------------
    // The thread name outlives the first message.
    //
    // LoggingTest already covers a single message on the calling thread, which
    // is the one case a context consumed by logging still gets right.
    // ---------------------------------------------------------------------

    @Test
    public void theSecondMessageCarriesTheThreadNameToo() {
        var recorder = new Recorder();
        var log = logger(recorder);

        log.info("first");
        log.info("second");

        assertEquals("logging a message must read the context, not consume it",
            List.of(thisThread(), thisThread()), recorder.threadNames());
    }

    @Test
    public void everyMessageInALongRunCarriesTheThreadName() {
        var recorder = new Recorder();
        var log = logger(recorder);

        for (int i = 0; i < 50; i++) {
            log.info("message " + i);
        }

        assertEquals(50, recorder.calls.size());
        assertEquals(Collections.nCopies(50, thisThread()), recorder.threadNames());
    }

    @Test
    public void theThreadNameIsCarriedAtEveryLevel() {
        var recorder = new Recorder();
        var log = logger(recorder);

        log.trace("t");
        log.debug("d");
        log.info("i");
        log.warn("w");
        log.error("e");
        log.fatal("f");

        assertEquals(Collections.nCopies(6, thisThread()), recorder.threadNames());
    }

    @Test
    public void aDroppedMessageDoesNotCostTheNextOneItsThreadName() {
        var recorder = new Recorder();
        var log = logger(recorder);

        log.setCurrentLevel(LogLevel.INFO);
        log.debug("dropped before it reaches the formatter");
        log.info("kept");

        assertEquals("filtering a message must leave the context as it found it",
            thisThread(), recorder.forMessage("kept").threadName());
    }

    @Test
    public void aFailedFormatDoesNotCostTheNextMessageItsThreadName() {
        var formatter = new FailsOnce();
        var log = logger(formatter);

        log.info("this one blows the formatter up");
        log.info("this one should still be intact");

        assertEquals("the recovery path must not leave the context stripped",
            thisThread(), formatter.delegate.forMessage("this one should still be intact").threadName());
    }

    @Test
    public void reconfiguringTheLoggerDoesNotCostItTheThreadName() {
        var recorder = new Recorder();
        var log = logger(recorder);

        log.info("before");
        log.setOptions(LogOptions.initiateOptions().setFormatter(recorder));
        log.info("after");

        assertEquals(List.of(thisThread(), thisThread()), recorder.threadNames());
    }

    // ---------------------------------------------------------------------
    // The thread name is the caller's, not the builder's.
    // ---------------------------------------------------------------------

    @Test
    public void theThreadNameIsTheCallersRatherThanTheOneThatBuiltTheLogger() throws Exception {
        var recorder = new Recorder();
        var log = logger(recorder);

        onThreadNamed("worker", () -> log.info("logged elsewhere"));

        assertEquals("the name is read when the message is logged, not when the logger is built",
            "worker", recorder.only().threadName());
        assertNotEquals(thisThread(), recorder.only().threadName());
    }

    @Test
    public void aLoggerBuiltOnAWorkerReportsTheThreadThatLogs() throws Exception {
        var recorder = new Recorder();
        var built = new AtomicReference<Loggable>();

        onThreadNamed("builder", () -> built.set(logger(recorder)));
        built.get().info("logged on the test thread");

        assertEquals(thisThread(), recorder.only().threadName());
    }

    @Test
    public void eachThreadSeesItsOwnNameThroughASharedLogger() throws Exception {
        var recorder = new Recorder();
        var log = logger(recorder);

        onThreadNamed("worker-one", () -> log.info("one"));
        onThreadNamed("worker-two", () -> log.info("two"));
        log.info("three");

        assertEquals("worker-one", recorder.forMessage("one").threadName());
        assertEquals("worker-two", recorder.forMessage("two").threadName());
        assertEquals(thisThread(), recorder.forMessage("three").threadName());
    }

    @Test
    public void oneThreadLoggingDoesNotStripAnotherThreadsThreadName() throws Exception {
        var recorder = new Recorder();
        var log = logger(recorder);

        log.info("before the worker ran");
        onThreadNamed("worker", () -> log.info("on the worker"));
        log.info("after the worker ran");

        assertEquals(thisThread(), recorder.forMessage("before the worker ran").threadName());
        assertEquals("worker", recorder.forMessage("on the worker").threadName());
        assertEquals("a message on one thread must not disturb another thread's context",
            thisThread(), recorder.forMessage("after the worker ran").threadName());
    }

    @Test
    public void aThreadThatOnlyEverLogsStillGetsItsName() throws Exception {
        var recorder = new Recorder();
        var log = logger(recorder);

        onThreadNamed("worker", () -> {
            log.info("first on this thread");
            log.info("second on this thread");
        });

        assertEquals("a thread the logger was not built on has no context to inherit",
            List.of("worker", "worker"), recorder.threadNames());
    }

}
