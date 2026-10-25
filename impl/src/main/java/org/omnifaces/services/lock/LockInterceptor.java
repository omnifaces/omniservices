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
package org.omnifaces.services.lock;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE;
import static org.omnifaces.services.lock.Lock.Type.READ;

import java.io.Serializable;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.omnifaces.services.util.CdiUtils;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Intercepted;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

/**
 * Locks the intercepted bean instance for the invocation of a {@link Lock} method, as specified by the method's
 * {@link Lock}, else by its class'.
 * <p>
 * A thread that holds a lock of the instance and calls one of its methods again, directly or through other beans, keeps
 * that lock: it can take the read lock again, and with the write lock, take either. A read lock isn't upgraded, though:
 * a thread that holds the read lock gets an {@link IllegalLoopbackException} when it needs the write lock, rather than
 * waiting for itself.
 * <p>
 * Its priority puts it after the asynchronous and pooled scope interceptors, so an asynchronous method is locked on
 * the thread it's dispatched to, and before the interceptors that start a transaction.
 *
 * @serial exclude
 */
@Interceptor
@Lock
@Priority(PLATFORM_BEFORE + 100)
public class LockInterceptor implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private BeanManager beanManager;

    @Inject
    @Intercepted
    private Bean<?> interceptedBean;

    private ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    /**
     * Creates the interceptor. Used by CDI.
     */
    public LockInterceptor() {
    }

    /**
     * Acquires the lock, invokes the method, and releases the lock.
     *
     * @param ctx the invocation context
     * @return what the method returned
     * @throws Exception what the method threw
     * @throws LockTimeoutException when the lock can't be acquired as specified
     * @throws IllegalLoopbackException when the method needs the write lock, and this thread holds the read lock
     */
    @AroundInvoke
    public Object doLock(InvocationContext ctx) throws Exception {
        Lock lockAnnotation = getLockAnnotation(ctx);
        java.util.concurrent.locks.Lock lock = getReadOrWriteLock(lockAnnotation);

        acquireLock(lockAnnotation, lock);
        try {
            return ctx.proceed();
        } finally {
            lock.unlock();
        }
    }

    private java.util.concurrent.locks.Lock getReadOrWriteLock(Lock lockAnnotation) {
        if (lockAnnotation.type() == READ) {
            return readWriteLock.readLock();
        }

        if (readWriteLock.getReadHoldCount() > 0 && !readWriteLock.isWriteLockedByCurrentThread()) {
            throw new IllegalLoopbackException("The write lock is needed by a thread that holds the read lock");
        }

        return readWriteLock.writeLock();
    }

    private Lock getLockAnnotation(InvocationContext ctx) {
        return CdiUtils.getInterceptorBindingAnnotation(ctx, beanManager, interceptedBean, Lock.class);
    }

    private void acquireLock(Lock lockAnnotation, java.util.concurrent.locks.Lock lock) throws InterruptedException {
        switch (lockAnnotation.timeoutType()) {
            case TIMEOUT:
                if (!lock.tryLock(lockAnnotation.accessTimeout(), lockAnnotation.unit())) {
                    throw new LockTimeoutException(
                        "Could not obtain lock in " +
                        lockAnnotation.accessTimeout() + " " +
                        lockAnnotation.unit().name());
                }
                break;

            case INDEFINITTE:
                lock.lock();
                break;

            case NOT_PERMITTED:
                if (!lock.tryLock()) {
                    throw new LockTimeoutException("Lock already locked, and no wait allowed");
                }
                break;
        }
    }

}