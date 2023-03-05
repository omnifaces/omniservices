/*
 * Copyright 2021, 2023 OmniFaces
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
import java.util.concurrent.locks.ReadWriteLock;
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

@Interceptor
@Lock
@Priority(PLATFORM_BEFORE)
public class LockInterceptor implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private BeanManager beanManager;

    @Inject
    @Intercepted
    private Bean<?> interceptedBean;

    private ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

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
        return lockAnnotation.type() == READ? readWriteLock.readLock() : readWriteLock.writeLock();
    }

    private Lock getLockAnnotation(InvocationContext ctx) {
        return CdiUtils.getInterceptorBindingAnnotation(ctx, beanManager, interceptedBean, Lock.class);
    }

    private void acquireLock(Lock lockAnnotation, java.util.concurrent.locks.Lock lock) throws InterruptedException {
        switch (lockAnnotation.timeoutType()) {
            case TIMEOUT:
                if (!lock.tryLock(lockAnnotation.accessTimeout(), lockAnnotation.unit())) {
                    throw new IllegalStateException(
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
                    throw new IllegalStateException("Lock already locked, and no wait allowed");
                }
                break;
        }
    }

}