package org.example;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Level threshold filtering: which messages reach the destination.
 *
 * <p>One rule: a message is written when the severity of the method that was
 * called is at or above the logger's current level, and dropped otherwise. The
 * threshold starts at TRACE, so nothing is filtered until a caller raises it,
 * and it governs every level rather than a privileged subset of them.
 *
 * <p>Dropping happens before formatting, so raising the threshold buys back
 * the cost of building the line as well as the cost of writing it.
 */
public class LevelFilteringTest {

    /** Every level a caller can log at, in ascending severity. */
    private static final List<LogLevel> LEVELS = List.of(
            LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO,
            LogLevel.WARN, LogLevel.ERROR, LogLevel.FATAL);

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /** Records what got as far as formatting, so suppression is visible. */
    private static final class Recorder implements LogFormatter {
        private final List<LogLevel> levels = new ArrayList<>();

        @Override
        public String format(LogEvent event) {
            levels.add(event.getLevel());
            return event.getLevel().name() + " " + event.getMessage();
        }
    }

    private record Run(List<LogLevel> formatted, List<String> written) {
        List<String> levelsWritten() {
            return written.stream().map(line -> line.split(" ", 2)[0]).toList();
        }

        List<String> levelsFormatted() {
            return formatted.stream().map(LogLevel::name).toList();
        }
    }

    /** Runs {@code action} against a logger writing to a temp file. */
    private Run run(Consumer<Loggable> action) throws Exception {
        File sink = tempFolder.newFile();
        var recorder = new Recorder();
        var log = Logging.getLogger(recorder, LogOptions.initiateOptions()
                .setDestination(LogDestination.file(sink.getAbsolutePath())));

        action.accept(log);

        return new Run(recorder.levels,
            Files.readString(sink.toPath(), StandardCharsets.UTF_8).lines().toList());
    }

    /** Logs one message at every level, in ascending severity. */
    private static void logEveryLevel(Loggable log) {
        log.trace("trace message");
        log.debug("debug message");
        log.info("info message");
        log.warn("warn message");
        log.error("error message");
        log.fatal("fatal message");
    }

    /** Calls the method matching {@code level}. */
    private static void logAt(Loggable log, LogLevel level, String message) {
        switch (level) {
            case TRACE -> log.trace(message);
            case DEBUG -> log.debug(message);
            case INFO -> log.info(message);
            case WARN -> log.warn(message);
            case ERROR -> log.error(message);
            default -> log.fatal(message);
        }
    }

    /** The levels a correct threshold lets through: those at or above it. */
    private static List<String> atOrAbove(LogLevel threshold) {
        return LEVELS.stream()
                .filter(level -> level.severity() >= threshold.severity())
                .map(LogLevel::name)
                .toList();
    }

    // ---------------------------------------------------------------------
    // The default threshold filters nothing.
    // ---------------------------------------------------------------------

    @Test
    public void traceIsWrittenAtTheDefaultLevel() throws Exception {
        Run run = run(log -> log.trace("Tracing"));

        assertEquals("the threshold starts at TRACE, so nothing is filtered",
            List.of("TRACE"), run.levelsWritten());
    }

    @Test
    public void debugIsWrittenAtTheDefaultLevel() throws Exception {
        Run run = run(log -> log.debug("chatter"));

        assertEquals(List.of("DEBUG"), run.levelsWritten());
    }

    @Test
    public void infoIsWrittenAtTheDefaultLevel() throws Exception {
        Run run = run(log -> log.info("visible"));

        assertEquals(List.of("INFO"), run.levelsWritten());
    }

    @Test
    public void everyLevelIsWrittenAtTheDefaultThreshold() throws Exception {
        Run run = run(LevelFilteringTest::logEveryLevel);

        assertEquals(atOrAbove(LogLevel.TRACE), run.levelsWritten());
    }

    // ---------------------------------------------------------------------
    // The threshold governs every level, not just the verbose ones.
    //
    // This is the whole of the rule, stated once: for each threshold in turn,
    // exactly the levels at or above it are written. The cases that follow
    // spell out the corners it covers, so a failure names itself.
    // ---------------------------------------------------------------------

    @Test
    public void aThresholdWritesExactlyTheLevelsAtOrAboveIt() throws Exception {
        for (LogLevel threshold : LEVELS) {
            Run run = run(log -> {
                log.setCurrentLevel(threshold);
                logEveryLevel(log);
            });

            assertEquals("threshold " + threshold,
                atOrAbove(threshold), run.levelsWritten());
        }
    }

    @Test
    public void raisingTheThresholdSilencesInfo() throws Exception {
        Run run = run(log -> log.setCurrentLevel(LogLevel.ERROR).info("below the threshold"));

        assertEquals("INFO is less severe than ERROR, so it is dropped",
            List.of(), run.levelsWritten());
    }

    @Test
    public void raisingTheThresholdSilencesWarn() throws Exception {
        Run run = run(log -> log.setCurrentLevel(LogLevel.ERROR).warn("below the threshold"));

        assertEquals(List.of(), run.levelsWritten());
    }

    @Test
    public void raisingTheThresholdAboveDebugSilencesIt() throws Exception {
        Run run = run(log -> log.setCurrentLevel(LogLevel.INFO).debug("hidden"));

        assertEquals(List.of(), run.levelsWritten());
    }

    @Test
    public void theHighestThresholdLeavesOnlyFatal() throws Exception {
        Run run = run(log -> {
            log.setCurrentLevel(LogLevel.FATAL);
            logEveryLevel(log);
        });

        assertEquals("FATAL is the only level at or above a FATAL threshold",
            List.of("FATAL"), run.levelsWritten());
    }

    // ---------------------------------------------------------------------
    // The boundary itself.
    // ---------------------------------------------------------------------

    @Test
    public void aLevelExactlyAtTheThresholdIsWritten() throws Exception {
        Run run = run(log -> log.setCurrentLevel(LogLevel.WARN).warn("at the boundary"));

        assertEquals("the comparison is at-or-above, not strictly above",
            List.of("WARN"), run.levelsWritten());
    }

    @Test
    public void theLevelOneStepBelowTheThresholdIsDropped() throws Exception {
        Run run = run(log -> log.setCurrentLevel(LogLevel.WARN).info("just under"));

        assertEquals(List.of(), run.levelsWritten());
    }

    @Test
    public void theLevelOneStepAboveTheThresholdIsWritten() throws Exception {
        Run run = run(log -> log.setCurrentLevel(LogLevel.WARN).error("just over"));

        assertEquals(List.of("ERROR"), run.levelsWritten());
    }

    // ---------------------------------------------------------------------
    // Dropping happens before formatting.
    // ---------------------------------------------------------------------

    @Test
    public void aSuppressedMessageIsNeverEvenFormatted() throws Exception {
        Run run = run(log -> log.setCurrentLevel(LogLevel.INFO).debug("expensive"));

        assertEquals("the formatter must not be called for a filtered level",
            List.of(), run.levelsFormatted());
    }

    @Test
    public void aSuppressedMessageOfAnyLevelIsNeverEvenFormatted() throws Exception {
        Run run = run(log -> {
            log.setCurrentLevel(LogLevel.FATAL);
            log.trace("expensive");
            log.debug("expensive");
            log.info("expensive");
            log.warn("expensive");
            log.error("expensive");
        });

        assertEquals("suppression must not depend on which level was dropped",
            List.of(), run.levelsFormatted());
    }

    @Test
    public void whatIsFormattedIsExactlyWhatIsWritten() throws Exception {
        Run run = run(log -> {
            log.setCurrentLevel(LogLevel.WARN);
            logEveryLevel(log);
        });

        assertEquals("nothing is formatted and then dropped, or written unformatted",
            run.levelsFormatted(), run.levelsWritten());
    }

    // ---------------------------------------------------------------------
    // Moving the threshold.
    // ---------------------------------------------------------------------

    @Test
    public void loweringTheThresholdLetsVerboseLevelsThrough() throws Exception {
        Run run = run(log -> log.setCurrentLevel(LogLevel.TRACE).trace("now visible"));

        assertEquals(List.of("TRACE"), run.levelsWritten());
    }

    @Test
    public void loweringTheThresholdToTraceLetsDebugThroughToo() throws Exception {
        Run run = run(log -> log.setCurrentLevel(LogLevel.TRACE).debug("now visible"));

        assertEquals(List.of("DEBUG"), run.levelsWritten());
    }

    @Test
    public void loweringTheThresholdAgainRestoresWhatItHadSilenced() throws Exception {
        Run run = run(log -> {
            log.setCurrentLevel(LogLevel.ERROR);
            log.info("silenced");
            log.setCurrentLevel(LogLevel.INFO);
            log.info("audible again");
        });

        assertEquals("raising the threshold must not be a one-way door",
            List.of("INFO"), run.levelsWritten());
    }

    @Test
    public void onlyTheThresholdInForceWhenTheCallIsMadeApplies() throws Exception {
        Run run = run(log -> {
            log.info("written under the default threshold");
            log.setCurrentLevel(LogLevel.FATAL);
            log.info("dropped under the raised one");
        });

        assertEquals(List.of("INFO"), run.levelsWritten());
    }

    // ---------------------------------------------------------------------
    // The threshold must stay put while logging.
    // ---------------------------------------------------------------------

    @Test
    public void everyLevelAtOrAboveTheThresholdIsWrittenRegardlessOfOrder() throws Exception {
        Run run = run(log -> {
            log.setCurrentLevel(LogLevel.INFO);
            log.error("first, high severity");
            log.info("second, lower severity but still at or above INFO");
        });

        assertEquals(List.of("ERROR", "INFO"), run.levelsWritten());
    }

    @Test
    public void loggingDoesNotRaiseTheThresholdForLaterMessages() throws Exception {
        Run run = run(log -> {
            log.fatal("a one-off fatal");
            log.info("routine work continues");
            log.warn("and so does this");
        });

        assertEquals("a single high-severity line must not silence what follows",
            List.of("FATAL", "INFO", "WARN"), run.levelsWritten());
    }

    @Test
    public void aDroppedMessageDoesNotDisturbTheThreshold() throws Exception {
        Run run = run(log -> {
            log.setCurrentLevel(LogLevel.INFO);
            log.debug("dropped");
            log.info("still audible");
            log.debug("dropped");
            log.info("still audible");
        });

        assertEquals(List.of("INFO", "INFO"), run.levelsWritten());
    }

    @Test
    public void theThresholdSurvivesRepeatedLoggingAtTheSameLevel() throws Exception {
        Run run = run(log -> {
            log.info("one");
            log.info("two");
            log.info("three");
        });

        assertEquals(List.of("INFO", "INFO", "INFO"), run.levelsWritten());
    }

    @Test
    public void theThresholdIsPerLoggerRatherThanShared() throws Exception {
        File sink = tempFolder.newFile();
        var quietRecorder = new Recorder();
        var loudRecorder = new Recorder();
        var options = LogOptions.initiateOptions()
                .setDestination(LogDestination.file(sink.getAbsolutePath()));

        var quiet = Logging.getLogger(quietRecorder, options);
        var loud = Logging.getLogger(loudRecorder, options);

        quiet.setCurrentLevel(LogLevel.ERROR);
        quiet.info("dropped");
        loud.info("written");

        assertEquals("raising one logger's threshold must not raise another's",
            List.of(), quietRecorder.levels);
        assertEquals(List.of(LogLevel.INFO), loudRecorder.levels);
    }

    // ---------------------------------------------------------------------
    // The ordering the threshold relies on.
    // ---------------------------------------------------------------------

    @Test
    public void severityRunsFromMostVerboseToMostSevere() {
        assertTrue(LogLevel.TRACE.severity() < LogLevel.DEBUG.severity());
        assertTrue(LogLevel.DEBUG.severity() < LogLevel.INFO.severity());
        assertTrue(LogLevel.INFO.severity() < LogLevel.WARN.severity());
        assertTrue(LogLevel.WARN.severity() < LogLevel.ERROR.severity());
        assertTrue(LogLevel.ERROR.severity() < LogLevel.FATAL.severity());
    }

    @Test
    public void everyLevelCarriesADistinctSeverity() {
        assertEquals("two levels sharing a severity would be indistinguishable to the threshold",
            LEVELS.size(), LEVELS.stream().map(LogLevel::severity).distinct().count());
    }

    @Test
    public void aLoggerNeverFiltersOutItsOwnThresholdLevel() throws Exception {
        for (LogLevel threshold : LEVELS) {
            Run run = run(log -> {
                log.setCurrentLevel(threshold);
                logAt(log, threshold, "at the threshold");
            });

            assertEquals("a logger set to " + threshold + " must still write " + threshold,
                List.of(threshold.name()), run.levelsWritten());
        }
    }

    // ---------------------------------------------------------------------
    // What a suppressed call costs.
    //
    // The threshold is consulted before an event is built and before the
    // monitor is taken, and the flush now lives inside the synchronized
    // write. So a filtered message should not reach the destination in any
    // form -- not even as the flush that used to run from a finally block on
    // every call, suppressed or not.
    // ---------------------------------------------------------------------

    /** Counts the flushes the logger asks for, which a suppressed call must not. */
    private static final class CountingStream extends PrintStream {
        private final AtomicInteger flushes = new AtomicInteger();

        CountingStream() {
            super(new ByteArrayOutputStream(), false, StandardCharsets.UTF_8);
        }

        @Override
        public void flush() {
            flushes.incrementAndGet();
            super.flush();
        }
    }

    private final PrintStream realStdout = System.out;

    @After
    public void restoreStandardOut() {
        System.setOut(realStdout);
    }

    /** A logger on a counting stdout. The stream must be in place first. */
    private static Loggable loggerOn(CountingStream stream, Recorder recorder) {
        System.setOut(stream);
        return Logging.getLogger(recorder, LogOptions.initiateOptions());
    }

    @Test
    public void aSuppressedMessageNeverTouchesTheDestination() {
        var counting = new CountingStream();
        var log = loggerOn(counting, new Recorder());

        log.setCurrentLevel(LogLevel.ERROR);
        log.trace("suppressed");
        log.debug("suppressed");
        log.info("suppressed");
        log.warn("suppressed");

        assertEquals("a filtered message must not even flush the stream",
            0, counting.flushes.get());
    }

    @Test
    public void aKeptMessageDoesTouchTheDestination() {
        var counting = new CountingStream();
        var log = loggerOn(counting, new Recorder());

        log.setCurrentLevel(LogLevel.ERROR);
        log.info("suppressed");
        log.error("kept");

        assertEquals("exactly the kept message should reach the stream",
            1, counting.flushes.get());
    }

    @Test
    public void aThresholdRaisedOnAnotherThreadAppliesHere() throws Exception {
        var recorder = new Recorder();
        var log = loggerOn(new CountingStream(), recorder);

        // Thread.join supplies the ordering here, so this pins the behaviour
        // rather than the memory model: a threshold set on one thread governs
        // what another thread may write.
        var configurer = new Thread(() -> log.setCurrentLevel(LogLevel.ERROR), "configurer");
        configurer.start();
        configurer.join(5_000);
        assertFalse("configurer did not finish", configurer.isAlive());

        log.info("below the threshold set elsewhere");

        assertEquals(List.of(), recorder.levels);
    }
}
