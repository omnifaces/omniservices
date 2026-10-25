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

/**
 * Gives access to the asynchronous invocation the current thread runs, i.e. an invocation of an {@link Asynchronous}
 * method.
 */
public final class AsynchronousInvocation {

    private static final ThreadLocal<AsynchronousFuture> CURRENT = new ThreadLocal<>();

    private AsynchronousInvocation() {
    }

    /**
     * Returns whether the caller of the current asynchronous invocation cancelled it with
     * {@code mayInterruptIfRunning} set to true, after it was started.
     *
     * @return whether cancel was called
     * @throws IllegalStateException when the current thread doesn't run an asynchronous invocation that returns a
     *         future
     */
    public static boolean wasCancelCalled() {
        AsynchronousFuture future = CURRENT.get();
        if (future == null) {
            throw new IllegalStateException("Not in an asynchronous invocation that returns a Future");
        }

        return future.wasCancelCalled();
    }

    static AsynchronousFuture setCurrent(AsynchronousFuture future) {
        AsynchronousFuture previous = CURRENT.get();

        if (future == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(future);
        }

        return previous;
    }
}
