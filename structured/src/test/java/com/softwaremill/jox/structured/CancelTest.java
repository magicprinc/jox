package com.softwaremill.jox.structured;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.Test;

public class CancelTest {
    @Test
    void testCancelBlocksUntilForkCompletes() throws Exception {
        var trail = new Trail();
        Scopes.supervised(
                scope -> {
                    var f =
                            scope.forkCancellable(
                                    () -> {
                                        trail.add("started");
                                        try {
                                            Thread.sleep(500);
                                            trail.add("main done");
                                        } catch (InterruptedException | CancellationException e) {
                                            trail.add("interrupted");
                                            Thread.sleep(500);
                                            trail.add("interrupted done");
                                            throw e;
                                        }
                                        return null;
                                    });

                    Thread.sleep(100); // making sure the fork starts
                    try {
                        f.cancel();
                    } catch (ExecutionException e) {
                        // ignore
                    }
                    trail.add("cancel done");
                    Thread.sleep(1000);
                    return null;
                });

        System.out.println(trail.get()); // [started, cancel done, interrupted, interrupted done]
        assertIterableEquals(
                Arrays.asList("started", "cancel done", "interrupted", "interrupted done"),
                trail.get());
    }

    @Test
    void testCancelBlocksUntilForkCompletesStressTest1() throws Exception {
        for (int i = 1; i <= 20; i++) {
            var trail = new Trail();
            var s = new Semaphore(0);
            var x =
                    Scopes.supervised(
                            scope -> {
                                var f =
                                        scope.forkCancellable(
                                                () -> {
                                                    try {
                                                        s.acquire();
                                                        trail.add("main done");
                                                    } catch (InterruptedException e) {
                                                        trail.add("interrupted");
                                                        Thread.sleep(100);
                                                        trail.add("interrupted done");
                                                    }
                                                    return null;
                                                });

                                Thread.sleep(
                                        555); // interleave immediate cancels and after the fork
                                // starts
                                // (probably)
                                try {
                                    f.cancel();
                                } catch (ExecutionException e) {
                                    // ignore
                                }
                                s.release(1); // the acquire should be interrupted
                                trail.add("cancel done");
                                Thread.sleep(100);
                                assertTrue(
                                        ((CancellableForkUsingResult<Object>) f)
                                                .isCompletedExceptionally());
                                return null;
                            });

            System.out.println(trail.get()); // [cancel done, main done]
            assertIterableEquals(Arrays.asList("cancel done", "main done"), trail.get());
            assertNull(x);
        }
    }

    @Test
    void testCancelBlocksUntilForkCompletesStressTest2() throws Exception {
        for (int i = 1; i <= 20; i++) {
            var trail = new Trail();
            var s = new Semaphore(0);
            Scopes.supervised(
                    scope -> {
                        var f =
                                scope.forkCancellable(
                                        () -> {
                                            try {
                                                s.acquire();
                                                trail.add("main done");
                                            } catch (InterruptedException e) {
                                                trail.add("interrupted");
                                                Thread.sleep(100);
                                                trail.add("interrupted done");
                                            }
                                            return null;
                                        });

                        // (probably)
                        try {
                            f.cancel();
                        } catch (ExecutionException e) {
                            // ignore
                        }
                        s.release(1); // the acquire should be interrupted
                        trail.add("cancel done");
                        Thread.sleep(100);
                        assertTrue(
                                ((CancellableForkUsingResult<Object>) f)
                                        .isCompletedExceptionally());
                        return null;
                    });

            System.out.println(trail.get()); // [cancel done, main done]
            assertIterableEquals(
                    List.of("cancel done"), trail.get()); // the fork wasn't even started
            assertEquals(1, trail.get().size());
        }
    }

    @Test
    void testCancelNowReturnsImmediatelyAndWaitForForksWhenScopeCompletes() throws Exception {
        Trail trail = new Trail();
        Scopes.supervised(
                scope -> {
                    var f =
                            scope.forkCancellable(
                                    () -> {
                                        try {
                                            Thread.sleep(500);
                                            trail.add("main done");
                                        } catch (InterruptedException e) {
                                            Thread.sleep(500);
                                            trail.add("interrupted done");
                                        }
                                        return null;
                                    });

                    Thread.sleep(100); // making sure the fork starts
                    f.cancelNow();
                    trail.add("cancel done");
                    assertIterableEquals(List.of("cancel done"), trail.get());
                    return null;
                });
        assertIterableEquals(Arrays.asList("cancel done", "interrupted done"), trail.get());
    }

    @Test
    void testCancelNowFollowedByJoinEitherCatchesInterruptedExceptionWithWhichForkEnds() {
        assertThrows(
                JoxScopeExecutionException.class,
                () ->
                        Scopes.supervised(
                                scope -> {
                                    var f =
                                            scope.forkCancellable(
                                                    () -> {
                                                        Thread.sleep(200);
                                                        return null;
                                                    });
                                    Thread.sleep(100);
                                    f.cancelNow();
                                    return f.join();
                                }));
    }
}
