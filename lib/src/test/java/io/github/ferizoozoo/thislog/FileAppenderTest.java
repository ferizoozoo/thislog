package io.github.ferizoozoo.thislog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import static org.junit.Assert.assertEquals;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileAppenderTest {

    private static final String NL = System.lineSeparator();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private final List<FileAppender> opened = new ArrayList<>();

    @After
    public void closeWhatWasOpened() {
        for (var appender : opened) {
            appender.close();
        }
    }

    private FileAppender appenderOn(File sink) {
        var appender = FileAppender.create(sink.getAbsolutePath(), LogEvent::getMessage);
        opened.add(appender);
        return appender;
    }

    private static LogEvent event(String message) {
        return LogEvent.create(message, LogLevel.INFO, 0L, "com.acme.Audit");
    }

    private static String contentsOf(File sink) throws Exception {
        return Files.readString(sink.toPath(), StandardCharsets.UTF_8);
    }

    @Test
    public void aFileCanKeepMoreDetailThanTheConsole() throws Exception {
        File sink = tempFolder.newFile();
        var console = new ByteArrayOutputStream();
        var consoleAppender = StreamAppender.create(console, PatternFormatter.create("%m"));
        consoleAppender.setLogLevel(LogLevel.INFO);
        var file = FileAppender.create(sink.getAbsolutePath(), LogEvent::getMessage);
        var log = Logging.create("com.acme.Audit", LogOptions.createFromEnvironment()
                .withLevel(LogLevel.DEBUG)
                .withAppender(consoleAppender));
        log.addAppender(file);

        log.debug("details for the file");
        log.info("for both");
        log.close();

        assertEquals("for both" + NL, console.toString(StandardCharsets.UTF_8));
        assertEquals("details for the file" + NL + "for both" + NL, contentsOf(sink));
    }

    @Test
    public void closingTheLastHolderWritesOutWhatWasBuffered() throws Exception {
        File sink = tempFolder.newFile();
        var appender = appenderOn(sink);

        appender.append(event("held in the buffer"));
        appender.close();

        assertEquals("held in the buffer" + NL, contentsOf(sink));
    }

    @Test
    public void closingTwiceLetsGoOfTheSharedFileOnlyOnce() throws Exception {
        File sink = tempFolder.newFile();
        var first = appenderOn(sink);
        var second = appenderOn(sink);

        first.close();
        first.close();
        second.append(event("the other holder is still writing"));
        second.flush();

        assertEquals("a second close must not give up the other appender's hold",
                "the other holder is still writing" + NL, contentsOf(sink));
    }

    @Test
    public void aClosedAppenderDiscardsWhatItIsHanded() throws Exception {
        File sink = tempFolder.newFile();
        var first = appenderOn(sink);
        var second = appenderOn(sink);

        first.close();
        first.append(event("too late"));
        second.flush();

        assertEquals("a closed appender no longer holds the file it would write to",
                "", contentsOf(sink));
    }

    @Test
    public void flushingAClosedAppenderLeavesTheSharedBufferAlone() throws Exception {
        File sink = tempFolder.newFile();
        var first = appenderOn(sink);
        var second = appenderOn(sink);

        second.append(event("still buffered"));
        first.close();
        first.flush();

        assertEquals("the buffer belongs to whoever still holds the file",
                "", contentsOf(sink));

        second.flush();

        assertEquals("still buffered" + NL, contentsOf(sink));
    }
}
