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

import static jakarta.interceptor.Interceptor.Priority.APPLICATION;

import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.Destroyed;
import jakarta.enterprise.event.Observes;

@Dependent
public class ShutdownObserver {

    /** Whether the application is stopping; set before OmniServices stops (this observer's priority is lower) */
    public static volatile boolean shuttingDown;

    /** Whether the application context was destroyed; set after OmniServices closed its pools (this observer's priority is higher) */
    public static volatile boolean destroyed;

    public void beforeDestroyed(@Observes @Priority(1) @BeforeDestroyed(ApplicationScoped.class) Object event) {
        shuttingDown = true;
        Trace.record("before-destroyed application scope");
    }

    /**
     * Also checks that, after OmniServices' observer closed the pools, asynchronous methods and pooled beans are
     * refused; while CDI still runs. On Quarkus, the application context was destroyed by now: a new QueueFiller is
     * created for the call.
     */
    public void destroyed(@Observes @Priority(APPLICATION + 1000) @Destroyed(ApplicationScoped.class) Object event, QueueFiller queueFiller, SinglePoolBean singlePoolBean) {
        Trace.record("destroyed application scope");
        call("late submission", queueFiller::target, RejectedExecutionException.class);
        call("late acquisition", singlePoolBean::ping, IllegalStateException.class);
        destroyed = true;
    }

    /**
     * Calls a method, and records whether it was accepted, rejected (with the given exception), or failed otherwise.
     */
    public static void call(String event, Runnable call, Class<? extends RuntimeException> rejection) {
        try {
            call.run();
            Trace.record(event + " accepted");
        } catch (RuntimeException e) {
            Trace.record(event + (rejection.isInstance(e) ? " rejected" : " failed: " + e));
        }
    }

    /**
     * Waits until the application stops, at most the given time.
     */
    public static void awaitShutdown(long millis) {
        await(() -> shuttingDown, millis);
    }

    /**
     * Waits until the application context was destroyed, at most the given time.
     */
    public static void awaitDestroyed(long millis) {
        await(() -> destroyed, millis);
    }

    private static void await(BooleanSupplier condition, long millis) {
        long deadline = System.currentTimeMillis() + millis;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            sleep(10);
        }
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
