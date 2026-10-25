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

import java.util.concurrent.ExecutorService;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.context.ThreadContext;

/**
 * Creates a MicroProfile {@link ManagedExecutor} for asynchronous methods. Only loaded when the MicroProfile Context
 * Propagation API is available, so that OmniServices doesn't need it.
 */
final class MicroProfileExecutor {

    static final String API_CLASS = "org.eclipse.microprofile.context.ManagedExecutor";

    private MicroProfileExecutor() {
    }

    /**
     * Creates a managed executor that propagates the application context (such as the context class loader), if the
     * implementation supports it, and clears all other contexts. Like an EJB asynchronous method, an asynchronous method
     * doesn't run in the caller's transaction or request context; the asynchronous interceptor activates a request
     * context of its own.
     * <p>
     * Its maximum concurrency and queue size are the implementation's defaults, which can be configured with the
     * MicroProfile Config properties {@code mp.context.ManagedExecutor.maxAsync} and
     * {@code mp.context.ManagedExecutor.maxQueued}.
     *
     * @return the managed executor
     * @throws IllegalStateException when there's no implementation
     */
    static ExecutorService create() {
        try {
            return ManagedExecutor.builder()
                    .propagated(ThreadContext.APPLICATION)
                    .cleared(ThreadContext.ALL_REMAINING)
                    .build();
        } catch (IllegalStateException e) {
            // E.g. Quarkus has no provider for the application context, but its threads have the application's class
            // loader. If there's no implementation at all, this throws again.
            return ManagedExecutor.builder()
                    .propagated(ThreadContext.NONE)
                    .cleared(ThreadContext.ALL_REMAINING)
                    .build();
        }
    }
}
