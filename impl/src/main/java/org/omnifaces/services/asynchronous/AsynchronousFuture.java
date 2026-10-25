/*
 * Copyright 2021 OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.services.asynchronous;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The future an asynchronous method returns to its caller, with the cancellation semantics of an EJB asynchronous
 * method: an invocation can only be cancelled before it's started. Cancelling a started invocation with
 * {@code mayInterruptIfRunning} doesn't interrupt it, but makes {@link AsynchronousInvocation#wasCancelCalled()} return
 * true in it.
 * <p>
 * It's completed when the invocation has completed, including the interceptors of the method, such as those that end a
 * transaction or release a lock or a pooled instance.
 */
final class AsynchronousFuture implements Future<Object> {

    private static final int NEW = 0;
    private static final int STARTED = 1;
    private static final int CANCELLED = 2;

    private final AtomicInteger state = new AtomicInteger(NEW);
    private final CompletableFuture<Object> result = new CompletableFuture<>();

    private volatile boolean cancelCalled;
    private volatile Future<?> task;

    void setTask(Future<?> task) {
        this.task = task;

        if (state.get() == CANCELLED) {
            task.cancel(false);
        }
    }

    /**
     * Starts the invocation, unless it was cancelled.
     *
     * @return whether the invocation must run
     */
    boolean start() {
        return state.compareAndSet(NEW, STARTED);
    }

    /**
     * Runs the given action when this future is done: completed, failed or cancelled.
     */
    void whenDone(Runnable action) {
        result.whenComplete((value, throwable) -> action.run());
    }

    void complete(Object value) {
        result.complete(value);
    }

    void fail(Throwable throwable) {
        result.completeExceptionally(throwable);
    }

    boolean wasCancelCalled() {
        return cancelCalled;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (state.compareAndSet(NEW, CANCELLED)) {
            result.cancel(false);

            Future<?> currentTask = task;
            if (currentTask != null) {
                currentTask.cancel(false);
            }

            return true;
        }

        if (mayInterruptIfRunning && state.get() == STARTED && !result.isDone()) {
            cancelCalled = true;
        }

        return false;
    }

    @Override
    public boolean isCancelled() {
        return state.get() == CANCELLED;
    }

    @Override
    public boolean isDone() {
        return result.isDone();
    }

    @Override
    public Object get() throws InterruptedException, ExecutionException {
        return result.get();
    }

    @Override
    public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return result.get(timeout, unit);
    }
}
