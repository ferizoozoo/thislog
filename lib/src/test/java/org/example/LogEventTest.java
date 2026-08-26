package org.example;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import static org.junit.Assert.assertEquals;
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
 * <p>An event is the unit the whole library now moves around. A caller builds
 * one and hands it to a level method; the logger reads its level to decide
 * whether to keep it and hands the whole thing to the formatter, which reads
 * whichever parts its layout calls for. So the event has two jobs worth
 * testing separately: being a faithful carrier of the four things it was
 * built with, and arriving at the formatter unchanged.
 */
public class LogEventTest {

    private static final String ESC = String.valueOf((char) 27);
    private static final String RESET = ESC + "[0m";
    private static final String GREEN = ESC + "[32m";

    private static final String NL = System.lineSeparator();

    /** A timestamp far enough in the past to be distinguishable from "now". */
    private static final long AT = 1_700_000_000_000L;

    private static LogEvent event(LogLevel level, String message) {
        return new LogEvent(message, AT, level, Thread.currentThread().getName());
    }

    // ---------------------------------------------------------------------
    // The event as a value.
    // ---------------------------------------------------------------------

    @Test
    public void anEventCarriesTheFourThingsItWasBuiltWith() {
        var logEvent = new LogEvent("Hello, World!", AT, LogLevel.WARN, "worker-1");

        assertEquals("Hello, World!", logEvent.getMessage());
        assertEquals(AT, logEvent.getTimestamp());
        assertEquals(LogLevel.WARN, logEvent.getLevel());
        assertEquals("worker-1", logEvent.getThreadName());
    }

    @Test
    public void readingAnEventDoesNotChangeIt() {
        var logEvent = event(LogLevel.INFO, "read twice");

        assertEquals(logEvent.getMessage(), logEvent.getMessage());
        assertEquals(logEvent.getTimestamp(), logEvent.getTimestamp());
        assertSame(logEvent.getLevel(), logEvent.getLevel());
        assertEquals(logEvent.getThreadName(), logEvent.getThreadName());
    }

    @Test
    public void theLevelIsTheEnumConstantItself() {
        var logEvent = event(LogLevel.ERROR, "boom");

        assertSame("an event should hold the level, not a copy of its name",
                LogLevel.ERROR, logEvent.getLevel());
        assertEquals(LogLevel.ERROR.severity(), logEvent.getLevel().severity());
    }

    @Test
    public void aMessageIsKeptVerbatim() {
        var awkward = "  two  spaces, a tab\t, a newline\n and a trailing space ";

        assertEquals("an event stores the message, it does not tidy it",
                awkward, new LogEvent(awkward, AT, LogLevel.INFO, "main").getMessage());
    }

    @Test
    public void aMessageContainingFormatSpecifiersIsNotInterpreted() {
        var looksLikeAFormat = "100%% done, %s of %d";

        assertEquals(looksLikeAFormat,
                new LogEvent(looksLikeAFormat, AT, LogLevel.INFO, "main").getMessage());
    }

    @Test
    public void anEmptyMessageIsKept() {
        assertEquals("", new LogEvent("", AT, LogLevel.INFO, "main").getMessage());
    }

    @Test
    public void aNullMessageIsAccepted() {
        // Characterises what the constructor does today: it validates nothing,
        // so a null message is stored and handed on to the formatter as null.
        assertNull(new LogEvent(null, AT, LogLevel.INFO, "main").getMessage());
    }

    @Test
    public void aNullLevelIsAccepted() {
        // Nothing rejects this at construction; the logger is where it goes
        // wrong, which anEventWithNoLevelIsReportedRatherThanThrown covers.
        assertNull(new LogEvent("no level", AT, null, "main").getLevel());
    }

    @Test
    public void aNullThreadNameIsAccepted() {
        assertNull(new LogEvent("no thread", AT, LogLevel.INFO, null).getThreadName());
    }

    @Test
    public void aTimestampOfZeroIsKeptRatherThanTreatedAsAbsent() {
        assertEquals(0L, new LogEvent("at the epoch", 0L, LogLevel.INFO, "main").getTimestamp());
    }

    @Test
    public void aNegativeTimestampIsKept() {
        assertEquals(-1L, new LogEvent("before the epoch", -1L, LogLevel.INFO, "main").getTimestamp());
    }

    @Test
    public void theLargestTimestampIsKept() {
        assertEquals(Long.MAX_VALUE,
                new LogEvent("far future", Long.MAX_VALUE, LogLevel.INFO, "main").getTimestamp());
    }

    @Test
    public void anEventDoesNotStampItselfWithTheTimeItWasBuilt() {
        var logEvent = event(LogLevel.INFO, "stamped by the caller");

        assertEquals("the timestamp is the caller's to supply, not the event's to invent",
                AT, logEvent.getTimestamp());
    }

    // ---------------------------------------------------------------------
    // The event is immutable, which is what makes it safe to hand to a
    // formatter and to log more than once.
    // ---------------------------------------------------------------------

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
    public void twoEventsBuiltFromTheSameValuesAreSeparateObjects() {
        var one = new LogEvent("same", AT, LogLevel.INFO, "main");
        var other = new LogEvent("same", AT, LogLevel.INFO, "main");

        // Characterises the design as it stands: LogEvent is a plain class
        // with no equals/hashCode, so two events comparing equal field for
        // field are still distinct values. LogDestination, by contrast, is a
        // record and does compare by value.
        assertNotSame(one, other);
        assertNotEquals(one, other);
        assertEquals(one.getMessage(), other.getMessage());
    }

    // ---------------------------------------------------------------------
    // The event on the logging path.
    // ---------------------------------------------------------------------

    /** Keeps the event objects themselves, so identity is assertable. */
    private static final class EventRecorder implements Formatable {
        private final List<LogEvent> events = new ArrayList<>();
        private final List<List<Object>> extras = new ArrayList<>();

        @Override
        public String getFormattedString(LogEvent event, Object... args) {
            events.add(event);
            extras.add(Arrays.asList(args));
            return String.valueOf(event.getMessage());
        }

        LogEvent only() {
            assertEquals("expected exactly one formatted line", 1, events.size());
            return events.get(0);
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

    private static Loggable logger(Formatable formatter) {
        return Logging.getLogger(formatter, LogOptions.initiateOptions());
    }

    @Test
    public void theFormatterIsHandedTheVeryEventThatWasLogged() {
        var recorder = new EventRecorder();
        var logEvent = event(LogLevel.INFO, "on the wire");

        logger(recorder).info(logEvent);

        assertSame("the logger should pass the event along, not rebuild it",
                logEvent, recorder.only());
    }

    @Test
    public void theEventArrivesAtTheFormatterUnchanged() {
        var recorder = new EventRecorder();

        logger(recorder).warn(new LogEvent("intact", AT, LogLevel.WARN, "worker-1"));

        assertEquals("intact", recorder.only().getMessage());
        assertEquals(AT, recorder.only().getTimestamp());
        assertEquals(LogLevel.WARN, recorder.only().getLevel());
        assertEquals("worker-1", recorder.only().getThreadName());
    }

    @Test
    public void theLoggerDoesNotRestampTheEvent() {
        var recorder = new EventRecorder();

        logger(recorder).info(event(LogLevel.INFO, "stamped long ago"));

        assertEquals("the logger must not overwrite the caller's timestamp",
                AT, recorder.only().getTimestamp());
    }

    @Test
    public void oneEventCanBeLoggedRepeatedlyWithoutBeingConsumed() {
        var recorder = new EventRecorder();
        var log = logger(recorder);
        var reused = event(LogLevel.INFO, "said three times");

        log.info(reused);
        log.info(reused);
        log.info(reused);

        assertEquals(List.of(reused, reused, reused), recorder.events);
    }

    @Test
    public void twoLoggersCanShareOneEvent() {
        var first = new EventRecorder();
        var second = new EventRecorder();
        var shared = event(LogLevel.INFO, "shared");

        logger(first).info(shared);
        logger(second).info(shared);

        assertSame(shared, first.only());
        assertSame(shared, second.only());
    }

    @Test
    public void theEventsThreadNameIsWhatTravelsAlongsideItAsAnExtraArgument() {
        var recorder = new EventRecorder();

        logger(recorder).info(new LogEvent("elsewhere", AT, LogLevel.INFO, "worker-1"));

        assertEquals("the extra argument is the event's thread name, not the logging thread's",
                List.of("worker-1"), recorder.extras.get(0));
    }

    @Test
    public void aFilteredEventNeverReachesTheFormatterAtAll() {
        var recorder = new EventRecorder();
        var log = logger(recorder);

        log.setCurrentLevel(LogLevel.ERROR);
        log.info(event(LogLevel.INFO, "below the threshold"));

        assertEquals(List.of(), recorder.events);
    }

    @Test
    public void anEventWithNoLevelIsReportedRatherThanThrown() {
        var recorder = new EventRecorder();

        logger(recorder).info(new LogEvent("no level", AT, null, "main"));

        // The threshold check dereferences the level, so a null one fails
        // before the event is ever formatted. The logger catches it and
        // reports through the same path a broken formatter takes: a fresh
        // ERROR event carrying the failure.
        assertEquals(LogLevel.ERROR, recorder.only().getLevel());
        assertNotEquals("the reported event is the logger's, not the caller's",
                "no level", recorder.only().getMessage());
    }

    @Test
    public void theEventTheRecoveryPathBuildsIsStampedWhenItFailed() {
        var recorder = new EventRecorder();
        var log = logger(new FailsOnceThenRecords(recorder));

        long before = System.currentTimeMillis();
        log.info(event(LogLevel.INFO, "never formatted"));
        long after = System.currentTimeMillis();

        LogEvent recovery = recorder.only();
        assertEquals(LogLevel.ERROR, recovery.getLevel());
        assertEquals("formatter exploded", recovery.getMessage());
        assertEquals(Thread.currentThread().getName(), recovery.getThreadName());
        assertTrue("the recovery event should be stamped when the failure happened, got "
                        + recovery.getTimestamp(),
                recovery.getTimestamp() >= before && recovery.getTimestamp() <= after);
        assertNotEquals("it must not inherit the failed event's timestamp",
                AT, recovery.getTimestamp());
    }

    /** Fails the first format, then records, so the recovery event is captured. */
    private static final class FailsOnceThenRecords implements Formatable {
        private final EventRecorder delegate;
        private boolean armed = true;

        FailsOnceThenRecords(EventRecorder delegate) {
            this.delegate = delegate;
        }

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
    // What a formatter can build out of an event.
    //
    // The event is what makes a rich line possible without the library
    // deciding on a layout: everything a formatter needs is on it.
    // ---------------------------------------------------------------------

    @Test
    public void aFormatterCanRenderEveryPartOfTheEvent() {
        Formatable everything = (logEvent, args) -> logEvent.getTimestamp()
                + " [" + logEvent.getLevel().name() + "]"
                + " (" + logEvent.getThreadName() + ") "
                + logEvent.getMessage();

        logger(everything).warn(new LogEvent("disk filling up", AT, LogLevel.WARN, "worker-1"));

        assertEquals(AT + " [WARN] (worker-1) disk filling up" + NL, stdoutText());
    }

    @Test
    public void theShippedFormatterReadsTheMessageOffTheEvent() {
        logger(new Formatter(Formatable.DEFAULT_FORMAT)).info(event(LogLevel.INFO, "Hello, World!"));

        assertEquals(GREEN + "Hello, World!" + RESET + NL, stdoutText());
    }

    @Test
    public void aNullMessageDoesNotBringTheLoggerDown() {
        logger(new Formatter("%s%s" + RESET)).info(new LogEvent(null, AT, LogLevel.INFO, "main"));

        assertEquals("a null message should render as null rather than throw",
                GREEN + "null" + RESET + NL, stdoutText());
    }
}
