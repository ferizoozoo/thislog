package io.github.ferizoozoo.thislog;

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

/**
 * What a FileAppender does with the file it shares.
 *
 * <p>Every appender on one path writes through the same stream, and the file
 * is only closed when the last of them lets go. So what one appender does
 * after it is closed matters to the others still holding the file.
 */
public class FileAppenderTest {

    private static final String NL = System.lineSeparator();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private final List<FileAppender> opened = new ArrayList<>();

    @After
    public void closeWhatWasOpened() {
        // An open handle would stop the temporary folder from being deleted.
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
