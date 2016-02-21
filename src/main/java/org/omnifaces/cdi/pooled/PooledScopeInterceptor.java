package org.omnifaces.cdi.pooled;

import static javax.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

import javax.annotation.Priority;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

@Interceptor
@Priority(LIBRARY_BEFORE + 42)
@PooledScopeEnabled
public class PooledScopeInterceptor {

	@Inject
	private BeanManager beanManager;

	@AroundInvoke
	public Object submitAsync(InvocationContext ctx) throws Exception {
		// TODO implement
		return ctx.proceed();
	}
}
