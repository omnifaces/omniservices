package org.omnifaces.services.asynchronous;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.ejb.AsyncResult;

public class FutureDelegator implements Future<Object> {

    private final Future<?> future;

    public FutureDelegator(Future<?> future) {
        this.future = future;
    }

    @Override
    public Object get() throws InterruptedException, ExecutionException {
        AsyncResult<?> asyncResult = (AsyncResult<?>) future.get();
        if (asyncResult == null) {
            return null;
        }

        return asyncResult.get();
    }

    @Override
    public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        AsyncResult<?> asyncResult = (AsyncResult<?>) future.get(timeout, unit);
        if (asyncResult == null) {
            return null;
        }

        return asyncResult.get();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return future.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return future.isCancelled();
    }

    @Override
    public boolean isDone() {
        return future.isDone();
    }
}