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

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * Records the thread, target and transaction state of an invocation after the pooled scope interceptor.
 */
@Interceptor
@Probed
@Priority(PLATFORM_BEFORE + 60)
public class AfterPoolProbe {

    @Inject
    Trace trace;

    @Inject
    TransactionSynchronizationRegistry transactionSynchronizationRegistry;

    @AroundInvoke
    public Object probe(InvocationContext ctx) throws Exception {
        trace.add("after-pool " + ctx.getMethod().getName() + " thread=" + Trace.thread() + " target=" + Trace.id(ctx.getTarget())
            + " tx=" + (transactionSynchronizationRegistry.getTransactionKey() != null));
        return ctx.proceed();
    }
}
