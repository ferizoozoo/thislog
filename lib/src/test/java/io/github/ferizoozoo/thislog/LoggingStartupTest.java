package io.github.ferizoozoo.thislog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LoggingStartupTest {

    private static final String NL = System.lineSeparator();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

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

    private static Logging configured(String name, LogOptions options) {
        var log = Logging.create(name, LogOptions.createFromEnvironment());
        log.changeOptions(options);
        return log;
    }

    private static LogOptions plainlyTo(LogDestination destination) {
        return LogOptions.createFromEnvironment()
                .withDestination(destination)
                .withFormatter(PatternFormatter.create(PatternFormatter.DEFAULT_PATTERN));
    }

    @Test
    public void aConfigurationThatCannotBeAppliedStillLeavesAUsableLogger() throws Exception {
        var directory = tempFolder.newFolder();

        var log = configured("com.acme.Boot",
                plainlyTo(LogDestination.file(directory.getAbsolutePath())));

        log.info(() -> "the application carries on");

        assertEquals("a logger whose configuration would not apply should still write",
                "the application carries on" + NL, stdoutText());
    }

    @Test
    public void anUnreadableConfigurationIsAnnouncedOnStandardError() throws Exception {
        var directory = tempFolder.newFolder();
        Logging.create("com.acme.Boot",
                plainlyTo(LogDestination.file(directory.getAbsolutePath())));

        assertTrue("the reason should reach stderr, got: " + stderrText(),
                stderrText().contains("FileNotFoundException"));
        assertTrue("and it should say what happened instead, got: " + stderrText(),
                stderrText().contains("falling back to stdout"));
    }

    @Test
    public void theNoticeAboutAnUnreadableConfigurationDoesNotLandInTheLog() throws Exception {
        var directory = tempFolder.newFolder();

        configured("com.acme.Boot",
                plainlyTo(LogDestination.file(directory.getAbsolutePath())));

        assertEquals("a configuration notice is not a log line", "", stdoutText());
    }

    @Test
    public void aDestinationThatCannotBeOpenedFallsBackToStdout() throws Exception {
        var directory = tempFolder.newFolder();

        var log = Logging.create("com.acme.Boot",
                plainlyTo(LogDestination.file(directory.getAbsolutePath())));

        log.info(() -> "still audible");

        assertEquals("a file that cannot be opened should not silence the logger",
                "still audible" + NL, stdoutText());
        assertTrue("and the failure should be announced, got: " + stderrText(),
                stderrText().contains("falling back to stdout"));
    }

    @Test
    public void aLoggerThatFellBackToStdoutSaysSoInItsOptions() throws Exception {
        var directory = tempFolder.newFolder();

        var log = Logging.create("com.acme.Boot",
                plainlyTo(LogDestination.file(directory.getAbsolutePath())));

        assertEquals("the options should name the destination actually in use",
                LogDestination.STDOUT, log.getOptions().getDestination());
    }

    @Test
    public void aReconfigureThatCannotBeAppliedChangesNothingAtAll() throws Exception {
        var sink = tempFolder.newFile();
        var directory = tempFolder.newFolder();
        var before = plainlyTo(LogDestination.file(sink.getAbsolutePath())).withLevel(LogLevel.INFO);
        var log = Logging.create("com.acme.Boot", before);

        log.changeOptions(before
                .withDestination(LogDestination.file(directory.getAbsolutePath()))
                .withFormatter(PatternFormatter.create("replaced %m"))
                .withLevel(LogLevel.DEBUG));
        log.debug(() -> "below the level that was in force");
        log.info(() -> "rendered as before");
        log.close();

        assertEquals("the options should still be the ones in force", before, log.getOptions());
        assertEquals("the level should not move on its own", LogLevel.INFO, log.getCurrentLogLevel());
        assertEquals("neither the level nor the formatter of a failed move should apply",
                java.util.List.of("rendered as before"), Files.readAllLines(sink.toPath()));
        assertTrue("and the notice should say what happened instead, got: " + stderrText(),
                stderrText().contains("keeping the previous configuration"));
    }

    @Test
    public void aClosedLoggerStaysClosedWhenItsReconfigureCannotBeApplied() throws Exception {
        var directory = tempFolder.newFolder();
        var log = Logging.create("com.acme.Boot", plainlyTo(LogDestination.STDOUT));
        log.close();

        log.changeOptions(plainlyTo(LogDestination.file(directory.getAbsolutePath())));
        log.info(() -> "discarded");

        assertEquals("a failed revival should not reopen the logger somewhere else", "", stdoutText());
    }

    @Test
    public void aWorkingConfigurationIsAppliedAndAnnouncesNothing() throws Exception {
        var sink = tempFolder.newFile();

        var log = configured("com.acme.Boot",
                plainlyTo(LogDestination.file(sink.getAbsolutePath())));
        log.error(() -> "to the file");

        assertEquals("nothing went wrong, so nothing should be said", "", stderrText());
        assertEquals("", stdoutText());
        assertEquals("to the file" + NL, Files.readString(sink.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void anOwnedFileIsClosedWhenTheLoggerIsClosed() throws Exception {
        var sink = tempFolder.newFile();
        var log = configured("com.acme.Boot",
                plainlyTo(LogDestination.file(sink.getAbsolutePath())));

        log.info(() -> "buffered");
        log.close();

        assertEquals("buffered" + NL, Files.readString(sink.toPath(), StandardCharsets.UTF_8));
        log.info(() -> "after closing");
        assertEquals("a closed logger writes nowhere at all", "", stdoutText());
        assertEquals("and nothing more reaches the file it released",
                "buffered" + NL, Files.readString(sink.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    public void closingTwiceIsHarmless() throws Exception {
        var sink = tempFolder.newFile();
        var log = configured("com.acme.Boot",
                plainlyTo(LogDestination.file(sink.getAbsolutePath())));

        log.info(() -> "buffered");
        log.close();
        log.close();

        assertEquals("buffered" + NL, Files.readString(sink.toPath(), StandardCharsets.UTF_8));
        assertEquals("a second close must not report anything", "", stderrText());
    }

    @Test
    public void aClosedLoggerNeverThrowsFromALoggingCall() throws Exception {
        var sink = tempFolder.newFile();
        var log = configured("com.acme.Boot",
                plainlyTo(LogDestination.file(sink.getAbsolutePath())));
        log.close();

        log.info(() -> "dropped");
        log.error(() -> "dropped too", new IllegalStateException("and its cause"));

        assertFalse("a closed logger should not even build the message",
                log.isEnabled(LogLevel.ERROR));
        assertEquals("", stdoutText());
        assertEquals("", stderrText());
    }

    @Test
    public void reconfiguringAClosedLoggerPutsItBackToWork() throws Exception {
        var sink = tempFolder.newFile();
        var log = configured("com.acme.Boot",
                plainlyTo(LogDestination.file(sink.getAbsolutePath())));
        log.close();

        log.changeOptions(plainlyTo(LogDestination.STDOUT));
        log.info(() -> "back in service");

        assertEquals("changeOptions is how a closed logger is revived",
                "back in service" + NL, stdoutText());
    }

    private static PrintStream refusingStream() {
        return new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("No space left on device");
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                throw new IOException("No space left on device");
            }
        }, true, StandardCharsets.UTF_8);
    }

    @Test
    public void aDestinationThatRefusesWritesIsReportedOnStandardError() {
        System.setOut(refusingStream());
        var log = configured("com.acme.Boot", plainlyTo(LogDestination.STDOUT));

        log.error(() -> "this never lands");

        assertTrue("a silent write failure should still be announced, got: " + stderrText(),
                stderrText().contains("com.acme.Boot"));
        assertTrue(stderrText().contains("log output may be lost"));
    }

    @Test
    public void aFailingDestinationIsReportedOnceRatherThanPerLine() {
        System.setOut(refusingStream());
        var log = configured("com.acme.Boot", plainlyTo(LogDestination.STDOUT));

        for (int i = 0; i < 20; i++) {
            log.error(() -> "this never lands");
        }

        assertEquals("one broken destination is one notice, not twenty",
                1, stderrText().split("log output may be lost", -1).length - 1);
    }

    @Test
    public void aWorkingDestinationIsNeverReported() {
        var log = configured("com.acme.Boot", plainlyTo(LogDestination.STDOUT));

        log.error(() -> "this lands");

        assertEquals("this lands" + NL, stdoutText());
        assertEquals("nothing failed, so nothing should be said", "", stderrText());
    }

    @Test
    public void movingToAWorkingDestinationEarnsAFreshVerdict() {
        System.setOut(refusingStream());
        var log = configured("com.acme.Boot", plainlyTo(LogDestination.STDOUT));
        log.error(() -> "lost");
        assertTrue(stderrText().contains("log output may be lost"));

        System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
        log.changeOptions(plainlyTo(LogDestination.STDOUT));
        log.error(() -> "lands");

        assertEquals("lands" + NL, stdoutText());
        assertEquals("one notice from the destination that actually failed",
                1, stderrText().split("log output may be lost", -1).length - 1);
    }

    @Test
    public void aBufferedLineBelowTheFlushThresholdIsNotYetJudged() {
        System.setOut(refusingStream());
        var log = configured("com.acme.Boot", plainlyTo(LogDestination.STDOUT));

        log.info(() -> "below the flush threshold");

        assertFalse("checkError flushes, so it is only consulted where a flush already happens",
                stderrText().contains("log output may be lost"));
    }

    @Test
    public void anUnconfiguredLoggerWritesWithTheEnvironmentDefaults() {
        var log = Logging.create("com.acme.Boot", LogOptions.createFromEnvironment());

        log.info(() -> "plain by default");

        assertEquals("plain by default" + NL, stdoutText());
        assertEquals("the default configuration is not a failure", "", stderrText());
    }

    @Test
    public void aFileHandedToTheConstructorIsTheFileThatGetsWrittenTo() throws Exception {
        var sink = tempFolder.newFile();
        var log = Logging.create("com.acme.Boot", plainlyTo(LogDestination.file(sink.getAbsolutePath())));

        log.info(() -> "this belongs in the file");
        log.close();

        assertEquals("the configured file is where the line goes",
                java.util.List.of("this belongs in the file"),
                Files.readAllLines(sink.toPath()));
        assertEquals("opening the configured destination is not a failure", "", stderrText());
        assertEquals("and nothing should have leaked onto the console", "", stdoutText());
    }

    @Test
    public void leavingAFileFlushesAndClosesIt() throws Exception {
        var first = tempFolder.newFile();
        var second = tempFolder.newFile();
        var log = Logging.create("com.acme.Boot", plainlyTo(LogDestination.file(first.getAbsolutePath())));

        log.info(() -> "written before the move");
        log.changeOptions(plainlyTo(LogDestination.file(second.getAbsolutePath())));

        assertEquals("the file a logger leaves should not be holding unwritten lines",
                java.util.List.of("written before the move"),
                Files.readAllLines(first.toPath()));
    }

    @Test
    public void leavingTheConsoleLeavesItUsable() {
        var log = configured("com.acme.Boot", plainlyTo(LogDestination.STDOUT));
        log.info(() -> "on stdout");

        log.changeOptions(plainlyTo(LogDestination.STDERR));

        System.out.print("still open");
        assertFalse("closing System.out would take the whole process's output down",
                System.out.checkError());
        assertTrue("and writes after the move should still land, got: " + stdoutText(),
                stdoutText().contains("still open"));
    }

    @Test
    public void aReconfigureThatCannotBeAppliedLeavesTheLoggerOnTheDestinationItHad() throws Exception {
        var sink = tempFolder.newFile();
        var directory = tempFolder.newFolder();
        var log = Logging.create("com.acme.Boot", plainlyTo(LogDestination.file(sink.getAbsolutePath())));

        log.changeOptions(plainlyTo(LogDestination.file(directory.getAbsolutePath())));
        log.info(() -> "still going to the original file");
        log.close();

        assertEquals("a destination that would not open should not cost the one that did",
                java.util.List.of("still going to the original file"),
                Files.readAllLines(sink.toPath()));
        assertTrue("and the failure should still be announced, got: " + stderrText(),
                stderrText().contains("cannot apply the logging configuration"));
    }

    @Test
    public void aQuietLoggerStopsHoldingItsLines() throws Exception {
        var sink = tempFolder.newFile();
        Flusher.setIntervalForTesting(50);
        try {
            var log = LoggingFactory.get("com.acme.Quiet",
                    plainlyTo(LogDestination.file(sink.getAbsolutePath())));

            log.info(() -> "written, then left alone");

            var deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (Files.size(sink.toPath()) == 0 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }

            assertEquals("a quiet INFO line should reach the file without a close()",
                    java.util.List.of("written, then left alone"),
                    Files.readAllLines(sink.toPath()));
        } finally {
            Flusher.resetIntervalForTesting();
        }
    }

    private static boolean flushThreadIsRunning() {
        return Thread.getAllStackTraces().keySet().stream()
                .anyMatch(thread -> thread.getName().equals("thislog-flush") && thread.isAlive());
    }

    @Test
    public void anOpenLoggerKeepsBeingFlushedAfterTheFirstTick() throws Exception {
        var sink = tempFolder.newFile();
        Flusher.setIntervalForTesting(50);
        try {
            var log = LoggingFactory.get("com.acme.Steady",
                    plainlyTo(LogDestination.file(sink.getAbsolutePath())));

            // Several ticks go by before the line is written, so it is not
            // the first flush that carries it out.
            Thread.sleep(300);
            log.info(() -> "written well after the timer started");

            var deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (Files.size(sink.toPath()) == 0 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }

            assertEquals("the timer should keep flushing while a logger is open",
                    java.util.List.of("written well after the timer started"),
                    Files.readAllLines(sink.toPath()));
        } finally {
            Flusher.resetIntervalForTesting();
        }
    }

    @Test
    public void theFlushThreadStopsOnceNoLoggerIsLeftOpen() throws Exception {
        var sink = tempFolder.newFile();
        Flusher.setIntervalForTesting(50);
        try {
            var log = LoggingFactory.get("com.acme.Brief",
                    plainlyTo(LogDestination.file(sink.getAbsolutePath())));
            assertTrue("a file logger should start the flush thread", flushThreadIsRunning());

            log.close();

            var deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (flushThreadIsRunning() && System.nanoTime() < deadline) {
                // Loggers other tests left open are only forgotten once they
                // have been collected.
                System.gc();
                Thread.sleep(20);
            }

            assertFalse("with nothing open, the flush thread should stop", flushThreadIsRunning());

            LoggingFactory.get("com.acme.Again",
                    plainlyTo(LogDestination.file(sink.getAbsolutePath())));

            assertTrue("a file opened afterwards should start it again", flushThreadIsRunning());
        } finally {
            Flusher.resetIntervalForTesting();
        }
    }

    @Test
    public void aConsoleOnlyLoggerStartsNoThread() {
        Flusher.setIntervalForTesting(50);
        try {
            LoggingFactory.get("com.acme.ConsoleOnly", plainlyTo(LogDestination.STDOUT))
                    .info(() -> "nothing here buffers");

            assertFalse("a process logging only to the console should not start a flush thread",
                    Thread.getAllStackTraces().keySet().stream()
                            .anyMatch(t -> "thislog-flush".equals(t.getName())));
        } finally {
            Flusher.resetIntervalForTesting();
        }
    }

    @Test
    public void aNameIsStillRequiredBeforeAnythingIsOpened() {
        var thrown = false;
        try {
            Logging.create(null, LogOptions.createFromEnvironment());
        } catch (NullPointerException e) {
            thrown = true;
            assertEquals("name", e.getMessage());
        }
        assertTrue("a null name is a programming mistake, not a configuration one", thrown);
    }
}
