package org.example;

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
        assertSame(LoggingFactory.get("com.acme.db"), LoggingFactory.get("com.acme.db"));
    }

    @Test
    public void differentNamesAreDifferentLoggers() {
        assertNotSame(LoggingFactory.get("com.acme.db"), LoggingFactory.get("com.acme.web"));
    }

    @Test
    public void aClassNamesItsLoggerAfterItself() {
        assertSame(LoggingFactory.get(LoggingFactoryTest.class),
                LoggingFactory.get("org.example.LoggingFactoryTest"));
    }

    @Test
    public void configuringByNameReachesTheLoggerSomebodyElseAlreadyHolds() {
        var held = LoggingFactory.get("com.acme.db");
        var recorder = new Recorder();

        LoggingFactory.get("com.acme.db", recorder, LogOptions.initiateOptions());
        held.info("through the handle taken before configuration");

        assertEquals(List.of("through the handle taken before configuration"), recorder.messages);
    }

    @Test
    public void reconfiguringDoesNotHandBackANewLogger() {
        var first = LoggingFactory.get("com.acme.db", new Recorder(), LogOptions.initiateOptions());
        var second = LoggingFactory.get("com.acme.db", new Recorder(), LogOptions.initiateOptions());

        assertSame(first, second);
    }

    @Test
    public void aLevelSetOnOneNameLeavesAnotherAlone() {
        var quietRecorder = new Recorder();
        var loudRecorder = new Recorder();
        var quiet = LoggingFactory.get("com.acme.db", quietRecorder, LogOptions.initiateOptions());
        var loud = LoggingFactory.get("com.acme.web", loudRecorder, LogOptions.initiateOptions());

        quiet.setCurrentLevel(LogLevel.ERROR);
        quiet.info("suppressed");
        loud.info("written");

        assertEquals(List.of(), quietRecorder.messages);
        assertEquals(List.of("written"), loudRecorder.messages);
    }

    @Test
    public void anExplicitlyAddedLoggerIsWhatTheNameResolvesTo() {
        var standIn = new Logging("com.acme.db");

        LoggingFactory.add("com.acme.db", standIn);

        assertSame(standIn, LoggingFactory.get("com.acme.db"));
    }

    @Test
    public void aLoggerCannotBeRegisteredUnderANameThatIsNotItsOwn() {
        var db = new Logging("com.acme.db");

        var refused = assertThrows(IllegalArgumentException.class,
                () -> LoggingFactory.add("com.acme.web", db));

        assertTrue(refused.getMessage(), refused.getMessage().contains("com.acme.db"));
        assertFalse(LoggingFactory.has("com.acme.web"));
    }

    @Test
    public void clearingForgetsEveryName() {
        LoggingFactory.get("com.acme.db");
        assertTrue(LoggingFactory.has("com.acme.db"));

        LoggingFactory.clear();

        assertFalse(LoggingFactory.has("com.acme.db"));
    }

    @Test
    public void aNameIsRequired() {
        assertThrows(NullPointerException.class, () -> LoggingFactory.get((String) null));
        assertThrows(NullPointerException.class, () -> LoggingFactory.get((Class<?>) null));
    }

    @Test
    public void racingThreadsStillAgreeOnOneInstance() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Loggable>> asking = IntStream.range(0, threads)
                    .<Callable<Loggable>>mapToObj(i -> () -> LoggingFactory.get("com.acme.contended"))
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
        assertEquals("com.acme.db", LoggingFactory.get("com.acme.db").getName());
        assertEquals("org.example.LoggingFactoryTest",
                LoggingFactory.get(LoggingFactoryTest.class).getName());
    }

    @Test
    public void everyEventCarriesTheNameOfTheLoggerThatBuiltIt() {
        var recorder = new EventRecorder();
        var log = LoggingFactory.get("com.acme.db", recorder, LogOptions.initiateOptions());

        log.info("through a convenience method");
        log.error("with a throwable", new IllegalStateException("boom"));

        assertEquals(List.of("com.acme.db", "com.acme.db"),
                recorder.events.stream().map(LogEvent::getLoggerName).toList());
    }

    @Test
    public void twoLoggersStampTheirOwnNamesNotEachOthers() {
        var dbRecorder = new EventRecorder();
        var webRecorder = new EventRecorder();
        LoggingFactory.get("com.acme.db", dbRecorder, LogOptions.initiateOptions()).info("query ran");
        LoggingFactory.get("com.acme.web", webRecorder, LogOptions.initiateOptions()).info("request served");

        assertEquals("com.acme.db", dbRecorder.events.get(0).getLoggerName());
        assertEquals("com.acme.web", webRecorder.events.get(0).getLoggerName());
    }

    @Test
    public void aLoggerTakenBeforeAnyConfigurationStillWrites() {
        var log = LoggingFactory.get("com.acme.unconfigured");

        log.info("no formatter was ever set");

        assertEquals("no formatter was ever set" + System.lineSeparator(), written.toString());
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
