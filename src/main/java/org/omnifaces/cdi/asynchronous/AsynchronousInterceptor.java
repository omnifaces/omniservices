package org.omnifaces.cdi.asynchronous;

import static javax.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

import java.io.Serializable;

import javax.annotation.Priority;
import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

@Interceptor
@Asynchronous
@Priority(PLATFORM_BEFORE)
public class AsynchronousInterceptor implements Serializable {
 
    private static final long serialVersionUID = 1L;
 
    @Resource
    private ManagedExecutorService managedExecutorService;
 
    @AroundInvoke
    public Object submitAsync(InvocationContext ctx) throws Exception {
        return new FutureDelegator(managedExecutorService.submit( ()-> { return ctx.proceed(); } ));
    }
}