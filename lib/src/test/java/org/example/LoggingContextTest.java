package org.example;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
 * <p>The thread name is the one piece of context the logger renders today, and
 * it is now read off the running thread at the moment of the call: each level
 * method builds a {@link LogEvent}, and {@code LogEvent.create} stamps
 * {@code Thread.currentThread().getName()} on it. A caller cannot supply one,
 * because the constructor is private.
 *
 * <p>So two rules govern it. It names the thread that made the call, not the
 * one that built the logger; and it is still there on the hundredth message,
 * because logging reads the thread rather than consuming anything.
 *
 * <p>{@link ContextTest} covers {@link Context} on its own. {@link LogEventTest}
 * covers the event. This covers what the logger puts on it.
 */
public class LoggingContextTest {

    private static final long TIMEOUT_MS = 5_000;

    /** Shared across threads by several tests, so the list must be too. */
    private static final class Recorder implements LogFormatter {
        private final List<LogEvent> events = Collections.synchronizedList(new ArrayList<>());

        @Override
        public String format(LogEvent event) {
            events.add(event);
            return String.valueOf(event.getMessage());
        }

        LogEvent only() {
            assertEquals("expected exactly one formatted line", 1, events.size());
            return events.get(0);
        }

        List<String> threadNames() {
            return events.stream().map(LogEvent::getThreadName).toList();
        }

        LogEvent forMessage(String message) {
            return events.stream()
                    .filter(event -> message.equals(event.getMessage()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no line was formatted for " + message));
        }
    }

    /** Throws on its first call only, so the recovery path runs mid-sequence. */
    private static final class FailsOnce implements LogFormatter {
        private final Recorder delegate = new Recorder();
        private boolean armed = true;

        @Override
        public String format(LogEvent event) {
            if (armed) {
                armed = false;
                throw new IllegalStateException("formatter exploded");
            }
            return delegate.format(event);
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

    private static Loggable logger(LogFormatter formatter) {
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
    // ---------------------------------------------------------------------

    @Test
    public void theFirstMessageCarriesTheThreadName() {
        var recorder = new Recorder();

        logger(recorder).info("first");

        assertEquals(thisThread(), recorder.only().getThreadName());
    }

    @Test
    public void theSecondMessageCarriesTheThreadNameToo() {
        var recorder = new Recorder();
        var log = logger(recorder);

        log.info("first");
        log.info("second");

        assertEquals("logging a message must read the thread, not consume anything",
            List.of(thisThread(), thisThread()), recorder.threadNames());
    }

    @Test
    public void everyMessageInALongRunCarriesTheThreadName() {
        var recorder = new Recorder();
        var log = logger(recorder);

        for (int i = 0; i < 50; i++) {
            log.info("message " + i);
        }

        assertEquals(50, recorder.events.size());
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

        assertEquals("filtering a message must leave the next one intact",
            thisThread(), recorder.forMessage("kept").getThreadName());
    }

    @Test
    public void aFailedFormatDoesNotCostTheNextMessageItsThreadName() {
        var formatter = new FailsOnce();
        var log = logger(formatter);

        log.info("this one blows the formatter up");
        log.info("this one should still be intact");

        assertEquals("the recovery path must not leave the next event stripped",
            thisThread(),
            formatter.delegate.forMessage("this one should still be intact").getThreadName());
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
            "worker", recorder.only().getThreadName());
        assertNotEquals(thisThread(), recorder.only().getThreadName());
    }

    @Test
    public void aLoggerBuiltOnAWorkerReportsTheThreadThatLogs() throws Exception {
        var recorder = new Recorder();
        var built = new AtomicReference<Loggable>();

        onThreadNamed("builder", () -> built.set(logger(recorder)));
        built.get().info("logged on the test thread");

        assertEquals(thisThread(), recorder.only().getThreadName());
    }

    @Test
    public void eachThreadSeesItsOwnNameThroughASharedLogger() throws Exception {
        var recorder = new Recorder();
        var log = logger(recorder);

        onThreadNamed("worker-one", () -> log.info("one"));
        onThreadNamed("worker-two", () -> log.info("two"));
        log.info("three");

        assertEquals("worker-one", recorder.forMessage("one").getThreadName());
        assertEquals("worker-two", recorder.forMessage("two").getThreadName());
        assertEquals(thisThread(), recorder.forMessage("three").getThreadName());
    }

    @Test
    public void oneThreadLoggingDoesNotStripAnotherThreadsThreadName() throws Exception {
        var recorder = new Recorder();
        var log = logger(recorder);

        log.info("before the worker ran");
        onThreadNamed("worker", () -> log.info("on the worker"));
        log.info("after the worker ran");

        assertEquals(thisThread(), recorder.forMessage("before the worker ran").getThreadName());
        assertEquals("worker", recorder.forMessage("on the worker").getThreadName());
        assertEquals("a message on one thread must not disturb another's",
            thisThread(), recorder.forMessage("after the worker ran").getThreadName());
    }

    @Test
    public void aThreadThatOnlyEverLogsStillGetsItsName() throws Exception {
        var recorder = new Recorder();
        var log = logger(recorder);

        onThreadNamed("worker", () -> {
            log.info("first on this thread");
            log.info("second on this thread");
        });

        assertEquals(List.of("worker", "worker"), recorder.threadNames());
    }

    @Test
    public void twoThreadsLoggingAtOnceEachKeepTheirOwnName() throws Exception {
        var recorder = new Recorder();
        var log = logger(recorder);
        var start = new CountDownLatch(1);

        var one = new Thread(() -> {
            await(start);
            for (int i = 0; i < 25; i++) {
                log.info("one-" + i);
            }
        }, "racer-one");

        var two = new Thread(() -> {
            await(start);
            for (int i = 0; i < 25; i++) {
                log.info("two-" + i);
            }
        }, "racer-two");

        one.start();
        two.start();
        start.countDown();
        one.join(TIMEOUT_MS);
        two.join(TIMEOUT_MS);

        assertFalse("racer-one did not finish", one.isAlive());
        assertFalse("racer-two did not finish", two.isAlive());
        assertEquals(50, recorder.events.size());

        synchronized (recorder.events) {
            for (LogEvent event : recorder.events) {
                String expected = event.getMessage().startsWith("one-") ? "racer-one" : "racer-two";
                assertEquals("every line must name the thread that logged it",
                    expected, event.getThreadName());
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
