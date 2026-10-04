package io.github.ferizoozoo.thislog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class LoggingAppendersTest {

    private static final String NL = System.lineSeparator();

    private ByteArrayOutputStream stdout;
    private ByteArrayOutputStream stderr;
    private PrintStream originalOut;
    private PrintStream originalErr;

    @Before
    public void redirectStandardStreams() {
        LoggingFactory.clear();
        originalOut = System.out;
        originalErr = System.err;
        stdout = new ByteArrayOutputStream();
        stderr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

    @After
    public void restoreStandardStreams() {
        LoggingFactory.clear();
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private String stdoutText() {
        return stdout.toString(StandardCharsets.UTF_8);
    }

    private String stderrText() {
        return stderr.toString(StandardCharsets.UTF_8);
    }

    private static LogOptions plainlyTo(LogDestination destination) {
        return LogOptions.createFromEnvironment()
                .withDestination(destination)
                .withFormatter(PatternFormatter.create(PatternFormatter.DEFAULT_PATTERN));
    }

    private static Logging onStdout() {
        return Logging.create("com.acme.Boot", plainlyTo(LogDestination.STDOUT));
    }

    private static String textOf(ByteArrayOutputStream sink) {
        return sink.toString(StandardCharsets.UTF_8);
    }

    private static class Recorder implements Appender {
        final List<String> messages = new ArrayList<>();
        int flushes;
        int closes;
        LogLevel level = LogLevel.TRACE;

        @Override
        public void append(LogEvent event) {
            messages.add(event.getMessage());
        }

        @Override
        public void flush() {
            flushes++;
        }

        @Override
        public void close() {
            closes++;
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
    }

    private static final class Broken implements Appender {
        @Override
        public void append(LogEvent event) {
            throw new IllegalStateException("append is broken");
        }

        @Override
        public void flush() {
            throw new IllegalStateException("flush is broken");
        }

        @Override
        public void close() {
            throw new IllegalStateException("close is broken");
        }

        @Override
        public boolean checkFailure() {
            throw new IllegalStateException("checkFailure is broken");
        }

        @Override
        public LogLevel getLogLevel() {
            return LogLevel.TRACE;
        }

        @Override
        public void setLogLevel(LogLevel level) {
        }
    }

    @Test
    public void anAddedAppenderGetsEveryLineAlongsideTheOneFromTheOptions() {
        var log = onStdout();
        var added = new Recorder();

        log.addAppender(added);
        log.info("to both");

        assertEquals("to both" + NL, stdoutText());
        assertEquals(List.of("to both"), added.messages);
    }

    @Test
    public void eachAppenderRendersWithItsOwnFormatter() {
        var log = onStdout();
        var sink = new ByteArrayOutputStream();

        log.addAppender(StreamAppender.create(sink, PatternFormatter.create("%level %m")));
        log.warn("rendered twice");

        assertEquals("rendered twice" + NL, stdoutText());
        assertEquals("WARN rendered twice" + NL, textOf(sink));
    }

    @Test
    public void aLineBelowTheLoggersLevelReachesNoAppender() {
        var log = onStdout();
        var added = new Recorder();

        log.addAppender(added);
        log.debug("dropped before any appender sees it");

        assertEquals(List.of(), added.messages);
    }

    @Test
    public void aLineBelowAnAppendersLevelSkipsOnlyThatAppender() {
        var log = onStdout();
        var errorsOnly = new Recorder();
        errorsOnly.setLogLevel(LogLevel.ERROR);

        log.addAppender(errorsOnly);
        log.info("below the appender");
        log.error("at the appender");

        assertEquals("below the appender" + NL + "at the appender" + NL, stdoutText());
        assertEquals(List.of("at the appender"), errorsOnly.messages);
    }

    @Test
    public void anAppendersLevelCannotLetThroughWhatTheLoggerDrops() {
        var log = onStdout();
        var everything = new Recorder();
        everything.setLogLevel(LogLevel.TRACE);

        log.addAppender(everything);
        log.debug("dropped by the logger");

        assertEquals(List.of(), everything.messages);
    }

    @Test
    public void anAppendersLevelCanBeMovedWhileTheLoggerIsInUse() {
        var log = onStdout();
        var added = new Recorder();
        log.addAppender(added);

        log.info("before");
        added.setLogLevel(LogLevel.WARN);
        log.info("after");

        assertEquals(List.of("before"), added.messages);
    }

    @Test
    public void anAppenderWithoutALevelTakesEverythingTheLoggerLetsThrough() {
        var log = onStdout();
        var added = new Recorder();
        added.setLogLevel(null);

        log.addAppender(added);
        log.info("let through");

        assertEquals(List.of("let through"), added.messages);
    }

    @Test
    public void aStreamAppenderStartsAtTrace() {
        var appender = StreamAppender.create(new ByteArrayOutputStream(), event -> "");

        assertEquals(LogLevel.TRACE, appender.getLogLevel());
    }

    @Test
    public void aStreamAppenderRejectsALevelThatIsNotThere() {
        var appender = StreamAppender.create(new ByteArrayOutputStream(), event -> "");

        assertThrows(NullPointerException.class, () -> appender.setLogLevel(null));
    }

    @Test
    public void aStreamAppenderAboveTheLineWritesNothing() {
        var log = onStdout();
        var sink = new ByteArrayOutputStream();
        var appender = StreamAppender.create(sink, PatternFormatter.create("%m"));
        appender.setLogLevel(LogLevel.WARN);

        log.addAppender(appender);
        log.info("quiet");
        log.warn("loud");

        assertEquals("loud" + NL, textOf(sink));
    }

    @Test
    public void addingTheSameAppenderTwiceStillWritesEachLineOnce() {
        var log = onStdout();
        var added = new Recorder();

        log.addAppender(added);
        log.addAppender(added);
        log.info("once");

        assertEquals(List.of("once"), added.messages);
    }

    @Test
    public void changingTheOptionsLeavesAnAddedAppenderInPlace() {
        var log = onStdout();
        var added = new Recorder();
        log.addAppender(added);

        log.changeOptions(plainlyTo(LogDestination.STDERR));
        log.info("after the move");

        assertEquals("the options only replace the appender they describe",
                List.of("after the move"), added.messages);
        assertEquals("an appender that is kept is not closed", 0, added.closes);
        assertEquals("after the move" + NL, stderrText());
        assertEquals("", stdoutText());
    }

    @Test
    public void anAppenderInTheOptionsTakesThePlaceOfTheDestination() {
        var given = new Recorder();
        var log = Logging.create("com.acme.Boot", plainlyTo(LogDestination.STDOUT).withAppender(given));

        log.info("to the given appender");

        assertEquals(List.of("to the given appender"), given.messages);
        assertEquals("the destination beside it is not opened", "", stdoutText());
    }

    @Test
    public void optionsWithoutAnAppenderStillOpenTheirDestination() {
        var log = Logging.create("com.acme.Boot", LogOptions.createFromEnvironment()
                .withFormatter(PatternFormatter.create(PatternFormatter.DEFAULT_PATTERN)));

        log.info("to stdout");

        assertEquals("to stdout" + NL, stdoutText());
    }

    @Test
    public void carryingTheSameAppenderForwardDoesNotCloseIt() {
        var given = new Recorder();
        var options = plainlyTo(LogDestination.STDOUT).withAppender(given);
        var log = Logging.create("com.acme.Boot", options);

        log.changeOptions(options.withLevel(LogLevel.DEBUG));
        log.debug("still writing");

        assertEquals(0, given.closes);
        assertEquals(List.of("still writing"), given.messages);
    }

    @Test
    public void movingOffAGivenAppenderClosesIt() {
        var given = new Recorder();
        var log = Logging.create("com.acme.Boot", plainlyTo(LogDestination.STDOUT).withAppender(given));

        log.changeOptions(plainlyTo(LogDestination.STDOUT));
        log.info("back on stdout");

        assertEquals("the logger owns what the options handed it", 1, given.closes);
        assertEquals(List.of(), given.messages);
        assertEquals("back on stdout" + NL, stdoutText());
    }

    @Test
    public void flushingTheLoggerFlushesEveryAppender() {
        var log = onStdout();
        var added = new Recorder();
        log.addAppender(added);

        log.flush();

        assertEquals(1, added.flushes);
    }

    @Test
    public void closingTheLoggerFlushesAndClosesWhatWasAdded() {
        var log = onStdout();
        var added = new Recorder();
        log.addAppender(added);

        log.close();
        log.info("discarded");

        assertEquals(1, added.flushes);
        assertEquals(1, added.closes);
        assertEquals("a closed logger writes to nothing", List.of(), added.messages);
    }

    @Test
    public void aClosedLoggerRefusesANewAppender() {
        var log = onStdout();
        log.close();

        assertThrows(IllegalStateException.class, () -> log.addAppender(new Recorder()));
    }

    @Test
    public void anAppenderThatIsNotThereIsRejected() {
        var log = onStdout();

        assertThrows(NullPointerException.class, () -> log.addAppender(null));
    }

    @Test
    public void aRemovedAppenderGetsNoMoreLines() {
        var log = onStdout();
        var added = new Recorder();
        log.addAppender(added);

        log.info("before");
        log.removeAppender(added);
        log.info("after");

        assertEquals(List.of("before"), added.messages);
        assertEquals("before" + NL + "after" + NL, stdoutText());
    }

    @Test
    public void aRemovedAppenderIsFlushedThenClosed() {
        var log = onStdout();
        var calls = new ArrayList<String>();
        var added = new Recorder() {
            @Override
            public void flush() {
                calls.add("flush");
            }

            @Override
            public void close() {
                calls.add("close");
            }
        };
        log.addAppender(added);

        log.removeAppender(added);

        assertEquals(List.of("flush", "close"), calls);
    }

    @Test
    public void aRemovedAppenderIsNotTouchedWhenTheLoggerCloses() {
        var log = onStdout();
        var added = new Recorder();
        log.addAppender(added);

        log.removeAppender(added);
        log.close();

        assertEquals(1, added.flushes);
        assertEquals(1, added.closes);
    }

    @Test
    public void removingAnAppenderTheLoggerDoesNotHaveChangesNothing() {
        var log = onStdout();
        var stranger = new Recorder();

        log.removeAppender(stranger);
        log.info("still on stdout");

        assertEquals(0, stranger.closes);
        assertEquals("still on stdout" + NL, stdoutText());
    }

    @Test
    public void theAppenderFromTheOptionsCannotBeRemoved() {
        var given = new Recorder();
        var log = Logging.create("com.acme.Boot", plainlyTo(LogDestination.STDOUT).withAppender(given));

        assertThrows(IllegalArgumentException.class, () -> log.removeAppender(given));
        log.info("still delivered");

        assertEquals(0, given.closes);
        assertEquals(List.of("still delivered"), given.messages);
    }

    @Test
    public void changingTheOptionsAfterARemovalLeavesTheOtherAddedAppendersInPlace() {
        var log = onStdout();
        var removed = new Recorder();
        var kept = new Recorder();
        log.addAppender(removed);
        log.addAppender(kept);

        log.removeAppender(removed);
        log.changeOptions(plainlyTo(LogDestination.STDERR));
        log.info("after the move");

        assertEquals(0, kept.closes);
        assertEquals(List.of("after the move"), kept.messages);
        assertEquals("after the move" + NL, stderrText());
    }

    @Test
    public void aClosedLoggerRefusesARemoval() {
        var log = onStdout();
        var added = new Recorder();
        log.addAppender(added);
        log.close();

        assertThrows(IllegalStateException.class, () -> log.removeAppender(added));
    }

    @Test
    public void removingAnAppenderThatIsNotThereIsRejected() {
        var log = onStdout();

        assertThrows(NullPointerException.class, () -> log.removeAppender(null));
    }

    @Test
    public void anAppenderThatThrowsCostsNeitherTheCallSiteNorTheOtherAppenders() {
        var log = onStdout();
        var after = new Recorder();
        log.addAppender(new Broken());
        log.addAppender(after);

        log.error("still delivered");
        log.flush();
        log.close();

        assertEquals("still delivered" + NL, stdoutText());
        assertEquals(List.of("still delivered"), after.messages);
        assertEquals("the appender behind the broken one should still be closed", 1, after.closes);
    }

    @Test
    public void anAppenderThatKeepsThrowingIsReportedOnce() {
        var log = onStdout();
        log.addAppender(new Broken());

        log.info("first");
        log.info("second");

        var notices = stderrText().lines().filter(line -> line.contains("an appender for")).count();
        assertEquals("got: " + stderrText(), 1, notices);
        assertTrue("the reason should be in the notice, got: " + stderrText(),
                stderrText().contains("append is broken"));
    }

    @Test
    public void aThrowingAppenderIsReportedAgainAfterAReconfiguration() {
        var log = onStdout();
        log.addAppender(new Broken());
        log.info("first");

        log.changeOptions(plainlyTo(LogDestination.STDOUT));
        log.info("second");

        assertEquals("one notice per configuration",
                2, stderrText().split("threw", -1).length - 1);
    }

    @Test
    public void anAddedDestinationThatStopsAcceptingWritesIsReported() {
        var log = onStdout();
        var refusing = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("disk full");
            }
        };
        log.addAppender(StreamAppender.create(refusing, PatternFormatter.create("%m")));

        log.error("severe enough to be checked");

        assertTrue("a failure on any appender should be announced, got: " + stderrText(),
                stderrText().contains("is failing"));
    }
}
