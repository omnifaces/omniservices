/*
 * Copyright 2020 OmniFaces
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

import static javax.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

import java.lang.reflect.InvocationTargetException;

import javax.annotation.Priority;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.Intercepted;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

@Interceptor
@Priority(PLATFORM_BEFORE)
@PooledScopeEnabled
public class PooledScopeInterceptor {

	@Inject
	private BeanManager beanManager;

	@Inject
	@Intercepted
	private Bean<?> interceptedBean;

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
		try {
			return ctx.getMethod().invoke(instance, ctx.getParameters());
		}
		catch(InvocationTargetException e) {
			throw e.getCause();
		}
	}

	private void destroyBeanIfNeeded(PooledContext pooledContext, Throwable throwable) throws Throwable {
		if (pooledContext.mustDestroyBeanWhenCaught(interceptedBean, throwable)) {
			pooledContext.destroy(interceptedBean);
		}
	}
}
