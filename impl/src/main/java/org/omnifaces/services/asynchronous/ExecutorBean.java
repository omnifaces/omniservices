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

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import jakarta.inject.Singleton;

/**
 * Provides the executor service that {@link Asynchronous} methods run on. It's created on first use, as the first of:
 * <ol>
 * <li>the executor service in JNDI at {@code java:comp/env/concurrent/ThreadPool};
 * <li>a MicroProfile {@code ManagedExecutor}, when the MicroProfile Context Propagation API and an implementation are
 * available (such as in Quarkus with {@code quarkus-smallrye-context-propagation}). It propagates the application
 * context, when the implementation has it, and clears all other contexts, such as the transaction and the CDI
 * contexts;
 * <li>the Jakarta Concurrency default managed executor service, in JNDI at
 * {@code java:comp/DefaultManagedExecutorService}, which doesn't propagate transactions;
 * <li>a thread pool of its own, with as many threads as there are processors.
 * </ol>
 * An executor service that OmniServices created (the managed executor or the thread pool) is shut down when the
 * application stops. Executor services from JNDI belong to the container.
 * <p>
 * When the application stops, no new invocations are accepted, the invocations that haven't started are cancelled
 * (also on executor services of the container), and those that have are waited for, for a limited time.
 * <p>
 * It's a {@link Singleton}, so that it outlives the application context: an asynchronous method called after that was
 * destroyed is refused too, rather than given a new executor service.
 */
@Singleton
public class ExecutorBean {

    private static final Logger logger = Logger.getLogger(ExecutorBean.class.getName());

    private ExecutorService executorService;

    /** The invocations that aren't done yet */
    private final Set<AsynchronousFuture> invocations = ConcurrentHashMap.newKeySet();

    // Whether OmniServices created the executor service, and so must shut it down
    private boolean owned;

    private boolean shutDown;

    /**
     * Creates the bean. Used by CDI.
     */
    public ExecutorBean() {
    }

    /**
     * Returns the executor service, which is created on first use.
     *
     * @return the executor service
     * @throws RejectedExecutionException when the application is stopping
     */
    public synchronized ExecutorService getExecutorService() {
        if (executorService == null) {
            if (shutDown) {
                throw new RejectedExecutionException("OmniServices asynchronous methods are shut down");
            }

            executorService = createExecutorService();
        }

        return executorService;
    }

    /**
     * Submits an invocation of an asynchronous method.
     *
     * @param future the invocation's future
     * @param task the invocation
     * @throws RejectedExecutionException when the application is stopping, or the executor service rejects it
     */
    void submit(AsynchronousFuture future, Runnable task) {
        synchronized (this) {
            if (shutDown) {
                throw new RejectedExecutionException("OmniServices asynchronous methods are shut down");
            }

            invocations.add(future);
        }

        future.whenDone(() -> invocations.remove(future));

        try {
            future.setTask(getExecutorService().submit(task));
        } catch (RuntimeException e) {
            invocations.remove(future);
            throw e;
        }
    }

    /**
     * Stops accepting invocations, cancels those that haven't started, and waits until the others are done, at most
     * until the given deadline. An executor service OmniServices created is shut down; those of the container are left
     * to the container.
     *
     * @param deadline the deadline, as {@link System#nanoTime()}
     * @return whether all invocations are done
     */
    public boolean shutdown(long deadline) {
        ExecutorService executorServiceToShutdown;

        synchronized (this) {
            shutDown = true;
            executorServiceToShutdown = owned ? executorService : null;
        }

        // Invocations that haven't started are cancelled; cancel(false) doesn't affect the others
        for (AsynchronousFuture invocation : invocations) {
            invocation.cancel(false);
        }

        if (executorServiceToShutdown != null) {
            executorServiceToShutdown.shutdown();
        }

        try {
            while (!invocations.isEmpty()) {
                if (System.nanoTime() >= deadline) {
                    logger.log(WARNING, invocations.size() + " asynchronous methods still running when the application stopped");
                    return false;
                }

                Thread.sleep(10);
            }

            if (executorServiceToShutdown != null) {
                executorServiceToShutdown.awaitTermination(Math.max(0, deadline - System.nanoTime()), NANOSECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return invocations.isEmpty();
    }

    private ExecutorService createExecutorService() {
        ExecutorService threadPool = lookup("java:comp/env/concurrent/ThreadPool");
        if (threadPool != null) {
            logger.log(FINE, "Asynchronous methods use java:comp/env/concurrent/ThreadPool");
            return threadPool;
        }

        ExecutorService managedExecutor = createMicroProfileExecutor();
        if (managedExecutor != null) {
            logger.log(FINE, "Asynchronous methods use a MicroProfile ManagedExecutor");
            owned = true;
            return managedExecutor;
        }

        ExecutorService defaultManagedExecutorService = lookup("java:comp/DefaultManagedExecutorService");
        if (defaultManagedExecutorService != null) {
            logger.log(FINE, "Asynchronous methods use java:comp/DefaultManagedExecutorService");
            return defaultManagedExecutorService;
        }

        logger.log(FINE, "Asynchronous methods use a thread pool of their own");
        owned = true;

        return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new AsynchronousThreadFactory());
    }

    private static ExecutorService lookup(String name) {
        try {
            return new InitialContext().lookup(name) instanceof ExecutorService executorService ? executorService : null;
        } catch (NamingException e) {
            // Ignore
            return null;
        }
    }

    private static ExecutorService createMicroProfileExecutor() {
        try {
            Class.forName(MicroProfileExecutor.API_CLASS, false, ExecutorBean.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }

        try {
            return MicroProfileExecutor.create();
        } catch (IllegalStateException | LinkageError e) {
            // No implementation, or an incomplete API
            logger.log(FINE, "No MicroProfile ManagedExecutor available", e);
            return null;
        }
    }

    private static class AsynchronousThreadFactory implements ThreadFactory {

        private final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "omniservices-async-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);

            return thread;
        }
    }
}
