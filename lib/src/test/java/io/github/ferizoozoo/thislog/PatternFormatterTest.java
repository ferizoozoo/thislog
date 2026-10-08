package io.github.ferizoozoo.thislog;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PatternFormatterTest {
    private static final String NL = System.lineSeparator();

    private static final long AT = 1_700_000_000_000L;

    private static final String NAME = "com.acme.checkout.OrderRouter";

    private static final IllegalStateException FAILURE = new IllegalStateException("pricing failed");

    private static LogEvent theEvent() {
        return LogEvent.create("Careful", LogLevel.WARN, AT, NAME);
    }

    private static LogEvent aFailure() {
        return LogEvent.create("checkout failed", LogLevel.ERROR, AT, NAME, FAILURE);
    }

    private static String rendered(String pattern) {
        return PatternFormatter.create(pattern).format(theEvent());
    }

    private static String clock(String datePattern) {
        return DateTimeFormatter.ofPattern(datePattern)
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(AT));
    }

    private static String appended(LogFormatter formatter, LogEvent event) {
        var sink = new ByteArrayOutputStream();
        var appender = StreamAppender.wrapping(sink, formatter);
        appender.append(event);
        return sink.toString(StandardCharsets.UTF_8);
    }

    // Widths

    @Test
    public void aMinimumWidthPadsOnTheLeft() {
        assertEquals(" WARN|", rendered("%5level|"));
    }

    @Test
    public void aMinusPadsOnTheRightInstead() {
        assertEquals("WARN |", rendered("%-5level|"));
    }

    @Test
    public void aValueAlreadyAsWideAsTheMinimumIsLeftAlone() {
        assertEquals("Careful", rendered("%3m"));
    }

    @Test
    public void aMaximumWidthKeepsTheStartOfTheValue() {
        assertEquals("com.acme", rendered("%.8logger"));
    }

    @Test
    public void aMinimumAndAMaximumGiveAFixedColumn() {
        assertEquals("WARN |Careful|", rendered("%-5.5level|%-5.7m|"));
        assertEquals("Caref     |", rendered("%-10.5m|"));
    }

    @Test
    public void everyLevelLinesUpInAPaddedColumn() {
        var formatter = PatternFormatter.create("%-5level|");
        for (LogLevel level : LogLevel.values()) {
            var line = formatter.format(LogEvent.create("x", level, AT, NAME));
            assertEquals(level + " should fill the column", 6, line.length());
        }
    }

    @Test
    public void aWidthWithNoConversionAfterItIsLiteral() {
        assertEquals("100%5 done", rendered("100%5 done"));
        assertEquals("50%- off", rendered("50%- off"));
        assertEquals("%.level", rendered("%.level"));
    }

    @Test
    public void aWidthInFrontOfAnUnknownConversionIsLiteral() {
        assertEquals("%-5nonsense", rendered("%-5nonsense"));
    }

    @Test
    public void thePlainConversionsAreUnchangedByTheModifierSyntax() {
        assertEquals("WARN Careful", rendered("%level %m"));
        assertEquals("50% done: Careful", rendered("50% done: %s"));
    }

    // Dates

    @Test
    public void aDateCanCarryItsOwnFormat() {
        assertEquals(clock("HH:mm:ss"), rendered("%date{HH:mm:ss}"));
    }

    @Test
    public void aDateWithoutAFormatKeepsTheDefault() {
        assertEquals(clock(PatternFormatter.DEFAULT_DATE_PATTERN), rendered("%date"));
    }

    @Test
    public void aDateFormatCanBePaddedLikeAnyOtherConversion() {
        assertEquals(clock("HH:mm") + "   |", rendered("%-8date{HH:mm}|"));
    }

    @Test
    public void twoDatesInOnePatternKeepTheirOwnFormats() {
        assertEquals(clock("yyyy") + " " + clock("HH"), rendered("%date{yyyy} %date{HH}"));
    }

    @Test
    public void aDateFormatThatIsNotOneIsRejectedWhenThePatternIsCreated() {
        assertThrows(IllegalArgumentException.class, () -> PatternFormatter.create("%date{bogus} %m"));
    }

    @Test
    public void anUnclosedBraceIsNotADateFormat() {
        assertEquals(clock(PatternFormatter.DEFAULT_DATE_PATTERN) + "{HH:mm Careful", rendered("%date{HH:mm %m"));
    }

    @Test
    public void onlyTheDateTakesABraceSoOtherBracesStayText() {
        assertEquals("Careful{x}", rendered("%m{x}"));
    }

    // Exceptions

    @Test
    public void aPatternWithoutExLeavesTheTraceToTheAppender() {
        var formatter = PatternFormatter.create("%level %m");

        assertFalse(formatter.rendersThrown());
        assertEquals("ERROR checkout failed" + NL + StackTraces.render(FAILURE) + NL,
                appended(formatter, aFailure()));
    }

    @Test
    public void exPutsTheTraceWhereThePatternSays() {
        var formatter = PatternFormatter.create("%m%ex [end]");

        assertTrue(formatter.rendersThrown());
        assertEquals("checkout failed" + NL + StackTraces.render(FAILURE) + " [end]" + NL,
                appended(formatter, aFailure()));
    }

    @Test
    public void exRendersTheSameTraceTheAppenderWouldHave() {
        assertEquals(appended(PatternFormatter.create("%m"), aFailure()),
                appended(PatternFormatter.create("%m%ex"), aFailure()));
    }

    @Test
    public void exRendersNothingWhenThereIsNoThrowable() {
        assertEquals("Careful|", rendered("%m%ex|"));
    }

    @Test
    public void nopexDropsTheTraceAltogether() {
        var formatter = PatternFormatter.create("%m%nopex");

        assertTrue(formatter.rendersThrown());
        assertEquals("checkout failed" + NL, appended(formatter, aFailure()));
    }

    @Test
    public void exIsAConversionOfItsOwnRatherThanThePrefixOfAWord() {
        assertEquals("%example", rendered("%example"));
    }

    @Test
    public void theColouredWrapperStillLetsThePatternPlaceTheTrace() {
        var colored = LogFormatter.colored(PatternFormatter.create("%m%nopex"));

        assertTrue(colored.rendersThrown());
        assertFalse(appended(colored, aFailure()).contains("pricing failed"));
    }

    @Test
    public void aLambdaFormatterStillGetsTheTraceFromTheAppender() {
        LogFormatter lambda = LogEvent::getMessage;

        assertFalse(lambda.rendersThrown());
        assertTrue(appended(lambda, aFailure()).contains("pricing failed"));
    }
}
