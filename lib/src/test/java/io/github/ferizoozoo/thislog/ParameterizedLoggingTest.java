package io.github.ferizoozoo.thislog;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * The {@code {}} forms of the six level methods, seen from the call site: what
 * reaches the appenders, when the arguments are looked at, and where a
 * trailing throwable ends up.
 */
public class ParameterizedLoggingTest {

    private PrintStream originalOut;
    private Recorder recorder;
    private Logging log;

    @Before
    public void quietLoggerWithARecorder() {
        LoggingFactory.clear();
        originalOut = System.out;
        System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        log = Logging.create("com.acme.Orders", LogOptions.createFromEnvironment()
                .withDestination(LogDestination.STDOUT)
                .withLevel(LogLevel.TRACE));
        recorder = new Recorder();
        log.addAppender(recorder);
    }

    @After
    public void restoreStdout() {
        LoggingFactory.clear();
        System.setOut(originalOut);
    }

    /** An appender that keeps every event it is handed. */
    private static final class Recorder implements Appender {
        final List<LogEvent> events = new ArrayList<>();
        private LogLevel level;

        @Override
        public void append(LogEvent event) {
            events.add(event);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        @Override
        public boolean checkFailure() {
            return false;
        }

        @Override
        public LogLevel getLogLevel() {
            return level;
        }

        @Override
        public void setLogLevel(LogLevel level) {
            this.level = level;
        }

        LogEvent only() {
            assertEquals("expected exactly one event", 1, events.size());
            return events.get(0);
        }
    }

    /** An argument that fails the test if anything renders it. */
    private static final class MustNotBeRendered {
        @Override
        public String toString() {
            throw new AssertionError("the argument was rendered for a line that was dropped");
        }
    }

    @Test
    public void everyLevelFillsItsPlaceholders() {
        log.trace("t {}", 1);
        log.debug("d {}", 2);
        log.info("i {}", 3);
        log.warn("w {}", 4);
        log.error("e {}", 5);
        log.fatal("f {}", 6);

        var messages = recorder.events.stream().map(LogEvent::getMessage).toList();
        assertEquals(List.of("t 1", "d 2", "i 3", "w 4", "e 5", "f 6"), messages);
    }

    @Test
    public void everyLevelLogsAtItsOwnLevel() {
        log.trace("{}", "x");
        log.debug("{}", "x");
        log.info("{}", "x");
        log.warn("{}", "x");
        log.error("{}", "x");
        log.fatal("{}", "x");

        var levels = recorder.events.stream().map(LogEvent::getLevel).toList();
        assertEquals(List.of(LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO,
                LogLevel.WARN, LogLevel.ERROR, LogLevel.FATAL), levels);
    }

    @Test
    public void severalArgumentsAreFilledInOrder() {
        log.info("user {} moved {} to {}", "ann", 3, "the basket");

        assertEquals("user ann moved 3 to the basket", recorder.only().getMessage());
    }

    @Test
    public void aDroppedLineNeverRendersItsArguments() {
        log.setCurrentLogLevel(LogLevel.WARN);

        log.info("dropped {}", new MustNotBeRendered());

        assertEquals(List.of(), recorder.events);
    }

    @Test
    public void anArgumentWhoseToStringThrowsIsReportedInTheLine() {
        var broken = new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("no string for you");
            }
        };

        log.info("value {}", broken);

        assertTrue("got: " + recorder.only().getMessage(),
                recorder.only().getMessage().contains("message supplier failed"));
    }

    @Test
    public void aTrailingThrowableIsTheCause() {
        var cause = new IllegalStateException("pricing failed");

        log.error("order {} failed", 4711, cause);

        assertEquals("order 4711 failed", recorder.only().getMessage());
        assertSame(cause, recorder.only().getThrown());
    }

    @Test
    public void aThrowableThatIsNotLastIsNoCause() {
        log.warn("{} then {}", new IllegalStateException("first"), "second");

        assertNull(recorder.only().getThrown());
    }

    @Test
    public void aLineWithoutAThrowableHasNoCause() {
        log.info("order {} accepted", 4711);

        assertNull(recorder.only().getThrown());
    }

    @Test
    public void aThrowableAsTheOnlyArgumentTakesTheThrowableForm() {
        // info(String, Throwable) is the closer match, so nothing is substituted.
        var cause = new IllegalStateException("boom");

        log.info("failed: {}", cause);

        assertEquals("failed: {}", recorder.only().getMessage());
        assertSame(cause, recorder.only().getThrown());
    }

    @Test
    public void aNullArgumentIsRenderedAsNull() {
        log.info("customer {}", (Object) null);

        assertEquals("customer null", recorder.only().getMessage());
        assertNull(recorder.only().getThrown());
    }
}
