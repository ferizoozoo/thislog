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
import static org.junit.Assert.assertNull;
import org.junit.Before;
import org.junit.Test;

/**
 * What the logger carries alongside the message.
 *
 * <p>The thread name is the one piece of context the logger renders today, and
 * it now arrives on the {@link LogEvent} rather than being read off the
 * running thread. So the rule has changed shape: the name the formatter sees
 * is whatever the caller put on the event, and the logger neither supplies one
 * nor corrects a wrong one. Everything here follows from that.
 *
 * <p>The logger still routes the name through a {@link Context}, writing the
 * event's name in and reading it straight back out within the same call. That
 * makes the context invisible from outside except in one respect worth
 * guarding: nothing may linger in it from one event to the next.
 *
 * <p>{@link ContextTest} covers {@link Context} on its own; this covers the
 * logger's use of it. {@link LogEventTest} covers the event itself.
 */
public class LoggingContextTest {

    private static final long TIMEOUT_MS = 5_000;

    private static final long AT = 1_700_000_000_000L;

    /** An event naming the thread it was built on, as a caller would build it. */
    private static LogEvent event(String message) {
        return new LogEvent(message, AT, LogLevel.INFO, Thread.currentThread().getName());
    }

    /** An event claiming a thread name of its own, whoever logs it. */
    private static LogEvent eventFrom(String threadName, String message) {
        return new LogEvent(message, AT, LogLevel.INFO, threadName);
    }

    private record Call(String message, List<Object> args) {
        /** The thread name, or null when the logger passed nothing at all. */
        Object threadName() {
            return args.isEmpty() ? null : args.get(0);
        }
    }

    /** Shared across threads by several tests, so the list must be too. */
    private static final class Recorder implements Formatable {
        private final List<Call> calls = Collections.synchronizedList(new ArrayList<>());

        @Override
        public String getFormattedString(LogEvent event, Object... args) {
            calls.add(new Call(event.getMessage(), Arrays.asList(args)));
            return String.valueOf(event.getMessage());
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
        public String getFormattedString(LogEvent event, Object... args) {
            if (armed) {
                armed = false;
                throw new IllegalStateException("formatter exploded");
            }
            return delegate.getFormattedString(event, args);
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
    // The name on the event is the name the formatter gets.
    // ---------------------------------------------------------------------

    @Test
    public void theEventsThreadNameIsHandedToTheFormatter() {
        var recorder = new Recorder();

        logger(recorder).info(event("on this thread"));

        assertEquals(thisThread(), recorder.only().threadName());
    }

    @Test
    public void theLoggerDoesNotReplaceTheEventsThreadNameWithItsOwn() {
        var recorder = new Recorder();

        logger(recorder).info(eventFrom("worker-1", "claims another thread"));

        assertEquals("the logger reports what the event says, not where it was logged",
            "worker-1", recorder.only().threadName());
        assertNotEquals(thisThread(), recorder.only().threadName());
    }

    @Test
    public void anEventBuiltOnOneThreadKeepsThatNameWhenLoggedOnAnother() throws Exception {
        var recorder = new Recorder();
        var log = logger(recorder);
        var built = new AtomicReference<LogEvent>();

        onThreadNamed("worker", () -> built.set(event("built on the worker")));
        log.info(built.get());

        assertEquals("the name is fixed when the event is built, not when it is logged",
            "worker", recorder.only().threadName());
    }

    @Test
    public void aNullThreadNameIsPassedAlongAsNull() {
        var recorder = new Recorder();

        logger(recorder).info(new LogEvent("nameless", AT, LogLevel.INFO, null));

        assertNull("nothing fills in a missing thread name", recorder.only().threadName());
    }

    @Test
    public void neitherTheBuildingThreadNorTheLoggingThreadOverridesTheEvent() throws Exception {
        var recorder = new Recorder();
        var built = new AtomicReference<Loggable>();

        onThreadNamed("builder", () -> built.set(logger(recorder)));
        built.get().info(eventFrom("worker-1", "neither builder nor test thread"));

        assertEquals("worker-1", recorder.only().threadName());
    }

    // ---------------------------------------------------------------------
    // Nothing lingers between events.
    //
    // The name is routed through a Context the logger keeps for the life of
    // the logger, so a stale entry would show up as one event wearing the
    // previous one's name.
    // ---------------------------------------------------------------------

    @Test
    public void oneEventsThreadNameDoesNotLeakIntoTheNext() {
        var recorder = new Recorder();
        var log = logger(recorder);

        log.info(eventFrom("worker-1", "first"));
        log.info(eventFrom("worker-2", "second"));

        assertEquals(List.of("worker-1", "worker-2"), recorder.threadNames());
    }

    @Test
    public void aNamelessEventDoesNotInheritThePreviousOnesName() {
        var recorder = new Recorder();
        var log = logger(recorder);

        log.info(eventFrom("worker-1", "named"));
        log.info(new LogEvent("nameless", AT, LogLevel.INFO, null));

        assertEquals(Arrays.asList("worker-1", null), recorder.threadNames());
    }

    @Test
    public void theSecondMessageCarriesTheThreadNameToo() {
        var recorder = new Recorder();
        var log = logger(recorder);

        log.info(event("first"));
        log.info(event("second"));

        assertEquals("logging a message must read the context, not consume it",
            List.of(thisThread(), thisThread()), recorder.threadNames());
    }

    @Test
    public void everyMessageInALongRunCarriesTheThreadName() {
        var recorder = new Recorder();
        var log = logger(recorder);

        for (int i = 0; i < 50; i++) {
            log.info(event("message " + i));
        }

        assertEquals(50, recorder.calls.size());
        assertEquals(Collections.nCopies(50, thisThread()), recorder.threadNames());
    }

    @Test
    public void theThreadNameIsCarriedAtEveryLevel() {
        var recorder = new Recorder();
        var log = logger(recorder);

        log.trace(new LogEvent("t", AT, LogLevel.TRACE, "worker-1"));
        log.debug(new LogEvent("d", AT, LogLevel.DEBUG, "worker-1"));
        log.info(new LogEvent("i", AT, LogLevel.INFO, "worker-1"));
        log.warn(new LogEvent("w", AT, LogLevel.WARN, "worker-1"));
        log.error(new LogEvent("e", AT, LogLevel.ERROR, "worker-1"));
        log.fatal(new LogEvent("f", AT, LogLevel.FATAL, "worker-1"));

        assertEquals(Collections.nCopies(6, "worker-1"), recorder.threadNames());
    }

    @Test
    public void aDroppedMessageDoesNotCostTheNextOneItsThreadName() {
        var recorder = new Recorder();
        var log = logger(recorder);

        log.setCurrentLevel(LogLevel.INFO);
        log.debug(new LogEvent("dropped before it reaches the formatter", AT,
                LogLevel.DEBUG, "worker-1"));
        log.info(eventFrom("worker-2", "kept"));

        assertEquals("filtering a message must leave the context as it found it",
            "worker-2", recorder.forMessage("kept").threadName());
    }

    @Test
    public void reconfiguringTheLoggerDoesNotCostItTheThreadName() {
        var recorder = new Recorder();
        var log = logger(recorder);

        log.info(event("before"));
        log.setOptions(LogOptions.initiateOptions().setFormatter(recorder));
        log.info(event("after"));

        assertEquals(List.of(thisThread(), thisThread()), recorder.threadNames());
    }

    // ---------------------------------------------------------------------
    // The recovery path is the one place the logger names a thread itself.
    // ---------------------------------------------------------------------

    @Test
    public void aFailedFormatIsReportedUnderTheThreadThatWasLogging() {
        var formatter = new FailsOnce();

        logger(formatter).info(eventFrom("worker-1", "this one blows the formatter up"));

        assertEquals("the recovery event is the logger's own, stamped with the running thread",
            thisThread(), formatter.delegate.only().threadName());
        assertNotEquals("it does not inherit the failed event's thread name",
            "worker-1", formatter.delegate.only().threadName());
    }

    @Test
    public void aFailedFormatDoesNotCostTheNextMessageItsThreadName() {
        var formatter = new FailsOnce();
        var log = logger(formatter);

        log.info(event("this one blows the formatter up"));
        log.info(eventFrom("worker-2", "this one should still be intact"));

        assertEquals("the recovery path must not leave the context stripped",
            "worker-2",
            formatter.delegate.forMessage("this one should still be intact").threadName());
    }

    // ---------------------------------------------------------------------
    // Several threads through one logger.
    // ---------------------------------------------------------------------

    @Test
    public void eachThreadSeesItsOwnNameThroughASharedLogger() throws Exception {
        var recorder = new Recorder();
        var log = logger(recorder);

        onThreadNamed("worker-one", () -> log.info(event("one")));
        onThreadNamed("worker-two", () -> log.info(event("two")));
        log.info(event("three"));

        assertEquals("worker-one", recorder.forMessage("one").threadName());
        assertEquals("worker-two", recorder.forMessage("two").threadName());
        assertEquals(thisThread(), recorder.forMessage("three").threadName());
    }

    @Test
    public void oneThreadLoggingDoesNotStripAnotherThreadsThreadName() throws Exception {
        var recorder = new Recorder();
        var log = logger(recorder);

        log.info(event("before the worker ran"));
        onThreadNamed("worker", () -> log.info(event("on the worker")));
        log.info(event("after the worker ran"));

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
            log.info(event("first on this thread"));
            log.info(event("second on this thread"));
        });

        assertEquals("a thread the logger was not built on has no context to inherit",
            List.of("worker", "worker"), recorder.threadNames());
    }
}
