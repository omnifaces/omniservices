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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.omnifaces.services.asynchronous.ExecutorBean;
import org.omnifaces.services.pooled.PoolLockTimeoutException;
import org.omnifaces.services.pooled.Pooled;
import org.omnifaces.services.pooled.UncheckedInterruptedException;

import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Status;

/**
 * Runs one probe, and responds with PASS or FAIL and the trace.
 */
@WebServlet("/probe")
public class ProbeServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    // The maxAsync of the MicroProfile ManagedExecutor, see META-INF/microprofile-config.properties
    private static final int MANAGED_MAX_ASYNC = 4;
    private static final int MANAGED_MAX_QUEUED = 4;

    @Inject
    Trace trace;

    @Inject
    PooledService pooledService;

    @Inject
    LockedSingleton lockedSingleton;

    @Inject
    NestedService nestedService;

    @Inject
    QueueFiller queueFiller;

    @Inject
    ExecutorBean executorBean;

    @Inject
    ServiceBean serviceBean;

    @Inject
    SinglePoolBean singlePoolBean;

    @Inject
    FireAndForget fireAndForget;

    @Inject
    TxCaller txCaller;

    @Inject
    PreDestroyCaller preDestroyCaller;

    @Inject
    LoopbackSingleton loopbackSingleton;

    @Inject
    BeanManager beanManager;

    @Inject
    Instance<RequestContextController> requestContextControllers;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String name = request.getParameter("name");
        List<String> failures = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        trace.clear();

        try {
            switch (name) {
                case "busyPool" -> submitWhileHeld("pool", () -> pooledService.hold("pool"), () -> pooledService.work(), failures, notes);
                case "heldLock" -> submitWhileHeld("lock", () -> lockedSingleton.hold("lock"), () -> lockedSingleton.work(), failures, notes);
                case "pooledTrace" -> pooledTrace(failures, notes);
                case "singletonTrace" -> singletonTrace(failures, notes);
                case "nested" -> nested(failures, notes);
                case "incomplete" -> incomplete(failures, notes);
                case "cancelQueued" -> cancelQueued(failures, notes);
                case "cancelRunning" -> cancelRunning(failures, notes);
                case "stereotype" -> stereotype(failures, notes);
                case "stereotypeSettings" -> stereotypeSettings(failures, notes);
                case "voidMethods" -> voidMethods(failures, notes);
                case "executor" -> executor(failures, notes);
                case "callerTransaction" -> callerTransaction(failures, notes);
                case "rejection" -> rejection(failures, notes);
                case "interruptedResult" -> interruptedResult(failures, notes);
                case "loopback" -> loopback(failures, notes);
                case "outstandingWork" -> outstandingWork(failures, notes);
                default -> failures.add("unknown probe " + name);
            }
        } catch (Throwable throwable) {
            failures.add("exception " + throwable);
            for (StackTraceElement element : throwable.getStackTrace()) {
                notes.add("  at " + element);
            }
        }

        response.setContentType("text/plain");
        StringBuilder body = new StringBuilder(failures.isEmpty() ? "PASS" : "FAIL " + failures).append('\n');
        notes.forEach(note -> body.append("note: ").append(note).append('\n'));
        trace.events().forEach(event -> body.append("event: ").append(event).append('\n'));
        response.getWriter().write(body.toString());
    }

    /**
     * an asynchronous call returns at once while the resource it needs is held by another thread.
     */
    private void submitWhileHeld(String latch, Runnable holder, Supplier<Future<String>> asyncCall, List<String> failures, List<String> notes) throws Exception {
        trace.newLatch(latch, 1);
        trace.newLatch(latch + "-holding", 1);

        Thread holderThread = newThread(holder, "holder");
        holderThread.start();
        if (!trace.await(latch + "-holding", 10_000)) {
            failures.add("holder didn't start");
            return;
        }

        long start = System.nanoTime();
        Future<String> future = asyncCall.get();
        long submitMillis = (System.nanoTime() - start) / 1_000_000;
        notes.add("submission took " + submitMillis + " ms");

        // Waiting for the resource would take until the holder gives up, 10 seconds
        if (submitMillis > 5000) {
            failures.add("submission waited " + submitMillis + " ms");
        }

        Thread.sleep(300);
        if (future.isDone()) {
            failures.add("completed while the resource was held");
        }

        trace.countDown(latch);
        notes.add("result " + future.get(30, SECONDS));
        holderThread.join(10_000);
    }

    /**
     * the interceptors and the method of an asynchronous call on a pooled bean run on the worker, in order,
     * on a pooled instance, and the method runs once.
     */
    private void pooledTrace(List<String> failures, List<String> notes) throws Exception {
        notes.add("result " + pooledService.work().get(30, SECONDS));

        List<String> events = withoutCreated(trace.events());
        String[] expected = { "before-pool work", "before-pool work", "after-pool work", "after-lock work", "in-tx work", "application work", "method work" };
        checkOrder(events, expected, failures);

        if (failures.isEmpty()) {
            String worker = field(events.get(0), "thread");
            String dispatchInstance = field(events.get(0), "target");
            String pooledInstance = field(events.get(1), "target");

            if (worker.equals(Trace.thread())) {
                failures.add("ran on the caller's thread");
            }
            for (String event : events) {
                if (!field(event, "thread").equals(worker)) {
                    failures.add("not all on one worker thread: " + event);
                }
            }
            if (dispatchInstance.equals(pooledInstance)) {
                failures.add("the method didn't move to a pooled instance");
            }
            for (int i = 1; i < events.size(); i++) {
                if (!field(events.get(i), "target").equals(pooledInstance)) {
                    failures.add("not on the pooled instance: " + events.get(i));
                }
            }
            checkTransaction(events, failures);
        }
    }

    /**
     * For a singleton: no pool, so one chain.
     */
    private void singletonTrace(List<String> failures, List<String> notes) throws Exception {
        notes.add("result " + lockedSingleton.work().get(30, SECONDS));

        List<String> events = withoutCreated(trace.events());
        String[] expected = { "before-pool work", "after-pool work", "after-lock work", "in-tx work", "application work", "method work" };
        checkOrder(events, expected, failures);

        if (failures.isEmpty()) {
            String worker = field(events.get(0), "thread");
            if (worker.equals(Trace.thread())) {
                failures.add("ran on the caller's thread");
            }
            for (String event : events) {
                if (!field(event, "thread").equals(worker)) {
                    failures.add("not all on one worker thread: " + event);
                }
            }
            checkTransaction(events, failures);
        }
    }

    /**
     * an asynchronous method calling the same method of the same bean asynchronously.
     */
    private void nested(List<String> failures, List<String> notes) throws Exception {
        notes.add("result " + nestedService.nested(1).get(30, SECONDS));

        List<String> methods = trace.events().stream().filter(event -> event.startsWith("method nested")).toList();
        if (methods.size() != 2) {
            failures.add("expected 2 invocations, got " + methods.size());
            return;
        }

        String outerThread = field(methods.get(0), "thread");
        String innerThread = field(methods.get(1), "thread");
        if (outerThread.equals(innerThread)) {
            failures.add("the nested call wasn't dispatched to another thread");
        }
        if (outerThread.equals(Trace.thread()) || innerThread.equals(Trace.thread())) {
            failures.add("ran on the caller's thread");
        }
        if (field(methods.get(0), "target").equals(field(methods.get(1), "target"))) {
            failures.add("both on the same instance");
        }
        if (!trace.events().contains("submitted inner, done=false")) {
            notes.add("inner future was already done when submitted (not a failure by itself)");
        }
    }

    /**
     * a returned future that isn't complete yet is resolved on the worker, before the transaction ends and
     * the pooled instance is released.
     */
    private void incomplete(List<String> failures, List<String> notes) throws Exception {
        trace.newLatch("complete-inner", 1);
        Future<String> future = pooledService.incomplete();

        if (!trace.await(event -> event.equals("method-return"), 10_000)) {
            failures.add("the method didn't return");
            return;
        }

        // The worker holds the only pooled instance until the result is resolved
        Thread pinger = newThread(() -> pooledService.ping(), "pinger");
        pinger.start();

        if (future.isDone()) {
            failures.add("done before the returned future completed");
        }
        try {
            future.get(100, MILLISECONDS);
            failures.add("get with timeout didn't time out");
        } catch (TimeoutException expected) {
            // Expected
        }

        trace.countDown("complete-inner");
        String result = future.get(10, SECONDS);
        trace.add("client-done");
        if (!"late".equals(result)) {
            failures.add("result " + result);
        }
        pinger.join(10_000);

        List<String> events = trace.events();
        int methodReturn = indexOf(events, "method-return");
        int innerComplete = indexOf(events, "inner-complete");
        int beforeCompletion = indexOf(events, "tx-before-completion");
        int afterCompletion = indexOf(events, "tx-after-completion");
        int ping = indexOf(events, "ping ");
        int clientDone = indexOf(events, "client-done");

        if (!(methodReturn < innerComplete && innerComplete < beforeCompletion && beforeCompletion < afterCompletion)) {
            failures.add("the transaction ended before the result was resolved");
        }
        if (!(afterCompletion < clientDone)) {
            failures.add("the caller's future completed before the transaction ended");
        }
        if (ping == Integer.MAX_VALUE) {
            failures.add("ping didn't run");
        } else if (!(afterCompletion < ping)) {
            failures.add("the pooled instance was released before the result was resolved");
        }
    }

    /**
     * cancelling a task that hasn't started.
     */
    private void cancelQueued(List<String> failures, List<String> notes) throws Exception {
        ExecutorService executorService = executorBean.getExecutorService();
        notes.add("executor " + executorService);

        int threads = executorService instanceof ThreadPoolExecutor threadPoolExecutor ? threadPoolExecutor.getMaximumPoolSize() : MANAGED_MAX_ASYNC;
        trace.newLatch("blockers", 1);
        trace.newLatch("blockers-entered", threads);

        List<Future<String>> blockers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            blockers.add(queueFiller.block());
        }
        if (!trace.await("blockers-entered", 10_000)) {
            failures.add("the blockers didn't all start");
            trace.countDown("blockers");
            return;
        }

        Future<String> target = queueFiller.target();
        boolean cancelled = target.cancel(true);
        Future<String> sentinel = queueFiller.sentinel();

        trace.countDown("blockers");
        for (Future<String> blocker : blockers) {
            blocker.get(10, SECONDS);
        }
        sentinel.get(10, SECONDS);

        if (!cancelled) {
            failures.add("cancel returned false");
        }
        if (!target.isCancelled() || !target.isDone()) {
            failures.add("not cancelled and done");
        }
        try {
            target.get(1, SECONDS);
            failures.add("get didn't throw");
        } catch (CancellationException expected) {
            // Expected
        }
        if (trace.events().contains("target ran")) {
            failures.add("the cancelled task ran");
        }
    }

    /**
     * cancelling a running task doesn't interrupt it, but sets wasCancelCalled, and the pooled instance is
     * only released when it ends.
     */
    private void cancelRunning(List<String> failures, List<String> notes) throws Exception {
        trace.newLatch("entered", 1);
        trace.newLatch("finish", 1);

        Future<String> future = pooledService.cancellable();
        if (!trace.await("entered", 10_000)) {
            failures.add("the method didn't start");
            return;
        }

        boolean cancelled = future.cancel(true);
        if (cancelled || future.isCancelled()) {
            failures.add("a running invocation was cancelled");
        }
        if (!trace.await(event -> event.equals("saw-cancel=true"), 10_000)) {
            failures.add("wasCancelCalled didn't become true");
        }

        Thread pinger = newThread(() -> pooledService.ping(), "pinger");
        pinger.start();
        Thread.sleep(300);
        if (indexOf(trace.events(), "ping ") != Integer.MAX_VALUE) {
            failures.add("the pooled instance was released while the method was running");
        }

        trace.countDown("finish");
        String result = future.get(10, SECONDS);
        pinger.join(10_000);

        if (!"cancel-called=true".equals(result)) {
            failures.add("result " + result);
        }
        if (trace.events().contains("interrupted")) {
            failures.add("the method was interrupted");
        }

        List<String> events = trace.events();
        if (indexOf(events, "ping ") == Integer.MAX_VALUE) {
            failures.add("ping didn't run");
        } else if (!(indexOf(events, "method-exit") < indexOf(events, "ping "))) {
            failures.add("ping ran before the method exited");
        }
    }

    /**
     * a bean that's pooled and transactional through the @Service stereotype.
     */
    private void stereotype(List<String> failures, List<String> notes) throws Exception {
        checkPooledScope(ServiceBean.class, failures);
        notes.add("result " + serviceBean.work());

        List<String> events = withoutCreated(trace.events());
        String[] expected = { "before-pool work", "before-pool work", "after-pool work", "after-lock work", "in-tx work", "application work", "method work" };
        checkOrder(events, expected, failures);

        if (failures.isEmpty()) {
            if (field(events.get(0), "target").equals(field(events.get(1), "target"))) {
                failures.add("the method didn't move to a pooled instance");
            }
            checkTransaction(events, failures);
        }
    }

    /**
     * pool settings from a stereotype (one instance, 300 ms timeout).
     */
    private void stereotypeSettings(List<String> failures, List<String> notes) throws Exception {
        checkPooledScope(SinglePoolBean.class, failures);

        // The pool's one instance, each time
        String first = singlePoolBean.id();
        String second = singlePoolBean.id();
        if (!first.equals(second)) {
            failures.add("calls used instances " + first + " and " + second + ", not the pool's one instance");
        }

        trace.newLatch("single", 1);
        trace.newLatch("single-holding", 1);

        Thread holderThread = newThread(() -> singlePoolBean.hold("single"), "holder");
        holderThread.start();
        if (!trace.await("single-holding", 10_000)) {
            failures.add("holder didn't start");
            return;
        }

        long start = System.nanoTime();
        try {
            singlePoolBean.ping();
            failures.add("got a second instance");
        } catch (PoolLockTimeoutException expected) {
            long millis = (System.nanoTime() - start) / 1_000_000;
            notes.add("timed out after " + millis + " ms");
            if (millis < 250 || millis > 5000) {
                failures.add("timeout of " + millis + " ms isn't the stereotype's 300 ms");
            }
        } finally {
            trace.countDown("single");
            holderThread.join(10_000);
        }
    }

    /**
     * void methods return at once, run on a worker, and their exceptions don't reach the caller.
     */
    private void voidMethods(List<String> failures, List<String> notes) throws Exception {
        fireAndForget.fire();
        fireAndForget.fail();

        if (!trace.await(event -> event.startsWith("fired"), 10_000) || !trace.await(event -> event.startsWith("failing"), 10_000)) {
            failures.add("didn't run");
            return;
        }

        for (String event : trace.events()) {
            if (field(event, "thread").equals(Trace.thread())) {
                failures.add("ran on the caller's thread: " + event);
            }
        }
    }

    /**
     * the executor service that was chosen.
     */
    private void executor(List<String> failures, List<String> notes) {
        String executor = executorBean.getExecutorService().getClass().getName();
        String expected = System.getProperty("omniservices.test.expectedExecutor");
        notes.add("executor " + executor + ", expected " + expected);

        if (expected == null || !executor.contains(expected)) {
            failures.add("unexpected executor " + executor);
        }
    }

    /**
     * an asynchronous method doesn't run in the caller's transaction.
     */
    private void callerTransaction(List<String> failures, List<String> notes) throws Exception {
        notes.add("result " + txCaller.callAsync());

        String callerKey = field(trace.events().stream().filter(event -> event.startsWith("caller")).findFirst().orElse(""), "key");
        String workerKey = field(trace.events().stream().filter(event -> event.startsWith("method work")).findFirst().orElse(""), "key");
        notes.add("caller transaction " + callerKey + ", worker transaction " + workerKey);

        if (callerKey.isEmpty() || callerKey.equals("null")) {
            failures.add("the caller had no transaction");
        }
        if (workerKey.isEmpty() || workerKey.equals("null")) {
            failures.add("the method had no transaction");
        }
        if (callerKey.equals(workerKey)) {
            failures.add("the method ran in the caller's transaction");
        }
    }

    /**
     * a full executor rejects the invocation, at the caller, without leaking a pooled instance.
     */
    private void rejection(List<String> failures, List<String> notes) throws Exception {
        ExecutorService executorService = executorBean.getExecutorService();
        if (executorService instanceof ThreadPoolExecutor) {
            notes.add("skipped: unbounded executor " + executorService);
            return;
        }

        int capacity = MANAGED_MAX_ASYNC + MANAGED_MAX_QUEUED;
        trace.newLatch("blockers", 1);
        trace.newLatch("blockers-entered", capacity);

        List<Future<String>> blockers = new ArrayList<>();
        try {
            for (int i = 0; i < capacity; i++) {
                blockers.add(queueFiller.block());
            }
            long deadline = System.currentTimeMillis() + 10_000;
            while (trace.count("blockers-entered") > MANAGED_MAX_QUEUED && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }

            try {
                pooledService.work();
                failures.add("not rejected");
            } catch (RejectedExecutionException expected) {
                notes.add("rejected: " + expected);
            }
        } finally {
            trace.countDown("blockers");
        }

        for (Future<String> blocker : blockers) {
            blocker.get(10, SECONDS);
        }

        // The pool of one instance is still available
        notes.add("after rejection " + pooledService.work().get(10, SECONDS));
    }

    /**
     * Leaves work outstanding for the application's shutdown, which ShutdownTest checks: a pooled invocation that runs
     * past the shutdown time limit, the other threads of the executor busy, and an invocation still queued.
     */
    /**
     * waiting for the future an asynchronous method returned is interrupted: the transaction rolls back, and the caller's
     * future fails with an UncheckedInterruptedException.
     */
    private void interruptedResult(List<String> failures, List<String> notes) throws Exception {
        try {
            pooledService.interrupted().get(30, SECONDS);
            failures.add("no exception");
        } catch (ExecutionException e) {
            notes.add("cause " + e.getCause());
            if (!(e.getCause() instanceof UncheckedInterruptedException)) {
                failures.add("cause " + e.getCause());
            }
        }

        if (!trace.events().contains("interrupted tx status=" + Status.STATUS_ROLLEDBACK)) {
            failures.add("the transaction didn't roll back");
        }
    }

    /**
     * a thread that holds the read lock and needs the write lock again, through another bean, fails at once rather than
     * waiting for itself; the other calls back run under the lock the thread holds.
     */
    private void loopback(List<String> failures, List<String> notes) {
        long start = System.nanoTime();
        String result = loopbackSingleton.readThenWrite();
        long millis = (System.nanoTime() - start) / 1_000_000;
        notes.add("read, then write: " + result + " after " + millis + " ms");
        if (!result.equals("refused") || millis > 1000) {
            failures.add("read, then write: " + result + " after " + millis + " ms");
        }

        checkLoopback("read, then read", loopbackSingleton::readThenRead, "read", failures);
        checkLoopback("write, then write", loopbackSingleton::writeThenWrite, "written", failures);
        checkLoopback("write, then read", loopbackSingleton::writeThenRead, "read", failures);
    }

    private static void checkLoopback(String name, Supplier<String> call, String expected, List<String> failures) {
        try {
            String result = call.get();
            if (!result.equals(expected)) {
                failures.add(name + ": " + result);
            }
        } catch (RuntimeException e) {
            failures.add(name + ": " + e);
        }
    }

    private void outstandingWork(List<String> failures, List<String> notes) throws Exception {
        ExecutorService executorService = executorBean.getExecutorService();
        int threads = executorService instanceof ThreadPoolExecutor threadPoolExecutor ? threadPoolExecutor.getMaximumPoolSize() : MANAGED_MAX_ASYNC;

        pooledService.outstanding();
        for (int i = 1; i < threads; i++) {
            queueFiller.busyUntilShutdown();
        }

        long deadline = System.currentTimeMillis() + 10_000;
        while ((QueueFiller.busy.get() < threads - 1 || Trace.recorded().stream().noneMatch(event -> event.startsWith("outstanding started")))
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        if (QueueFiller.busy.get() < threads - 1) {
            failures.add("only " + QueueFiller.busy.get() + " of " + (threads - 1) + " busy invocations started");
        }

        queueFiller.queued();
        preDestroyCaller.activate();
        notes.add("executor " + executorService);
    }

    /**
     * The scope of a bean that's pooled through a stereotype is Pooled (on ArC, OmniServices declares it on the class).
     */
    private void checkPooledScope(Class<?> beanClass, List<String> failures) {
        Class<? extends Annotation> scope = beanManager.resolve(beanManager.getBeans(beanClass)).getScope();
        if (scope != Pooled.class) {
            failures.add(beanClass.getSimpleName() + " has scope " + scope.getSimpleName() + ", not Pooled");
        }
    }

    /**
     * A thread with a request context, which some @Transactional implementations need (Piranha's Transact).
     */
    private Thread newThread(Runnable runnable, String name) {
        return new Thread(() -> {
            RequestContextController requestContextController = requestContextControllers.get();
            requestContextController.activate();
            try {
                runnable.run();
            } finally {
                requestContextController.deactivate();
            }
        }, name);
    }

    private static List<String> withoutCreated(List<String> events) {
        return events.stream().filter(event -> !event.startsWith("created")).toList();
    }

    private static void checkOrder(List<String> events, String[] expected, List<String> failures) {
        if (events.size() != expected.length) {
            failures.add("expected " + expected.length + " events, got " + events.size());
            return;
        }
        for (int i = 0; i < expected.length; i++) {
            if (!events.get(i).startsWith(expected[i] + " ")) {
                failures.add("event " + i + " should be '" + expected[i] + "'");
            }
        }
    }

    private static void checkTransaction(List<String> events, List<String> failures) {
        for (String event : events) {
            boolean expectTransaction = event.startsWith("in-tx") || event.startsWith("application") || event.startsWith("method");
            if (expectTransaction != event.endsWith("tx=true")) {
                failures.add("unexpected transaction state: " + event);
            }
        }
    }

    private static String field(String event, String name) {
        for (String part : event.split(" ")) {
            if (part.startsWith(name + "=")) {
                return part.substring(name.length() + 1);
            }
        }
        return "";
    }

    private static int indexOf(List<String> events, String prefix) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }
}
