package io.github.ferizoozoo.thislog;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The registry is what separates a factory from a constructor: the same name
 * has to come back as the same logger, or configuring one part of an
 * application silently leaves another part on the old settings.
 */
public class LoggingFactoryTest {

    private final PrintStream realStdout = System.out;

    private ByteArrayOutputStream written;

    @Before
    public void startClean() {
        LoggingFactory.clear();
        written = new ByteArrayOutputStream();
        System.setOut(new PrintStream(written, true, StandardCharsets.UTF_8));
    }

    @After
    public void restore() {
        LoggingFactory.clear();
        System.setOut(realStdout);
    }

    @Test
    public void oneNameAlwaysComesBackAsTheSameLogger() {
        assertSame(LoggingFactory.get("com.acme.db", plain()),
                LoggingFactory.get("com.acme.db", plain()));
    }

    @Test
    public void differentNamesAreDifferentLoggers() {
        assertNotSame(LoggingFactory.get("com.acme.db", plain()),
                LoggingFactory.get("com.acme.web", plain()));
    }

    @Test
    public void aClassNamesItsLoggerAfterItself() {
        assertSame(LoggingFactory.get(LoggingFactoryTest.class, plain()),
                LoggingFactory.get("io.github.ferizoozoo.thislog.LoggingFactoryTest", plain()));
    }

    @Test
    public void configuringByNameReachesTheLoggerSomebodyElseAlreadyHolds() {
        var held = LoggingFactory.get("com.acme.db", plain());
        var recorder = new Recorder();

        registered("com.acme.db", recorder);
        held.info("through the handle taken before configuration");

        assertEquals(List.of("through the handle taken before configuration"), recorder.messages);
    }

    @Test
    public void reconfiguringDoesNotHandBackANewLogger() {
        var first = registered("com.acme.db", new Recorder());
        var second = registered("com.acme.db", new Recorder());

        assertSame(first, second);
    }

    @Test
    public void aMessageLoggedOnOneNameLeavesAnotherAlone() {
        var dbRecorder = new Recorder();
        var webRecorder = new Recorder();
        var db = registered("com.acme.db", dbRecorder);
        var web = registered("com.acme.web", webRecorder);

        db.info("for the database logger");
        web.info("for the web logger");

        assertEquals(List.of("for the database logger"), dbRecorder.messages);
        assertEquals(List.of("for the web logger"), webRecorder.messages);
    }

    @Test
    public void anExplicitlyAddedLoggerIsWhatTheNameResolvesTo() {
        var standIn = Logging.create("com.acme.db", plain());

        LoggingFactory.add("com.acme.db", standIn);

        assertSame(standIn, LoggingFactory.get("com.acme.db", plain()));
    }

    @Test
    public void aLoggerCannotBeRegisteredUnderANameThatIsNotItsOwn() {
        var db = Logging.create("com.acme.db", plain());

        var refused = assertThrows(IllegalArgumentException.class,
                () -> LoggingFactory.add("com.acme.web", db));

        assertTrue(refused.getMessage(), refused.getMessage().contains("com.acme.db"));
        assertFalse(LoggingFactory.has("com.acme.web"));
    }

    @Test
    public void clearingForgetsEveryName() {
        LoggingFactory.get("com.acme.db", plain());
        assertTrue(LoggingFactory.has("com.acme.db"));

        LoggingFactory.clear();

        assertFalse(LoggingFactory.has("com.acme.db"));
    }

    @Test
    public void aNameIsRequired() {
        assertThrows(NullPointerException.class,
                () -> LoggingFactory.get((String) null, plain()));
        assertThrows(NullPointerException.class,
                () -> LoggingFactory.get((Class<?>) null, plain()));
        assertThrows("the options are required too", NullPointerException.class,
                () -> LoggingFactory.get("com.acme.db", null));
    }

    @Test
    public void racingThreadsStillAgreeOnOneInstance() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Loggable>> asking = IntStream.range(0, threads)
                    .<Callable<Loggable>>mapToObj(i -> () -> LoggingFactory.get("com.acme.contended", plain()))
                    .toList();

            var resolved = pool.invokeAll(asking).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            }).toList();

            assertEquals("every thread must see one logger",
                    1, resolved.stream().distinct().count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void aLoggerKnowsTheNameItWasRegisteredUnder() {
        assertEquals("com.acme.db", LoggingFactory.get("com.acme.db", plain()).getName());
        assertEquals("io.github.ferizoozoo.thislog.LoggingFactoryTest",
                LoggingFactory.get(LoggingFactoryTest.class, plain()).getName());
    }

    @Test
    public void everyEventCarriesTheNameOfTheLoggerThatBuiltIt() {
        var recorder = new EventRecorder();
        var log = registered("com.acme.db", recorder);

        log.info("through a convenience method");
        log.error("with a throwable", new IllegalStateException("boom"));

        assertEquals(List.of("com.acme.db", "com.acme.db"),
                recorder.events.stream().map(LogEvent::getLoggerName).toList());
    }

    @Test
    public void twoLoggersStampTheirOwnNamesNotEachOthers() {
        var dbRecorder = new EventRecorder();
        var webRecorder = new EventRecorder();
        registered("com.acme.db", dbRecorder).info("query ran");
        registered("com.acme.web", webRecorder).info("request served");

        assertEquals("com.acme.db", dbRecorder.events.get(0).getLoggerName());
        assertEquals("com.acme.web", webRecorder.events.get(0).getLoggerName());
    }

    @Test
    public void aLoggerTakenBeforeAnyConfigurationStillWrites() {
        var log = LoggingFactory.get("com.acme.unconfigured", plain());

        log.info("no formatter was ever set");

        assertEquals("the default options carry a formatter, so the line lands",
                "no formatter was ever set" + System.lineSeparator(), written.toString());
    }

    /** Options good enough to build a logger with; nothing is applied by them. */
    private static LogOptions plain() {
        return LogOptions.createFromEnvironment();
    }

    /**
     * A registered logger with its options actually applied. The factory
     * only builds a logger the first time a name is asked for, so changeOptions
     * is what configures one that may already exist.
     */
    private static Loggable registered(String name, LogFormatter formatter) {
        var log = LoggingFactory.get(name, plain());
        log.changeOptions(LogOptions.createFromEnvironment()
                .setFormatter(formatter)
                .setDestination(LogDestination.STDOUT));
        return log;
    }

    /** Keeps the events that reached formatting. */
    private static final class EventRecorder implements LogFormatter {
        private final List<LogEvent> events = new ArrayList<>();

        @Override
        public String format(LogEvent event) {
            events.add(event);
            return event.getMessage();
        }
    }

    /** Keeps the messages that reached formatting. */
    private static final class Recorder implements LogFormatter {
        private final List<String> messages = new ArrayList<>();

        @Override
        public String format(LogEvent event) {
            messages.add(event.getMessage());
            return event.getMessage();
        }
    }
}
