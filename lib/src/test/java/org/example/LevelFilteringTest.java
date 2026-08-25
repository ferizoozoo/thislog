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
 * <p>Two rules: the threshold starts at TRACE, so nothing is filtered until a
 * caller raises it; and it governs DEBUG and TRACE only, so INFO, WARN, ERROR
 * and FATAL are always written however high it goes.
 */
public class LevelFilteringTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /** Records what got as far as formatting, so suppression is visible. */
    private static final class Recorder implements Formatable {
        private final List<LogLevel> levels = new ArrayList<>();

        @Override
        public String getFormattedString(LogLevel level, String message, Object... args) {
            levels.add(level);
            return level.name() + " " + message;
        }
    }

    private record Run(List<LogLevel> formatted, List<String> written) {
        List<String> levelsWritten() {
            return written.stream().map(line -> line.split(" ", 2)[0]).toList();
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
    public void aSuppressedMessageIsNeverEvenFormatted() throws Exception {
        Run run = run(log -> log.setCurrentLevel(LogLevel.INFO).debug("expensive"));

        assertEquals("the formatter must not be called for a filtered level",
            List.of(), run.formatted());
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
    public void aLevelExactlyAtTheThresholdIsWritten() throws Exception {
        Run run = run(log -> log.setCurrentLevel(LogLevel.WARN).warn("at the boundary"));

        assertEquals(List.of("WARN"), run.levelsWritten());
    }

    @Test
    public void loweringTheThresholdToTraceLetsDebugThroughToo() throws Exception {
        Run run = run(log -> log.setCurrentLevel(LogLevel.TRACE).debug("now visible"));

        assertEquals(List.of("DEBUG"), run.levelsWritten());
    }

    @Test
    public void raisingTheThresholdAboveDebugSilencesIt() throws Exception {
        Run run = run(log -> log.setCurrentLevel(LogLevel.INFO).debug("hidden"));

        assertEquals(List.of(), run.written());
    }

    // ---------------------------------------------------------------------
    // Only DEBUG and TRACE answer to the threshold.
    // ---------------------------------------------------------------------

    @Test
    public void raisingTheThresholdDoesNotSilenceInfo() throws Exception {
        Run run = run(log -> log.setCurrentLevel(LogLevel.ERROR).info("still printed"));

        assertEquals("INFO is not silenceable, however high the threshold goes",
            List.of("INFO"), run.levelsWritten());
    }

    @Test
    public void aLevelBelowTheThresholdIsStillWrittenUnlessItIsDebugOrTrace() throws Exception {
        Run run = run(log -> log.setCurrentLevel(LogLevel.WARN).info("just under"));

        assertEquals(List.of("INFO"), run.levelsWritten());
    }

    @Test
    public void theHighestThresholdSilencesOnlyDebugAndTrace() throws Exception {
        Run run = run(log -> {
            log.setCurrentLevel(LogLevel.FATAL);
            log.trace("hidden");
            log.debug("hidden");
            log.info("shown");
            log.warn("shown");
            log.error("shown");
            log.fatal("shown");
        });

        assertEquals(List.of("INFO", "WARN", "ERROR", "FATAL"), run.levelsWritten());
    }

    // ---------------------------------------------------------------------
    // The threshold must stay put while logging.
    // ---------------------------------------------------------------------

    @Test
    public void everyLevelAtOrAboveTheThresholdIsWrittenRegardlessOfOrder() throws Exception {
        Run run = run(log -> {
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
    public void theThresholdSurvivesRepeatedLoggingAtTheSameLevel() throws Exception {
        Run run = run(log -> {
            log.info("one");
            log.info("two");
            log.info("three");
        });

        assertEquals(List.of("INFO", "INFO", "INFO"), run.levelsWritten());
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
    public void severityAndDeclarationOrderDisagreeSoFilteringMustUseSeverity() {
        // RESET is declared last but carries the lowest severity, so a check
        // written against ordinal() would rank it above FATAL.
        assertTrue(LogLevel.RESET.ordinal() > LogLevel.FATAL.ordinal());
        assertTrue(LogLevel.RESET.severity() < LogLevel.TRACE.severity());
    }
}
