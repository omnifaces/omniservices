package org.omnifaces.cdi.pooled;

import javax.annotation.Priority;
import javax.enterprise.inject.spi.*;
import javax.inject.Inject;
import javax.interceptor.*;
import javax.interceptor.Interceptor;
import static javax.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

@Interceptor
@PooledInterceptorBinding
@Priority(PLATFORM_BEFORE)
public class PooledInterceptor {

    @Inject
    BeanManager beanMgr;

    @AroundInvoke
    public Object invoke(InvocationContext ctx) throws Exception {
        Object result = ctx.proceed();
        PooledContext pooled = (PooledContext) beanMgr.getContext(Pooled.class);
        pooled.releaseBean(pooled.threadPoolKey.get());
        return result;
    }
}
