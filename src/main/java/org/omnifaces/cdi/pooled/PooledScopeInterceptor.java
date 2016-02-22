package org.omnifaces.cdi.pooled;

import static javax.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

import javax.annotation.Priority;
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
	public Object submitAsync(InvocationContext ctx) throws Exception {
		PooledContext context = (PooledContext) beanManager.getContext(Pooled.class);

		context.pushNewScope();
		try {
			return ctx.proceed();
		} finally {
			context.popScope();
		}
	}
}
