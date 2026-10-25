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

import org.omnifaces.services.asynchronous.Asynchronous;
import org.omnifaces.services.lock.Lock;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * A singleton with a write lock, transactional, probed.
 */
@ApplicationScoped
@Lock
@Transactional
@Probed
public class LockedSingleton {

    @Inject
    Trace trace;

    @Inject
    TransactionSynchronizationRegistry transactionSynchronizationRegistry;

    public String hold(String latch) {
        trace.countDown(latch + "-holding");
        trace.await(latch, 10_000);
        return "held";
    }

    @Asynchronous
    public Future<String> work() {
        trace.add("method work thread=" + Trace.thread() + " target=" + Trace.id(this)
            + " key=" + Trace.key(transactionSynchronizationRegistry.getTransactionKey())
            + " tx=" + (transactionSynchronizationRegistry.getTransactionKey() != null));
        return CompletableFuture.completedFuture("done@" + Trace.thread());
    }
}
