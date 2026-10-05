package io.github.ferizoozoo.thislog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/**
 * How a message pattern and its arguments become one string, and which
 * argument, if any, is the cause.
 */
public class PlaceholdersTest {

    private static String format(String message, Object... params) {
        return Placeholders.formatMessageWithParams(message, params);
    }

    @Test
    public void eachPlaceholderTakesTheNextArgumentInOrder() {
        assertEquals("user 42 did checkout", format("user {} did {}", 42, "checkout"));
    }

    @Test
    public void aPlaceholderCanSitAtEitherEndOfTheMessage() {
        assertEquals("start middle end", format("{} middle {}", "start", "end"));
    }

    @Test
    public void adjacentPlaceholdersAreFilledSeparately() {
        assertEquals("ab", format("{}{}", "a", "b"));
    }

    @Test
    public void aPlaceholderWithNoArgumentLeftIsKeptAsWritten() {
        assertEquals("too few one {}", format("too few {} {}", "one"));
    }

    @Test
    public void anArgumentWithNoPlaceholderLeftIsLeftOut() {
        assertEquals("too many one", format("too many {}", "one", "two"));
    }

    @Test
    public void aMessageWithoutArgumentsIsReturnedUntouched() {
        assertEquals("nothing to fill {}", format("nothing to fill {}"));
    }

    @Test
    public void aMissingArgumentArrayLeavesTheMessageAlone() {
        assertEquals("as written {}", Placeholders.formatMessageWithParams("as written {}", null));
    }

    @Test
    public void aMissingMessageStaysMissing() {
        assertNull(format(null, "ignored"));
    }

    @Test
    public void aNullArgumentIsRenderedAsNull() {
        assertEquals("value null", format("value {}", (Object) null));
    }

    @Test
    public void aLoneBraceIsNotAPlaceholder() {
        assertEquals("{ x } and {x}", format("{ {} } and {x}", "x"));
    }

    @Test
    public void anArrayArgumentShowsWhatIsInIt() {
        assertEquals("ids [1, 2, 3]", format("ids {}", new int[] { 1, 2, 3 }));
    }

    @Test
    public void anArrayOfObjectsShowsItsElements() {
        assertEquals("names [ann, bob]", format("names {}", (Object) new String[] { "ann", "bob" }));
    }

    @Test
    public void aNestedArrayShowsEveryLevel() {
        assertEquals("grid [[1, 2], [3]]", format("grid {}", (Object) new int[][] { { 1, 2 }, { 3 } }));
    }

    @Test
    public void aPlaceholderInsideAnArgumentIsNotFilledAgain() {
        assertEquals("first {} second", format("first {} {}", "{}", "second"));
    }

    @Test
    public void aThrowableInLastPlaceIsTheCause() {
        var cause = new IllegalStateException("boom");

        assertSame(cause, Placeholders.trailingThrowable(new Object[] { 4711, cause }));
    }

    @Test
    public void aThrowableAnywhereElseIsJustAnArgument() {
        var notLast = new IllegalStateException("boom");

        assertNull(Placeholders.trailingThrowable(new Object[] { notLast, 4711 }));
    }

    @Test
    public void noArgumentsMeansNoCause() {
        assertNull(Placeholders.trailingThrowable(new Object[0]));
        assertNull(Placeholders.trailingThrowable(null));
    }

    @Test
    public void aNullInLastPlaceIsNotACause() {
        assertNull(Placeholders.trailingThrowable(new Object[] { "x", null }));
    }
}
