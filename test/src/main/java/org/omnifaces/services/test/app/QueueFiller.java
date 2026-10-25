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
package org.omnifaces.services.test.app;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.omnifaces.services.asynchronous.Asynchronous;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Asynchronous methods to fill the executor with, and to queue behind them.
 */
@ApplicationScoped
public class QueueFiller {

    @Inject
    Trace trace;

    @Asynchronous
    public Future<String> block() {
        trace.countDown("blockers-entered");
        trace.await("blockers", 10_000);
        return CompletableFuture.completedFuture("unblocked");
    }

    @Asynchronous
    public Future<String> target() {
        trace.add("target ran");
        return CompletableFuture.completedFuture("target");
    }

    /** The number of busyUntilShutdown invocations that started */
    public static final AtomicInteger busy = new AtomicInteger();

    /**
     * Keeps a thread of the executor busy until the application stops, and a while after.
     */
    @Asynchronous
    public Future<String> busyUntilShutdown() {
        busy.incrementAndGet();
        ShutdownObserver.awaitShutdown(60_000);
        ShutdownObserver.sleep(3000);
        return CompletableFuture.completedFuture("done");
    }

    /**
     * Records that it ran: it mustn't, since it's still queued when the application stops.
     */
    @Asynchronous
    public Future<String> queued() {
        Trace.record("queued ran");
        return CompletableFuture.completedFuture("queued");
    }

    @Asynchronous
    public Future<String> sentinel() {
        trace.add("sentinel ran");
        return CompletableFuture.completedFuture("sentinel");
    }
}
