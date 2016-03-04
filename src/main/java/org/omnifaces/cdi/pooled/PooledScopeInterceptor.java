package org.omnifaces.cdi.pooled;

import static javax.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

import javax.annotation.Priority;
import javax.enterprise.context.spi.CreationalContext;
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

	@AroundInvoke
	public Object aroundInvoke(InvocationContext ctx) throws Exception {
		PooledContext context = (PooledContext) beanManager.getContext(Pooled.class);

		Object target = ctx.getTarget();
		if (!context.isDummy(target)) {
			return ctx.proceed();
		}

		Bean<?> contextual = context.getBeanByDummyInstance(target);
		PoolKey<?> poolKey = context.allocateBean(contextual);

		try {
			CreationalContext<?> creationalContext = beanManager.createCreationalContext(contextual);
			Object reference = beanManager.getReference(contextual, target.getClass(), creationalContext);

			ctx.getMethod().invoke(reference, ctx.getParameters());
		}
		finally {
			context.releaseBean(poolKey);
		}

		return null;
	}
}
