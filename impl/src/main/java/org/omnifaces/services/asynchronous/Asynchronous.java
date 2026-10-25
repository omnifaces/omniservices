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

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.interceptor.InterceptorBinding;

/**
 * Specifies that a method, or all methods of a class, run asynchronously, like an EJB asynchronous method.
 * <p>
 * The method must return {@code void} or {@link java.util.concurrent.Future}. The invocation is dispatched to another
 * thread (see {@link ExecutorBean}) before the other OmniServices interceptors and those of {@code @Transactional}
 * run, so the caller never waits for a pooled instance or a lock, and the method's transaction and locks are those of
 * the thread it runs on. It doesn't run in the caller's transaction or request context; it gets a request context of its
 * own.
 * <ul>
 * <li>A {@code Future} the method returns is resolved on that thread, before its transaction ends and its lock and
 * pooled instance are released. The future the caller gets completes after that, with the same value or exception.
 * <li>The caller's future can only be cancelled before the invocation started. Cancelling it later with
 * {@code mayInterruptIfRunning} doesn't interrupt the method, but makes {@link AsynchronousInvocation#wasCancelCalled()}
 * return true in it.
 * <li>An exception thrown by a {@code void} method is logged.
 * <li>When the executor service rejects the invocation, the caller gets its
 * {@link java.util.concurrent.RejectedExecutionException}. Nothing was acquired for the invocation at that point.
 * </ul>
 * Interceptors with a priority before {@code Interceptor.Priority.PLATFORM_BEFORE + 1} run on the caller's thread.
 */
@InterceptorBinding
@Target({ METHOD, TYPE })
@Retention(RUNTIME)
@Inherited
public @interface Asynchronous {
    
    /**
     * Supports inline instantiation of the {@link Asynchronous} annotation.
     *
     */
    public static final class Literal extends AnnotationLiteral<Asynchronous> implements Asynchronous {
        private static final long serialVersionUID = 1L;

        /**
         * Instance of the {@link Asynchronous} annotation.
         */
        public static final Literal INSTANCE = new Literal();

        /**
         * Creates an instance of the {@link Asynchronous} annotation. {@link #INSTANCE} can be used instead.
         */
        public Literal() {
        }
    }
    
}
