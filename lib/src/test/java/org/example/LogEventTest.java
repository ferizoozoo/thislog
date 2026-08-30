package org.example;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * The log event: everything the logger knows about a single message.
 *
 * <p>An event is no longer something a caller builds. {@link Logging} creates
 * one inside each level method and hands it to the formatter, so the event's
 * four values are all decided by the library: the message and level come from
 * the call, the timestamp from the logger's clock, and the thread name from
 * whoever is running. The constructor is private, which is what makes those
 * guarantees hold rather than merely being the convention.
 *
 * <p>Two jobs are worth testing separately: being a faithful carrier of what
 * it was created with, and arriving at the formatter intact.
 */
public class LogEventTest {

    private static final String ESC = String.valueOf((char) 27);
    private static final String RESET = ESC + "[0m";
    private static final String GREEN = ESC + "[32m";

    private static final String NL = System.lineSeparator();

    private static final long TIMEOUT_MS = 5_000;

    /** A timestamp far enough in the past to be distinguishable from "now". */
    private static final long AT = 1_700_000_000_000L;

    // ---------------------------------------------------------------------
    // The event as a value.
    // ---------------------------------------------------------------------

    @Test
    public void anEventCarriesTheMessageLevelAndTimestampItWasCreatedWith() {
        var logEvent = LogEvent.create("Hello, World!", LogLevel.WARN, AT);

        assertEquals("Hello, World!", logEvent.getMessage());
        assertEquals(LogLevel.WARN, logEvent.getLevel());
        assertEquals(AT, logEvent.getTimestamp());
    }

    @Test
    public void anEventIsStampedWithTheThreadThatCreatedIt() {
        assertEquals(Thread.currentThread().getName(),
                LogEvent.create("here", LogLevel.INFO, AT).getThreadName());
    }

    @Test
    public void anEventCreatedOnAWorkerCarriesTheWorkersName() throws Exception {
        var built = new AtomicReference<LogEvent>();

        onThreadNamed("worker-1", () -> built.set(LogEvent.create("there", LogLevel.INFO, AT)));

        assertEquals("the thread name is read at creation, and cannot be supplied",
                "worker-1", built.get().getThreadName());
    }

    @Test
    public void readingAnEventDoesNotChangeIt() {
        var logEvent = LogEvent.create("read twice", LogLevel.INFO, AT);

        assertEquals(logEvent.getMessage(), logEvent.getMessage());
        assertEquals(logEvent.getTimestamp(), logEvent.getTimestamp());
        assertSame(logEvent.getLevel(), logEvent.getLevel());
        assertEquals(logEvent.getThreadName(), logEvent.getThreadName());
    }

    @Test
    public void theLevelIsTheEnumConstantItself() {
        var logEvent = LogEvent.create("boom", LogLevel.ERROR, AT);

        assertSame(LogLevel.ERROR, logEvent.getLevel());
        assertEquals(LogLevel.ERROR.severity(), logEvent.getLevel().severity());
    }

    @Test
    public void aMessageIsKeptVerbatim() {
        var awkward = "  two  spaces, a tab\t, a newline\n and a trailing space ";

        assertEquals("an event stores the message, it does not tidy it",
                awkward, LogEvent.create(awkward, LogLevel.INFO, AT).getMessage());
    }

    @Test
    public void aMessageContainingFormatSpecifiersIsNotInterpreted() {
        var looksLikeAFormat = "100%% done, %s of %d";

        assertEquals(looksLikeAFormat,
                LogEvent.create(looksLikeAFormat, LogLevel.INFO, AT).getMessage());
    }

    @Test
    public void anEmptyMessageIsKept() {
        assertEquals("", LogEvent.create("", LogLevel.INFO, AT).getMessage());
    }

    @Test
    public void aNullMessageIsAccepted() {
        // create() validates nothing, so a null message is stored and handed
        // on. aNullMessageDoesNotBringTheLoggerDown covers what happens next.
        assertNull(LogEvent.create(null, LogLevel.INFO, AT).getMessage());
    }

    @Test
    public void aNullLevelIsAccepted() {
        // Reachable only by calling create() directly: the logger always
        // supplies a real level, so this cannot arrive through info() and the
        // rest.
        assertNull(LogEvent.create("no level", null, AT).getLevel());
    }

    @Test
    public void aTimestampOfZeroIsKeptRatherThanTreatedAsAbsent() {
        assertEquals(0L, LogEvent.create("at the epoch", LogLevel.INFO, 0L).getTimestamp());
    }

    @Test
    public void aNegativeTimestampIsKept() {
        assertEquals(-1L, LogEvent.create("before the epoch", LogLevel.INFO, -1L).getTimestamp());
    }

    @Test
    public void theLargestTimestampIsKept() {
        assertEquals(Long.MAX_VALUE,
                LogEvent.create("far future", LogLevel.INFO, Long.MAX_VALUE).getTimestamp());
    }

    // ---------------------------------------------------------------------
    // The event is immutable, and only the library can build one.
    // ---------------------------------------------------------------------

    @Test
    public void theConstructorIsNotPublic() {
        for (Constructor<?> constructor : LogEvent.class.getDeclaredConstructors()) {
            assertFalse("an event must only be reachable through create(...), "
                            + "or a caller could forge a thread name",
                    Modifier.isPublic(constructor.getModifiers()));
        }
    }

    @Test
    public void everyFieldIsFinal() {
        for (Field field : LogEvent.class.getDeclaredFields()) {
            assertTrue(field.getName() + " should be final",
                    Modifier.isFinal(field.getModifiers()));
        }
    }

    @Test
    public void anEventExposesNoSetters() {
        List<String> setters = Arrays.stream(LogEvent.class.getMethods())
                .map(Method::getName)
                .filter(name -> name.startsWith("set"))
                .toList();

        assertEquals("an event should be read-only once built", List.of(), setters);
    }

    @Test
    public void twoEventsCreatedFromTheSameValuesAreSeparateObjects() {
        var one = LogEvent.create("same", LogLevel.INFO, AT);
        var other = LogEvent.create("same", LogLevel.INFO, AT);

        // Characterises the design as it stands: LogEvent is a plain class
        // with no equals/hashCode, so two events matching field for field are
        // still distinct. LogDestination, by contrast, is a record.
        assertNotSame(one, other);
        assertNotEquals(one, other);
        assertEquals(one.getMessage(), other.getMessage());
    }

    // ---------------------------------------------------------------------
    // The event on the logging path.
    // ---------------------------------------------------------------------

    /** Keeps the events the logger hands to the formatter. */
    private static final class EventRecorder implements LogFormatter {
        private final List<LogEvent> events = new ArrayList<>();

        @Override
        public String format(LogEvent event) {
            events.add(event);
            return String.valueOf(event.getMessage());
        }

        LogEvent only() {
            assertEquals("expected exactly one formatted line", 1, events.size());
            return events.get(0);
        }
    }

    /** Fails the first format, then records, so the recovery event is captured. */
    private static final class FailsOnceThenRecords implements LogFormatter {
        private final EventRecorder delegate;
        private boolean armed = true;

        FailsOnceThenRecords(EventRecorder delegate) {
            this.delegate = delegate;
        }

        @Override
        public String format(LogEvent event) {
            if (armed) {
                armed = false;
                throw new IllegalStateException("formatter exploded");
            }
            return delegate.format(event);
        }
    }

    private final PrintStream realStdout = System.out;
    private ByteArrayOutputStream stdout;

    @Before
    public void redirectStandardOut() {
        stdout = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
    }

    @After
    public void restoreStandardOut() {
        System.setOut(realStdout);
    }

    private String stdoutText() {
        return stdout.toString(StandardCharsets.UTF_8);
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

    @Test
    public void theLoggerBuildsAnEventCarryingTheCallItWasGiven() {
        var recorder = new EventRecorder();

        logger(recorder).warn("disk filling up");

        assertEquals("disk filling up", recorder.only().getMessage());
        assertEquals(LogLevel.WARN, recorder.only().getLevel());
    }

    @Test
    public void theLoggerStampsTheEventAtTheMomentOfTheCall() {
        var recorder = new EventRecorder();

        long before = System.currentTimeMillis();
        logger(recorder).info("stamped now");
        long after = System.currentTimeMillis();

        long stamped = recorder.only().getTimestamp();
        assertTrue("expected a timestamp between " + before + " and " + after + ", got " + stamped,
                stamped >= before && stamped <= after);
    }

    @Test
    public void theLoggerStampsTheEventWithTheThreadThatLogged() throws Exception {
        var recorder = new EventRecorder();
        var log = logger(recorder);

        onThreadNamed("worker-1", () -> log.info("logged elsewhere"));

        assertEquals("worker-1", recorder.only().getThreadName());
        assertNotEquals(Thread.currentThread().getName(), recorder.only().getThreadName());
    }

    @Test
    public void eachCallProducesItsOwnEvent() {
        var recorder = new EventRecorder();
        var log = logger(recorder);

        log.info("first");
        log.info("second");

        assertEquals(2, recorder.events.size());
        assertNotSame(recorder.events.get(0), recorder.events.get(1));
        assertEquals(List.of("first", "second"),
                recorder.events.stream().map(LogEvent::getMessage).toList());
    }

    @Test
    public void aFilteredCallNeverBuildsAnEventTheFormatterCanSee() {
        var recorder = new EventRecorder();
        var log = logger(recorder);

        log.setCurrentLevel(LogLevel.ERROR);
        log.info("below the threshold");

        assertEquals(List.of(), recorder.events);
    }

    @Test
    public void theRecoveryPathBuildsItsOwnErrorEvent() {
        var recorder = new EventRecorder();
        var log = logger(new FailsOnceThenRecords(recorder));

        long before = System.currentTimeMillis();
        log.info("never formatted");
        long after = System.currentTimeMillis();

        LogEvent recovery = recorder.only();
        assertEquals(LogLevel.ERROR, recovery.getLevel());
        assertEquals("Failed to format log message", recovery.getMessage());
        assertEquals("the failure rides on the event, not inside the message text",
                "formatter exploded", recovery.getThrown().getMessage());
        assertEquals(Thread.currentThread().getName(), recovery.getThreadName());
        assertTrue("the recovery event should be stamped when the failure happened, got "
                        + recovery.getTimestamp(),
                recovery.getTimestamp() >= before && recovery.getTimestamp() <= after);
    }

    // ---------------------------------------------------------------------
    // What a formatter can build out of an event.
    //
    // The event is what makes a rich line possible without the library
    // deciding on a layout: everything a formatter needs is on it.
    // ---------------------------------------------------------------------

    @Test
    public void aFormatterCanRenderEveryPartOfTheEvent() {
        var seen = new AtomicReference<LogEvent>();
        LogFormatter everything = logEvent -> {
            seen.set(logEvent);
            return logEvent.getTimestamp()
                    + " [" + logEvent.getLevel().name() + "]"
                    + " (" + logEvent.getThreadName() + ") "
                    + logEvent.getMessage();
        };

        logger(everything).warn("disk filling up");

        assertEquals(seen.get().getTimestamp() + " [WARN] ("
                        + Thread.currentThread().getName() + ") disk filling up" + NL,
                stdoutText());
    }

    @Test
    public void theShippedFormatterReadsTheMessageOffTheEvent() {
        logger(new PatternFormatter(PatternFormatter.DEFAULT_PATTERN)).info("Hello, World!");

        assertEquals("Hello, World!" + NL, stdoutText());
    }

    @Test
    public void aNullMessageDoesNotBringTheLoggerDown() {
        logger(new PatternFormatter("%s")).info(null);

        assertEquals("a null message should render as null rather than throw",
                "null" + NL, stdoutText());
    }
}
