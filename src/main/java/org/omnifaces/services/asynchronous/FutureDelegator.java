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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.ejb.AsyncResult;

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