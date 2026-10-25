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
package org.omnifaces.services.internal;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;

import jakarta.enterprise.inject.spi.Bean;

/**
 * A one-shot token for the re-invocation of an asynchronous method of a pooled bean.
 * <p>
 * The asynchronous interceptor dispatches the invocation on the bean's dispatch instance to another thread. There, the
 * pooled scope interceptor takes an instance from the pool, and invokes the method again, on that instance. The
 * asynchronous interceptor of that invocation must not dispatch it again. So the pooled scope interceptor begins a
 * reinvocation right before it invokes the method, the asynchronous interceptor of that invocation consumes it before
 * it proceeds, and the pooled scope interceptor ends it afterwards, in case nothing consumed it.
 * <p>
 * The tokens of a thread are kept in a stack, so nested invocations don't mix them up. A token only matches the bean
 * and method it was created for, and it's consumed once, so any other asynchronous invocation, including a nested one
 * of the same method of the same bean, is still dispatched.
 * <p>
 * Internal: not part of the API.
 */
public final class Reinvocation {

    private static final ThreadLocal<Deque<Reinvocation>> REINVOCATIONS = new ThreadLocal<>();

    private final Bean<?> bean;
    private final Method method;

    private Reinvocation(Bean<?> bean, Method method) {
        this.bean = bean;
        this.method = method;
    }

    /**
     * Begins the reinvocation of the given method of the given bean, on the current thread.
     *
     * @param bean the bean that is invoked again
     * @param method the method that is invoked again
     * @return the reinvocation, which must be ended
     */
    public static Reinvocation begin(Bean<?> bean, Method method) {
        Deque<Reinvocation> reinvocations = REINVOCATIONS.get();
        if (reinvocations == null) {
            reinvocations = new ArrayDeque<>();
            REINVOCATIONS.set(reinvocations);
        }

        Reinvocation reinvocation = new Reinvocation(bean, method);
        reinvocations.push(reinvocation);

        return reinvocation;
    }

    /**
     * Consumes the innermost reinvocation of the current thread, if it's one of the given method of the given bean.
     *
     * @param bean the invoked bean
     * @param method the invoked method
     * @return whether the invocation is a reinvocation
     */
    public static boolean consume(Bean<?> bean, Method method) {
        Deque<Reinvocation> reinvocations = REINVOCATIONS.get();
        if (reinvocations == null || reinvocations.isEmpty()) {
            return false;
        }

        Reinvocation reinvocation = reinvocations.peek();
        if (!reinvocation.bean.equals(bean) || !reinvocation.method.equals(method)) {
            return false;
        }

        reinvocation.end();

        return true;
    }

    /**
     * Ends this reinvocation, if it wasn't consumed.
     */
    public void end() {
        Deque<Reinvocation> reinvocations = REINVOCATIONS.get();
        if (reinvocations != null) {
            reinvocations.removeFirstOccurrence(this);

            if (reinvocations.isEmpty()) {
                REINVOCATIONS.remove();
            }
        }
    }
}
