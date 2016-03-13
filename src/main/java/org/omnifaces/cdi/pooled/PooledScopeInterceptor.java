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

		if (context.isPooledScopeActive()) {
			return ctx.proceed();
		}

		context.startPooledScope();
		try {
			Object target = ctx.getTarget();

			// The current interceptor is on a dummy instance, now we're in an active PooledScope, we can use a "real" instance from the pool

			Bean<?> bean = beanManager.getBeans(target.getClass()).stream().findFirst().get();

			CreationalContext<?> creationalContext = beanManager.createCreationalContext(bean);
			Object reference = beanManager.getReference(bean, target.getClass(), creationalContext);

			return ctx.getMethod().invoke(reference, ctx.getParameters());
		} finally {
			context.endPooledScope();
		}
	}
}
