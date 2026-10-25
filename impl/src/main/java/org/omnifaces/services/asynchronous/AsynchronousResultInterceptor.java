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

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.omnifaces.services.pooled.UncheckedInterruptedException;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

/**
 * Waits for the result of an {@link Asynchronous} method that returns a {@link Future}, on the thread the method was
 * dispatched to.
 * <p>
 * Its priority puts it after the interceptors that start a transaction (such as those of {@code @Transactional}),
 * acquire a lock or a pooled instance, so the result is resolved before the transaction ends and the lock and the
 * instance are released, as an EJB container does. The future returned to the caller of the method only completes
 * after that.
 */
@Interceptor
@Asynchronous
@Priority(PLATFORM_BEFORE + 300)
public class AsynchronousResultInterceptor implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the interceptor. Used by CDI.
     */
    public AsynchronousResultInterceptor() {
    }

    /**
     * Invokes the method, and waits for the future it returns, if any.
     *
     * @param ctx the invocation context
     * @return a completed future with the result of the future the method returned, else what the method returned
     * @throws Exception what the method threw, or the cause of the failure of the future it returned
     * @throws UncheckedInterruptedException when waiting for the future is interrupted, so a transaction rolls back
     */
    @AroundInvoke
    public Object resolveResult(InvocationContext ctx) throws Exception {
        Object result = ctx.proceed();

        if (!(result instanceof Future<?> future)) {
            return result;
        }

        try {
            return CompletableFuture.completedFuture(future.get());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();

            if (cause instanceof Exception exception) {
                throw exception;
            }

            if (cause instanceof Error error) {
                throw error;
            }

            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException("Interrupted while waiting for the result of " + ctx.getMethod(), e);
        }
    }
}
