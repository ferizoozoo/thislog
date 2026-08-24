package org.example;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * Context is a per-thread key/value store, so the interesting behaviour is not
 * the map operations but what happens at thread boundaries.
 */
public class ContextTest {

    private static final long TIMEOUT_MS = 5_000;

    private final Context context = new Context();

    /** Runs {@code work} on a fresh thread and hands back what it returned. */
    private static <T> T onAnotherThread(Callable<T> work) throws Exception {
        var result = new AtomicReference<T>();
        var failure = new AtomicReference<Throwable>();

        var thread = new Thread(() -> {
            try {
                result.set(work.call());
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "context-test-worker");

        thread.start();
        thread.join(TIMEOUT_MS);

        assertFalse("worker thread did not finish within " + TIMEOUT_MS + "ms", thread.isAlive());
        if (failure.get() != null) {
            throw new AssertionError("worker thread failed", failure.get());
        }
        return result.get();
    }

    // ---------------------------------------------------------------------
    // Single-threaded behaviour.
    // ---------------------------------------------------------------------

    @Test
    public void putThenGetReturnsTheValue() {
        context.put("requestId", "a1f3");

        assertEquals("a1f3", context.get("requestId"));
    }

    @Test
    public void getReturnsNullForAnUnknownKey() {
        assertNull(context.get("neverSet"));
    }

    @Test
    public void putReplacesAnExistingValue() {
        context.put("userId", 4711);
        context.put("userId", 9002);

        assertEquals(9002, context.get("userId"));
    }

    @Test
    public void valuesKeepTheirType() {
        context.put("count", 42);
        context.put("enabled", Boolean.TRUE);

        assertEquals(Integer.valueOf(42), context.get("count"));
        assertEquals(Boolean.TRUE, context.get("enabled"));
    }

    @Test
    public void keysAreIndependentOfOneAnother() {
        context.put("requestId", "a1f3");
        context.put("userId", "4711");

        assertEquals("a1f3", context.get("requestId"));
        assertEquals("4711", context.get("userId"));
    }

    @Test
    public void clearRemovesEveryEntry() {
        context.put("requestId", "a1f3");
        context.put("userId", "4711");

        context.clear();

        assertNull(context.get("requestId"));
        assertNull(context.get("userId"));
    }

    @Test
    public void clearOnAnEmptyContextIsHarmless() {
        context.clear();

        assertNull(context.get("anything"));
    }

    @Test
    public void aStoredNullIsIndistinguishableFromAnAbsentKey() {
        context.put("requestId", null);

        // Documents a real limitation: get() cannot tell "set to null" from
        // "never set", so a null value silently behaves like no value at all.
        assertNull(context.get("requestId"));
    }

    // ---------------------------------------------------------------------
    // Thread boundaries -- the reason this class exists.
    // ---------------------------------------------------------------------

    @Test
    public void valuesAreNotVisibleFromAnotherThread() throws Exception {
        context.put("requestId", "a1f3");

        Object seenElsewhere = onAnotherThread(() -> context.get("requestId"));

        assertNull("context must not leak across unrelated threads", seenElsewhere);
    }

    @Test
    public void eachThreadHoldsItsOwnValueForTheSameKey() throws Exception {
        context.put("requestId", "main-thread");

        Object seenElsewhere = onAnotherThread(() -> {
            context.put("requestId", "worker-thread");
            return context.get("requestId");
        });

        assertEquals("worker-thread", seenElsewhere);
        assertEquals("the worker must not have disturbed this thread",
            "main-thread", context.get("requestId"));
    }

    @Test
    public void clearOnOneThreadLeavesAnotherThreadUntouched() throws Exception {
        context.put("requestId", "main-thread");

        onAnotherThread(() -> {
            context.put("requestId", "worker-thread");
            context.clear();
            return null;
        });

        assertEquals("main-thread", context.get("requestId"));
    }

    @Test
    public void aChildThreadDoesNotInheritTheParentsValues() throws Exception {
        context.put("requestId", "a1f3");

        // The child is started from a thread that already has a value, which is
        // exactly the case InheritableThreadLocal would cover. Plain
        // ThreadLocal does not, so work handed to another thread starts blank.
        Object seenByChild = onAnotherThread(() -> context.get("requestId"));

        assertNull(seenByChild);
    }

    @Test
    public void separateContextInstancesDoNotShareState() {
        var other = new Context();

        context.put("requestId", "a1f3");
        other.put("requestId", "b7c2");

        assertEquals("a1f3", context.get("requestId"));
        assertEquals("b7c2", other.get("requestId"));
        assertNotEquals(context.get("requestId"), other.get("requestId"));
    }

    @Test
    public void valuesSurviveBetweenTasksSharingAPooledThread() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> context.put("requestId", "first")).get(TIMEOUT_MS, TimeUnit.MILLISECONDS);

            Callable<Object> readBack = () -> context.get("requestId");
            Object seenByNextTask = pool.submit(readBack).get(TIMEOUT_MS, TimeUnit.MILLISECONDS);

            // Characterises the pooled-thread hazard: the second task inherits
            // the first task's context because they share a thread. Whoever
            // owns a unit of work has to clear() at the end of it.
            assertEquals("first", seenByNextTask);
        } finally {
            pool.shutdownNow();
        }
    }
}
