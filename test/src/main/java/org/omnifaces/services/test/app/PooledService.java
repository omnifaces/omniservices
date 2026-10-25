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

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import org.omnifaces.services.asynchronous.Asynchronous;
import org.omnifaces.services.asynchronous.AsynchronousInvocation;
import org.omnifaces.services.lock.Lock;
import org.omnifaces.services.pooled.Pooled;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.transaction.Synchronization;
import jakarta.transaction.Transactional;
import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * A pool of one instance, locked, transactional, probed.
 */
@Pooled(maxNumberOfInstances = 1, instanceLockTimeout = 30, instanceLockTimeoutUnit = SECONDS)
@Lock
@Transactional
@Probed
public class PooledService {

    @Inject
    Trace trace;

    @Inject
    QueueFiller queueFiller;

    @Inject
    SinglePoolBean singlePoolBean;

    @Inject
    TransactionSynchronizationRegistry transactionSynchronizationRegistry;

    @PostConstruct
    void created() {
        trace.add("created PooledService " + Trace.id(this) + " thread=" + Trace.thread());
    }

    @PreDestroy
    void destroyed() {
        Trace.record("destroyed PooledService " + Trace.id(this) + " thread=" + Trace.thread());
    }

    public String hold(String latch) {
        trace.countDown(latch + "-holding");
        trace.await(latch, 10_000);
        return "held";
    }

    public String ping() {
        trace.add("ping thread=" + Trace.thread() + " target=" + Trace.id(this));
        return "pong";
    }

    @Asynchronous
    public Future<String> work() {
        trace.add("method work thread=" + Trace.thread() + " target=" + Trace.id(this)
            + " tx=" + (transactionSynchronizationRegistry.getTransactionKey() != null));
        return CompletableFuture.completedFuture("done@" + Trace.thread());
    }

    @Asynchronous
    public Future<String> incomplete() {
        transactionSynchronizationRegistry.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
                trace.add("tx-before-completion");
            }

            @Override
            public void afterCompletion(int status) {
                trace.add("tx-after-completion");
            }
        });

        // Completed when the caller has checked that its future isn't done yet
        CompletableFuture<String> result = new CompletableFuture<>();
        Thread completer = new Thread(() -> {
            trace.await("complete-inner", 10_000);
            trace.add("inner-complete");
            result.complete("late");
        });
        completer.start();

        trace.add("method-return");
        return result;
    }

    /**
     * Interrupts its thread and returns a future that never completes, so waiting for its result is interrupted. Records
     * how its transaction completes.
     */
    @Asynchronous
    public Future<String> interrupted() {
        transactionSynchronizationRegistry.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
            }

            @Override
            public void afterCompletion(int status) {
                trace.add("interrupted tx status=" + status);
            }
        });

        Thread.currentThread().interrupt();
        return new CompletableFuture<>();
    }

    /**
     * Runs until a while after the application started stopping: past OmniServices' shutdown time limit in the test.
     * Meanwhile, it checks that new asynchronous invocations are refused as soon as the application stops, and pooled
     * instances once the application context was destroyed.
     */
    @Asynchronous
    public Future<String> outstanding() {
        Trace.record("outstanding started " + Trace.id(this));
        ShutdownObserver.awaitShutdown(60_000);

        // OmniServices' observer runs right after the one that sets the flag
        ShutdownObserver.sleep(500);
        ShutdownObserver.call("submission", queueFiller::target, RejectedExecutionException.class);
        ShutdownObserver.call("acquisition", singlePoolBean::ping, IllegalStateException.class);

        // Still running when the pools are closed
        ShutdownObserver.awaitDestroyed(60_000);
        ShutdownObserver.sleep(3000);
        Trace.record("outstanding finished " + Trace.id(this));

        return CompletableFuture.completedFuture("finished");
    }

    @Asynchronous
    public Future<String> cancellable() {
        trace.countDown("entered");

        boolean cancelCalled = false;
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (AsynchronousInvocation.wasCancelCalled()) {
                cancelCalled = true;
                break;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                trace.add("interrupted");
                break;
            }
        }
        trace.add("saw-cancel=" + cancelCalled);

        trace.await("finish", 10_000);
        trace.add("method-exit");

        return CompletableFuture.completedFuture("cancel-called=" + cancelCalled);
    }
}
