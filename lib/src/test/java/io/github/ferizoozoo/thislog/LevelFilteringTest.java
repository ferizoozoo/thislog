package io.github.ferizoozoo.thislog;

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
import org.junit.Before;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Level handling: which messages reach the destination.
 *
 * <p>The level a logger compares against is fixed at TRACE and there is no
 * longer any way for a caller to move it, so every level a caller can log at
 * is written. What is left to pin down is that the comparison drops nothing of
 * its own accord, that severity stays ordered the way the comparison needs,
 * and that formatting and writing agree on what happened.
 *
 * <p>The cases that raised the threshold and checked what fell below it went
 * with {@code setCurrentLevel}; if a way to configure the level comes back,
 * they belong here again.
 */
public class LevelFilteringTest {

    /** Every level a caller can log at, in ascending severity. */
    private static final List<LogLevel> LEVELS = List.of(
            LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO,
            LogLevel.WARN, LogLevel.ERROR, LogLevel.FATAL);

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /** Records what got as far as formatting, so a drop would be visible. */
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
        var log = Logging.create(nextLoggerName(), LogOptions.initiateOptions());
        log.addOptions(LogOptions.initiateOptions()
                .setFormatter(recorder)
                .setDestination(LogDestination.file(sink.getAbsolutePath())));

        action.accept(log);
        log.close();

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

    /** The levels a threshold lets through: those at or above it. */
    private static List<String> atOrAbove(LogLevel threshold) {
        return LEVELS.stream()
                .filter(level -> level.severity() >= threshold.severity())
                .map(LogLevel::name)
                .toList();
    }

    // ---------------------------------------------------------------------
    // The level a logger starts at filters nothing.
    // ---------------------------------------------------------------------

    @Test
    public void traceIsWrittenAtTheDefaultLevel() throws Exception {
        Run run = run(log -> log.trace("Tracing"));

        assertEquals("the level starts at TRACE, so nothing is filtered",
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
    public void everyLevelIsWrittenAtTheDefaultLevel() throws Exception {
        Run run = run(LevelFilteringTest::logEveryLevel);

        assertEquals(atOrAbove(LogLevel.TRACE), run.levelsWritten());
    }

    // ---------------------------------------------------------------------
    // Formatting and writing agree.
    // ---------------------------------------------------------------------

    @Test
    public void whatIsFormattedIsExactlyWhatIsWritten() throws Exception {
        Run run = run(LevelFilteringTest::logEveryLevel);

        assertEquals("nothing is formatted and then dropped, or written unformatted",
            run.levelsFormatted(), run.levelsWritten());
    }

    // ---------------------------------------------------------------------
    // Logging one message does not change what the next one may do.
    // ---------------------------------------------------------------------

    @Test
    public void loggingDoesNotRaiseTheLevelForLaterMessages() throws Exception {
        Run run = run(log -> {
            log.fatal("a one-off fatal");
            log.info("routine work continues");
            log.warn("and so does this");
        });

        assertEquals("a single high-severity line must not silence what follows",
            List.of("FATAL", "INFO", "WARN"), run.levelsWritten());
    }

    @Test
    public void repeatedLoggingAtTheSameLevelKeepsWriting() throws Exception {
        Run run = run(log -> {
            log.info("one");
            log.info("two");
            log.info("three");
        });

        assertEquals(List.of("INFO", "INFO", "INFO"), run.levelsWritten());
    }

    // ---------------------------------------------------------------------
    // The ordering the comparison relies on.
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
        assertEquals("two levels sharing a severity would be indistinguishable to the comparison",
            LEVELS.size(), LEVELS.stream().map(LogLevel::severity).distinct().count());
    }

    // ---------------------------------------------------------------------
    // What reaching the destination costs.
    //
    // The flush lives inside the synchronized write and runs only for levels
    // at or above the flush threshold, rather than from a finally block on
    // every call.
    // ---------------------------------------------------------------------

    /** Counts the flushes the logger asks for. */
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

    @Before
    public void clearRegistry() {
        LoggingFactory.clear();
    }

    @After
    public void restoreStandardOut() {
        System.setOut(realStdout);
    }

    /** A logger on a counting stdout. The stream must be in place first. */
    private static Loggable loggerOn(CountingStream stream, Recorder recorder) {
        System.setOut(stream);
        var log = Logging.create(nextLoggerName(), LogOptions.initiateOptions());
        log.addOptions(LogOptions.initiateOptions()
                .setFormatter(recorder)
                .setDestination(LogDestination.STDOUT));
        return log;
    }

    @Test
    public void onlyASevereMessageFlushesTheDestination() {
        var counting = new CountingStream();
        var log = loggerOn(counting, new Recorder());

        log.info("written, but not severe enough to flush");
        log.error("severe enough to flush");

        assertEquals("exactly the severe message should flush the stream",
            1, counting.flushes.get());
    }

    private static final AtomicInteger LOGGER_SEQ = new AtomicInteger();

    /** Each test gets its own logger name, so the registry never crosses tests. */
    private static String nextLoggerName() {
        return "test.%s".formatted(LOGGER_SEQ.incrementAndGet());
    }
}
