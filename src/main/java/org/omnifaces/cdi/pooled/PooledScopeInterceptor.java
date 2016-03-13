package org.omnifaces.cdi.pooled;

import static javax.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

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
	public Object aroundInvoke(InvocationContext ctx) throws Exception {
		PooledContext context = (PooledContext) beanManager.getContext(Pooled.class);

		if (context.hasAllocatedInstanceOf(interceptedBean)) {
			return ctx.proceed();
		}

		PoolKey<?> poolKey = context.allocateBean(interceptedBean);

		try {
			CreationalContext<?> creationalContext = beanManager.createCreationalContext(interceptedBean);
			Object reference = beanManager.getReference(interceptedBean, interceptedBean.getBeanClass(), creationalContext);

			return ctx.getMethod().invoke(reference, ctx.getParameters());
		}
		finally {
			context.releaseBean(poolKey);
		}
	}
}
