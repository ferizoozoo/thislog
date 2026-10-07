package io.github.ferizoozoo.thislog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import org.junit.Test;

public class AsciiColorsTest {
    private static final String ESC = String.valueOf((char) 27);
    private static final String RESET = ESC + "[0m";
    private static final String GREEN = ESC + "[32m";
    private static final String YELLOW = ESC + "[33m";
    private static final String RED = ESC + "[31m";
    private static final String BLUE = ESC + "[34m";

    @Test
    public void everyLevelMapsToAColour() {
        assertEquals(BLUE, AsciiColors.color(LogLevel.TRACE));
        assertEquals(BLUE, AsciiColors.color(LogLevel.DEBUG));
        assertEquals(GREEN, AsciiColors.color(LogLevel.INFO));
        assertEquals(YELLOW, AsciiColors.color(LogLevel.WARN));
        assertEquals(RED, AsciiColors.color(LogLevel.ERROR));
        assertEquals(RED, AsciiColors.color(LogLevel.FATAL));
    }

    @Test
    public void eachColourRendersAsItsEscapeSequence() {
        assertEquals(RESET, AsciiColors.RESET.toString());
        assertEquals(GREEN, AsciiColors.GREEN.toString());
        assertEquals(YELLOW, AsciiColors.YELLOW.toString());
        assertEquals(RED, AsciiColors.RED.toString());
        assertEquals(BLUE, AsciiColors.BLUE.toString());
    }

    @Test
    public void noLevelIsColouredWithTheReset() {
        for (LogLevel level : LogLevel.values()) {
            assertNotEquals("a level worth logging at needs a colour of its own",
                    RESET, AsciiColors.color(level));
        }
    }

    @Test
    public void aColouredMessageIsWrappedInItsLevelAndClosedWithTheReset() {
        assertEquals(YELLOW + "careful" + RESET,
                AsciiColors.coloredMessage("careful", LogLevel.WARN));
    }

    @Test
    public void theColouredFormatterTintsTheLineItWraps() {
        var colored = LogFormatter.colored(LogEvent::getMessage);

        assertEquals(RED + "boom" + RESET,
                colored.format(LogEvent.create("boom", LogLevel.ERROR, 0L, "com.acme.Test")));
    }
}
