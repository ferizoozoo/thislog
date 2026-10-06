package io.github.ferizoozoo.thislog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.After;
import static org.junit.Assert.assertArrayEquals;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Which bytes a line becomes. A PrintStream already knows its charset, so a
 * console gets text in the encoding it reads; a file is always UTF-8; and a
 * bare OutputStream, which has no charset of its own, gets UTF-8 too.
 */
public class StreamAppenderCharsetTest {

    private static final String NL = System.lineSeparator();
    private static final Charset LATIN_1 = StandardCharsets.ISO_8859_1;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private PrintStream originalOut;

    @Before
    public void rememberStdout() {
        LoggingFactory.clear();
        originalOut = System.out;
    }

    @After
    public void restoreStdout() {
        LoggingFactory.clear();
        System.setOut(originalOut);
    }

    private static LogEvent event(String message) {
        return LogEvent.create(message, LogLevel.INFO, 0L, "com.acme.Cafe");
    }

    private static byte[] bytes(String text, Charset charset) {
        return (text + NL).getBytes(charset);
    }

    @Test
    public void aPrintStreamGetsTheLineInItsOwnCharset() {
        var sink = new ByteArrayOutputStream();
        var appender = StreamAppender.wrapping(new PrintStream(sink, true, LATIN_1), LogEvent::getMessage);

        appender.append(event("café"));
        appender.flush();

        assertArrayEquals("é is the single byte 0xE9 in ISO-8859-1",
                bytes("café", LATIN_1), sink.toByteArray());
    }

    @Test
    public void aUtf8PrintStreamGetsUtf8() {
        var sink = new ByteArrayOutputStream();
        var appender = StreamAppender.wrapping(
                new PrintStream(sink, true, StandardCharsets.UTF_8), LogEvent::getMessage);

        appender.append(event("café"));
        appender.flush();

        assertArrayEquals("é is the two bytes 0xC3 0xA9 in UTF-8",
                bytes("café", StandardCharsets.UTF_8), sink.toByteArray());
    }

    @Test
    public void aBareOutputStreamGetsUtf8() {
        var sink = new ByteArrayOutputStream();
        var appender = StreamAppender.wrapping(sink, LogEvent::getMessage);

        appender.append(event("café"));
        appender.flush();

        assertArrayEquals(bytes("café", StandardCharsets.UTF_8), sink.toByteArray());
    }

    @Test
    public void aFileIsAlwaysWrittenAsUtf8() throws Exception {
        File file = tempFolder.newFile();
        var appender = FileAppender.create(file.getAbsolutePath(), LogEvent::getMessage);

        appender.append(event("café"));
        appender.close();

        assertArrayEquals("a file should not depend on how the JVM was started",
                bytes("café", StandardCharsets.UTF_8), Files.readAllBytes(file.toPath()));
    }

    @Test
    public void aConsoleLoggerWritesInTheConsolesCharset() {
        var console = new ByteArrayOutputStream();
        System.setOut(new PrintStream(console, true, LATIN_1));
        var log = Logging.create("com.acme.Cafe", LogOptions.createFromEnvironment()
                .withDestination(LogDestination.STDOUT)
                .withFormatter(PatternFormatter.create("%m")));

        log.info("café");
        log.flush();

        assertArrayEquals("stdout should get the bytes its own charset expects",
                bytes("café", LATIN_1), console.toByteArray());
    }

    @Test
    public void textOutsideTheConsolesCharsetBecomesAQuestionMarkRatherThanGarbage() {
        var sink = new ByteArrayOutputStream();
        var appender = StreamAppender.wrapping(new PrintStream(sink, true, LATIN_1), LogEvent::getMessage);

        appender.append(event("price: 5€"));
        appender.flush();

        assertArrayEquals("€ has no ISO-8859-1 byte, so the charset's replacement stands in",
                bytes("price: 5?", LATIN_1), sink.toByteArray());
    }
}
