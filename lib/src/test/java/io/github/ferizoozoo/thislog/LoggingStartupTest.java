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

/**
 * What a logger does when the configuration it is handed cannot be applied, and
 * when the destination it did open stops accepting writes.
 *
 * <p>Configuration reaches a logger through its constructor and through
 * changeOptions. The one failure those paths can still hit is a
 * destination that will not open; a PrintStream hides the rest behind a flag,
 * so neither is reachable from the other suites.
 */
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

    /** A logger with the options applied. */
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

    // ---------------------------------------------------------------------
    // Configuration that cannot be applied.
    // ---------------------------------------------------------------------

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
        configured("com.acme.Boot",
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

        var log = configured("com.acme.Boot",
                plainlyTo(LogDestination.file(directory.getAbsolutePath())));

        log.info(() -> "still audible");

        assertEquals("a file that cannot be opened should not silence the logger",
                "still audible" + NL, stdoutText());
        assertTrue("and the failure should be announced, got: " + stderrText(),
                stderrText().contains("falling back to stdout"));
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

    // ---------------------------------------------------------------------
    // Ownership of a file the constructor opened.
    //
    // A logger that does not know it owns its file abandons the stream on the
    // way out instead of closing it, and closing is what flushes whatever is
    // still buffered. So the buffered line is the evidence.
    // ---------------------------------------------------------------------

    @Test
    public void anOwnedFileIsClosedWhenTheLoggerIsClosed() throws Exception {
        var sink = tempFolder.newFile();
        var log = configured("com.acme.Boot",
                plainlyTo(LogDestination.file(sink.getAbsolutePath())));

        // INFO is below FLUSH_THRESHOLD, so this only reaches the buffer.
        log.info(() -> "buffered");
        log.close();

        assertEquals("buffered" + NL, Files.readString(sink.toPath(), StandardCharsets.UTF_8));
        log.info(() -> "after closing");
        assertEquals("a closed logger falls back to stdout", "after closing" + NL, stdoutText());
    }

    // ---------------------------------------------------------------------
    // A destination that stops accepting writes.
    // ---------------------------------------------------------------------

    /** Fails every write the way a full disk does, silently, as PrintStream will. */
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

        // A new destination has not failed yet, so a later failure on it must
        // be reported again rather than swallowed by the earlier verdict.
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

    // ---------------------------------------------------------------------
    // What a logger does before anything is configured on it.
    // ---------------------------------------------------------------------

    @Test
    public void anUnconfiguredLoggerWritesWithTheEnvironmentDefaults() {
        // createFromEnvironment resolves the environment, so the defaults are
        // what a logger starts on.
        var log = Logging.create("com.acme.Boot", LogOptions.createFromEnvironment());

        log.info(() -> "plain by default");

        assertEquals("plain by default" + NL, stdoutText());
        assertEquals("the default configuration is not a failure", "", stderrText());
    }

    // ---------------------------------------------------------------------
    // Replacing the stream a logger writes to.
    //
    // Reconfiguration has to release the stream it leaves behind, or a file
    // handle leaks with unwritten lines still in its buffer. The care is in
    // which stream that is: a file belongs to the logger, but System.out
    // belongs to the JVM, and closing one takes the whole process's output
    // with it. Both directions are pinned down here because both have been
    // wrong.
    // ---------------------------------------------------------------------

    @Test
    public void aFileHandedToTheConstructorIsTheFileThatGetsWrittenTo() throws Exception {
        // Not via changeOptions: the constructor is its own path into
        // setupPrinter, and it runs with no previous stream to release.
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

        // INFO sits below the flush threshold, so this line is still in the
        // buffer when the destination is replaced. It reaches the file only if
        // the stream being left behind is flushed and closed.
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

        // System.out is not the logger's to close. If it were closed here, every
        // later write in the process would fail silently, not just this one.
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

        // A directory cannot be opened as a file, so this reconfigure fails
        // after the logger is already running. The replacement is opened before
        // the current stream is released, so a failure costs nothing.
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
