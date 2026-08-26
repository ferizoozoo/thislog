package org.example;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Level threshold filtering: which messages reach the destination.
 *
 * <p>One rule: an event is written when its severity is at or above the
 * logger's current level, and dropped otherwise. The threshold starts at
 * TRACE, so nothing is filtered until a caller raises it, and it governs
 * every level rather than a privileged subset of them.
 *
 * <p>The severity compared is the one on the {@link LogEvent}, not one implied
 * by the method that was called -- {@code info(...)} and {@code error(...)}
 * both hand the event straight to the same {@code log(LogEvent)}. So an event
 * carries its own fate at the threshold, which
 * {@link #filteringFollowsTheEventsLevelNotTheMethodCalled} pins down.
 *
 * <p>Dropping happens before formatting, so raising the threshold buys back
 * the cost of building the line as well as the cost of writing it.
 */
public class LevelFilteringTest {

    /** Every level a caller can log at, in ascending severity. */
    private static final List<LogLevel> LEVELS = List.of(
            LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO,
            LogLevel.WARN, LogLevel.ERROR, LogLevel.FATAL);

    private static final long AT = 1_700_000_000_000L;

    private static LogEvent event(LogLevel level, String message) {
        return new LogEvent(message, AT, level, Thread.currentThread().getName());
    }

    /** An event at {@code level} whose message says so, for readable failures. */
    private static LogEvent at(LogLevel level) {
        return event(level, level.name().toLowerCase() + " message");
    }

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /** Records what got as far as formatting, so suppression is visible. */
    private static final class Recorder implements Formatable {
        private final List<LogLevel> levels = new ArrayList<>();

        @Override
        public String getFormattedString(LogEvent event, Object... args) {
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

    /** Logs one event at every level, in ascending severity. */
    private static void logEveryLevel(Loggable log) {
        log.trace(at(LogLevel.TRACE));
        log.debug(at(LogLevel.DEBUG));
        log.info(at(LogLevel.INFO));
        log.warn(at(LogLevel.WARN));
        log.error(at(LogLevel.ERROR));
        log.fatal(at(LogLevel.FATAL));
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
        Run run = run(log -> log.trace(at(LogLevel.TRACE)));

        assertEquals("the threshold starts at TRACE, so nothing is filtered",
            List.of("TRACE"), run.levelsWritten());
    }

    @Test
    public void debugIsWrittenAtTheDefaultLevel() throws Exception {
        Run run = run(log -> log.debug(at(LogLevel.DEBUG)));

        assertEquals(List.of("DEBUG"), run.levelsWritten());
    }

    @Test
    public void infoIsWrittenAtTheDefaultLevel() throws Exception {
        Run run = run(log -> log.info(at(LogLevel.INFO)));

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
        Run run = run(log -> log.setCurrentLevel(LogLevel.ERROR).info(at(LogLevel.INFO)));

        assertEquals("INFO is less severe than ERROR, so it is dropped",
            List.of(), run.levelsWritten());
    }

    @Test
    public void raisingTheThresholdSilencesWarn() throws Exception {
        Run run = run(log -> log.setCurrentLevel(LogLevel.ERROR).warn(at(LogLevel.WARN)));

        assertEquals(List.of(), run.levelsWritten());
    }

    @Test
    public void raisingTheThresholdAboveDebugSilencesIt() throws Exception {
        Run run = run(log -> log.setCurrentLevel(LogLevel.INFO).debug(at(LogLevel.DEBUG)));

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
    // It is the event's level that is compared, not the method's name.
    // ---------------------------------------------------------------------

    @Test
    public void filteringFollowsTheEventsLevelNotTheMethodCalled() throws Exception {
        Run run = run(log -> {
            log.setCurrentLevel(LogLevel.ERROR);
            log.error(event(LogLevel.INFO, "an INFO event handed to error()"));
            log.info(event(LogLevel.FATAL, "a FATAL event handed to info()"));
        });

        assertEquals("error() cannot promote an INFO event, and info() cannot demote a FATAL one",
            List.of("FATAL"), run.levelsWritten());
    }

    @Test
    public void aSevereEventSurvivesAVerboseMethod() throws Exception {
        Run run = run(log -> {
            log.setCurrentLevel(LogLevel.FATAL);
            log.trace(event(LogLevel.FATAL, "a FATAL event handed to trace()"));
        });

        assertEquals(List.of("FATAL"), run.levelsWritten());
    }

    // ---------------------------------------------------------------------
    // The boundary itself.
    // ---------------------------------------------------------------------

    @Test
    public void aLevelExactlyAtTheThresholdIsWritten() throws Exception {
        Run run = run(log -> log.setCurrentLevel(LogLevel.WARN).warn(at(LogLevel.WARN)));

        assertEquals("the comparison is at-or-above, not strictly above",
            List.of("WARN"), run.levelsWritten());
    }

    @Test
    public void theLevelOneStepBelowTheThresholdIsDropped() throws Exception {
        Run run = run(log -> log.setCurrentLevel(LogLevel.WARN).info(at(LogLevel.INFO)));

        assertEquals(List.of(), run.levelsWritten());
    }

    @Test
    public void theLevelOneStepAboveTheThresholdIsWritten() throws Exception {
        Run run = run(log -> log.setCurrentLevel(LogLevel.WARN).error(at(LogLevel.ERROR)));

        assertEquals(List.of("ERROR"), run.levelsWritten());
    }

    // ---------------------------------------------------------------------
    // Dropping happens before formatting.
    // ---------------------------------------------------------------------

    @Test
    public void aSuppressedMessageIsNeverEvenFormatted() throws Exception {
        Run run = run(log -> log.setCurrentLevel(LogLevel.INFO).debug(at(LogLevel.DEBUG)));

        assertEquals("the formatter must not be called for a filtered level",
            List.of(), run.levelsFormatted());
    }

    @Test
    public void aSuppressedMessageOfAnyLevelIsNeverEvenFormatted() throws Exception {
        Run run = run(log -> {
            log.setCurrentLevel(LogLevel.FATAL);
            log.trace(at(LogLevel.TRACE));
            log.debug(at(LogLevel.DEBUG));
            log.info(at(LogLevel.INFO));
            log.warn(at(LogLevel.WARN));
            log.error(at(LogLevel.ERROR));
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
        Run run = run(log -> log.setCurrentLevel(LogLevel.TRACE).trace(at(LogLevel.TRACE)));

        assertEquals(List.of("TRACE"), run.levelsWritten());
    }

    @Test
    public void loweringTheThresholdToTraceLetsDebugThroughToo() throws Exception {
        Run run = run(log -> log.setCurrentLevel(LogLevel.TRACE).debug(at(LogLevel.DEBUG)));

        assertEquals(List.of("DEBUG"), run.levelsWritten());
    }

    @Test
    public void loweringTheThresholdAgainRestoresWhatItHadSilenced() throws Exception {
        Run run = run(log -> {
            log.setCurrentLevel(LogLevel.ERROR);
            log.info(at(LogLevel.INFO));
            log.setCurrentLevel(LogLevel.INFO);
            log.info(at(LogLevel.INFO));
        });

        assertEquals("raising the threshold must not be a one-way door",
            List.of("INFO"), run.levelsWritten());
    }

    @Test
    public void onlyTheThresholdInForceWhenTheCallIsMadeApplies() throws Exception {
        Run run = run(log -> {
            log.info(at(LogLevel.INFO));
            log.setCurrentLevel(LogLevel.FATAL);
            log.info(at(LogLevel.INFO));
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
            log.error(at(LogLevel.ERROR));
            log.info(at(LogLevel.INFO));
        });

        assertEquals(List.of("ERROR", "INFO"), run.levelsWritten());
    }

    @Test
    public void loggingDoesNotRaiseTheThresholdForLaterMessages() throws Exception {
        Run run = run(log -> {
            log.fatal(at(LogLevel.FATAL));
            log.info(at(LogLevel.INFO));
            log.warn(at(LogLevel.WARN));
        });

        assertEquals("a single high-severity line must not silence what follows",
            List.of("FATAL", "INFO", "WARN"), run.levelsWritten());
    }

    @Test
    public void aDroppedMessageDoesNotDisturbTheThreshold() throws Exception {
        Run run = run(log -> {
            log.setCurrentLevel(LogLevel.INFO);
            log.debug(at(LogLevel.DEBUG));
            log.info(at(LogLevel.INFO));
            log.debug(at(LogLevel.DEBUG));
            log.info(at(LogLevel.INFO));
        });

        assertEquals(List.of("INFO", "INFO"), run.levelsWritten());
    }

    @Test
    public void theThresholdSurvivesRepeatedLoggingAtTheSameLevel() throws Exception {
        Run run = run(log -> {
            log.info(at(LogLevel.INFO));
            log.info(at(LogLevel.INFO));
            log.info(at(LogLevel.INFO));
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
        quiet.info(at(LogLevel.INFO));
        loud.info(at(LogLevel.INFO));

        assertEquals("raising one logger's threshold must not raise another's",
            List.of(), quietRecorder.levels);
        assertEquals(List.of(LogLevel.INFO), loudRecorder.levels);
    }

    @Test
    public void oneEventCanBeDroppedByOneLoggerAndKeptByAnother() throws Exception {
        File sink = tempFolder.newFile();
        var quietRecorder = new Recorder();
        var loudRecorder = new Recorder();
        var options = LogOptions.initiateOptions()
                .setDestination(LogDestination.file(sink.getAbsolutePath()));

        var quiet = Logging.getLogger(quietRecorder, options);
        var loud = Logging.getLogger(loudRecorder, options);
        var shared = at(LogLevel.INFO);

        quiet.setCurrentLevel(LogLevel.ERROR);
        quiet.info(shared);
        loud.info(shared);

        assertEquals("the threshold belongs to the logger, not to the event",
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
                var atTheThreshold = at(threshold);
                switch (threshold) {
                    case TRACE -> log.trace(atTheThreshold);
                    case DEBUG -> log.debug(atTheThreshold);
                    case INFO -> log.info(atTheThreshold);
                    case WARN -> log.warn(atTheThreshold);
                    case ERROR -> log.error(atTheThreshold);
                    default -> log.fatal(atTheThreshold);
                }
            });

            assertEquals("a logger set to " + threshold + " must still write " + threshold,
                List.of(threshold.name()), run.levelsWritten());
        }
    }
}
