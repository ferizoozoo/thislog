package io.github.ferizoozoo.thislog;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RollingFileAppenderTest {

    private static final String NL = System.lineSeparator();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private final List<RollingFileAppender> opened = new ArrayList<>();

    @After
    public void closeWhatWasOpened() {
        for (var appender : opened) {
            appender.close();
        }
    }

    private RollingFileAppender appenderOn(File sink, long intervalMs) {
        var appender = RollingFileAppender.create(sink.getAbsolutePath(), LogEvent::getMessage, intervalMs);
        opened.add(appender);
        return appender;
    }

    private static LogEvent event(String message) {
        return LogEvent.create(message, LogLevel.INFO, 0L, "com.acme.Audit");
    }

    private static List<String> contentsOfEveryFileIn(File folder) throws Exception {
        var contents = new ArrayList<String>();
        for (var file : folder.listFiles()) {
            try (var in = file.getName().endsWith(".gz")
                    ? new GZIPInputStream(new FileInputStream(file))
                    : new FileInputStream(file)) {
                contents.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        contents.sort(null);
        return contents;
    }

    @Test
    public void aLineWrittenAfterTheIntervalGoesToANewFile() throws Exception {
        var folder = tempFolder.newFolder();
        var appender = appenderOn(new File(folder, "app.log"), 50);

        appender.append(event("before the roll"));
        Thread.sleep(100);
        appender.append(event("after the roll"));
        appender.flush();

        assertEquals(List.of("after the roll" + NL, "before the roll" + NL), contentsOfEveryFileIn(folder));
    }

    @Test(timeout = 10_000)
    public void severalRollsWithinOneSecondKeepEveryLine() throws Exception {
        var folder = tempFolder.newFolder();
        var appender = appenderOn(new File(folder, "app.log"), 20);

        var written = new ArrayList<String>();
        for (int i = 0; i < 5; i++) {
            written.add("line " + i + NL);
            appender.append(event("line " + i));
            Thread.sleep(30);
        }
        appender.flush();

        assertFalse(appender.checkFailure());
        var contents = new ArrayList<>(contentsOfEveryFileIn(folder));
        contents.removeIf(String::isEmpty);
        assertEquals(written, contents);
    }

    @Test
    public void aRolledFileIsCompressedAndTheOriginalRemoved() throws Exception {
        var folder = tempFolder.newFolder();
        var appender = appenderOn(new File(folder, "app.log"), 50);

        appender.append(event("before the roll"));
        Thread.sleep(100);
        appender.append(event("after the roll"));

        assertTrue(new File(folder, "app.log.gz").exists());
        assertFalse(new File(folder, "app.log").exists());
    }

    @Test
    public void aRestartCanCompressItsFirstFileWhenAnEarlierRunAlreadyDidSo() throws Exception {
        var folder = tempFolder.newFolder();
        var sink = new File(folder, "app.log");

        var firstRun = appenderOn(sink, 50);
        firstRun.append(event("first run, before the roll"));
        Thread.sleep(100);
        firstRun.append(event("first run, after the roll"));
        firstRun.close();
        assertTrue(new File(folder, "app.log.gz").exists());

        var secondRun = appenderOn(sink, 50);
        secondRun.append(event("second run, before the roll"));
        Thread.sleep(100);
        secondRun.append(event("second run, after the roll"));
        secondRun.flush();

        assertFalse(secondRun.checkFailure());
        assertFalse(new File(folder, "app.log").exists());
        assertEquals(List.of(), List.of(folder.list((dir, name) -> name.endsWith(".tmp"))));
        var contents = contentsOfEveryFileIn(folder);
        for (var line : List.of("first run, before the roll", "second run, before the roll", "second run, after the roll")) {
            assertTrue(line + " is missing from " + contents, contents.contains(line + NL));
        }
    }

    @Test
    public void closingTheAppenderWritesWhatWasStillBuffered() throws Exception {
        var sink = new File(tempFolder.newFolder(), "app.log");
        var appender = appenderOn(sink, 60_000);

        appender.append(event("written before close"));
        appender.close();

        assertEquals("written before close" + NL, Files.readString(sink.toPath(), StandardCharsets.UTF_8));
    }
}
