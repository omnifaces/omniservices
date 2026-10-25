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
package org.omnifaces.services.pooled;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

import java.lang.reflect.InvocationTargetException;

import org.omnifaces.services.asynchronous.Asynchronous;
import org.omnifaces.services.internal.Reinvocation;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Intercepted;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

/**
 * Invokes a method of a {@link Pooled} bean on an instance from the pool.
 * <p>
 * A method invoked on a thread that doesn't hold an instance of the bean yet is invoked on the bean's dispatch
 * instance (see {@link PooledContext}). For that invocation, this interceptor takes an instance from the pool, invokes
 * the method again through the bean's contextual reference, which on this thread now resolves to that instance, and
 * releases the instance afterwards. The interceptors after this one only run for the invocation on the pooled instance.
 * <p>
 * Its priority is after the asynchronous interceptor, so an asynchronous method takes an instance on the thread it's
 * dispatched to, and the caller never waits for one.
 */
@Interceptor
@Priority(PLATFORM_BEFORE + 50)
@PooledScopeEnabled
public class PooledScopeInterceptor {

	@Inject
	private BeanManager beanManager;

	@Inject
	@Intercepted
	private Bean<?> interceptedBean;

	/**
	 * Creates the interceptor. Used by CDI.
	 */
	public PooledScopeInterceptor() {
	}

	/**
	 * Invokes the method on an instance from the pool, unless this thread already holds one.
	 *
	 * @param ctx the invocation context
	 * @return what the method returned
	 * @throws Throwable what the method threw
	 * @throws PoolLockTimeoutException when no instance becomes available in time
	 */
	@AroundInvoke
	public Object aroundInvoke(InvocationContext ctx) throws Throwable {
		PooledContext context = (PooledContext) beanManager.getContext(Pooled.class);

		if (context.hasAllocatedInstanceOf(interceptedBean)) {
			return ctx.proceed();
		}

		PoolKey<?> poolKey = context.allocateBean(interceptedBean);

		try {
			CreationalContext<?> creationalContext = beanManager.createCreationalContext(interceptedBean);
			Object reference = beanManager.getReference(interceptedBean, interceptedBean.getBeanClass(), creationalContext);

			return proceedOnInstance(ctx, reference);
		}
		catch (Throwable t) {
			destroyBeanIfNeeded(context, t);

			throw t;
		}
		finally {
			context.releaseBean(poolKey);
		}
	}

	private Object proceedOnInstance(InvocationContext ctx, Object instance) throws Throwable {
		// An asynchronous method was already dispatched to this thread, its invocation on the instance must not be
		// dispatched again
		Reinvocation reinvocation = ctx.getInterceptorBinding(Asynchronous.class) != null ? Reinvocation.begin(interceptedBean, ctx.getMethod()) : null;

		try {
			return ctx.getMethod().invoke(instance, ctx.getParameters());
		}
		catch(InvocationTargetException e) {
			throw e.getCause();
		}
		finally {
			if (reinvocation != null) {
				reinvocation.end();
			}
		}
	}

	private void destroyBeanIfNeeded(PooledContext pooledContext, Throwable throwable) throws Throwable {
		if (pooledContext.mustDestroyBeanWhenCaught(interceptedBean, throwable)) {
			pooledContext.destroy(interceptedBean);
		}
	}
}
