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
import static java.util.logging.Level.SEVERE;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import org.omnifaces.services.internal.Reinvocation;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.inject.Intercepted;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

/**
 * Dispatches the invocation of an {@link Asynchronous} method to another thread, and returns a future for its result
 * to the caller (or nothing, for a void method).
 * <p>
 * Its priority puts it before the other OmniServices interceptors, so that the caller never waits for a pooled
 * instance or a lock, and the method's transaction is started on the thread the method runs on. The method's result is
 * resolved on that thread too, see {@link AsynchronousResultInterceptor}.
 *
 * @serial exclude
 */
@Interceptor
@Asynchronous
@Priority(PLATFORM_BEFORE + 1)
public class AsynchronousInterceptor implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = Logger.getLogger(AsynchronousInterceptor.class.getName());

    @Inject
    private ExecutorBean executorBean;

    @Inject
    private Provider<RequestContextController> requestContextControllerProvider;

    @Inject
    @Intercepted
    private Bean<?> interceptedBean;

    /**
     * Creates the interceptor. Used by CDI.
     */
    public AsynchronousInterceptor() {
    }

    /**
     * Dispatches the invocation to another thread, unless it was already dispatched to this thread.
     *
     * @param ctx the invocation context
     * @return a future for the result, or null for a void method
     * @throws Exception when the invocation is invoked again on a pooled instance, and that fails
     */
    @AroundInvoke
    public Object submitAsync(InvocationContext ctx) throws Exception {
        Method method = ctx.getMethod();

        if (Reinvocation.consume(interceptedBean, method)) {
            // The invocation was already dispatched to this thread, and is now invoked again on a pooled instance
            return ctx.proceed();
        }

        boolean isVoid = method.getReturnType() == void.class;
        if (!isVoid && !method.getReturnType().isAssignableFrom(AsynchronousFuture.class)) {
            throw new IllegalStateException("An asynchronous method must return void or Future, " + method + " doesn't.");
        }

        AsynchronousFuture future = new AsynchronousFuture();
        executorBean.submit(future, () -> invoke(ctx, future, isVoid));

        return isVoid ? null : future;
    }

    private void invoke(InvocationContext ctx, AsynchronousFuture future, boolean isVoid) {
        if (!future.start()) {
            // Cancelled before it started
            return;
        }

        AsynchronousFuture previous = AsynchronousInvocation.setCurrent(isVoid ? null : future);
        RequestContextController requestContextController = requestContextControllerProvider.get();
        boolean activated = requestContextController.activate();

        try {
            future.complete(getValue(ctx.proceed()));
        } catch (Throwable throwable) {
            future.fail(throwable);

            if (isVoid) {
                logger.log(SEVERE, "Asynchronous method " + ctx.getMethod() + " failed", throwable);
            }
        } finally {
            if (activated) {
                requestContextController.deactivate();
            }

            AsynchronousInvocation.setCurrent(previous);
        }
    }

    private static Object getValue(Object result) throws Throwable {
        if (!(result instanceof Future<?> future)) {
            return result;
        }

        // Normally resolved already, by the AsynchronousResultInterceptor
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }
}
