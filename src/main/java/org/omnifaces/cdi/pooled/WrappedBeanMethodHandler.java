package org.omnifaces.cdi.pooled;

import java.lang.reflect.Method;

import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;

import javassist.util.proxy.MethodHandler;

public class WrappedBeanMethodHandler<T> implements MethodHandler {

	private final Contextual<T> contextual;

	private final CreationalContext<T> context;

	private final PooledContext pooledContext;

	public WrappedBeanMethodHandler(Contextual<T> contextual, CreationalContext<T> context, PooledContext pooledContext) {
		this.contextual = contextual;
		this.context = context;
		this.pooledContext = pooledContext;
	}


	@Override
	public Object invoke(Object o, Method method, Method method1, Object[] objects) throws Throwable {
		PoolKey<T> poolKey = pooledContext.allocateBean(contextual);

		try {
			T bean = pooledContext.getBean(poolKey, context);

			return method.invoke(bean, objects);
		}
		finally {
			pooledContext.releaseBean(poolKey);
		}
	}

}
