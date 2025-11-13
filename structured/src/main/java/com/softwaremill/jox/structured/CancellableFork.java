package com.softwaremill.jox.structured;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

public interface CancellableFork<T> extends Fork<T> {
    /**
     * Interrupts the fork, and blocks until it completes with a result.
     *
     * @throws ExecutionException When the cancelled fork threw an exception.
     */
    T cancel() throws InterruptedException, ExecutionException;

    /**
     * Interrupts the fork, and returns immediately, without waiting for the fork to complete. Note
     * that the enclosing scope will only complete once all forks have completed.
     */
    void cancelNow();
}

final class CancellableForkUsingResult<T> extends ForkUsingResult<T> implements CancellableFork<T> {
    /** interrupt signal */
    final Semaphore done = new Semaphore(0);

    @Override
    public T cancel() throws InterruptedException, ExecutionException {
        cancelNow();
        return join(); // ForkUsingResult#join throws InterruptedException,ExecutionException
    }

    @Override
    public void cancelNow() {
        // will cause the scope to end, interrupting the task if it hasn't yet finished (or
        // potentially never starting it)
        done.release();
        // ~ cancel(true);// Future#cancel: CompletableFuture guarantees the invariant
        completeExceptionally(new InterruptedException("fork was cancelled before it started"));
    }
}
