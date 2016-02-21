package org.omnifaces.cdi.pooled;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.context.spi.Context;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;

public class PooledContext implements Context {

	private final ThreadLocal<Map<Contextual<?>, PoolKey<?>>> activeBeanKeys = ThreadLocal.withInitial(HashMap::new);

	private final Map<Contextual<?>, InstancePool<?>> instancePools = new ConcurrentHashMap<>();

	@Override
	public Class<? extends Annotation> getScope() {
		return Pooled.class;
	}

	@Override
	public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
		PoolKey<T> poolKey = (PoolKey<T>) activeBeanKeys.get().get(contextual);

		if (poolKey == null) {
			// TODO add error message
			throw new IllegalStateException();
		}


		return ((InstancePool<T>) instancePools.get(contextual)).getInstance(poolKey, creationalContext);
	}

	@Override
	public <T> T get(Contextual<T> contextual) {
		PoolKey<T> poolKey = (PoolKey<T>) activeBeanKeys.get().get(contextual);

		if (poolKey == null) {
			// TODO check spec if this is correct behaviour or if we should try to allocate an existing bean anyway
			return null;
		}

		return ((InstancePool<T>) instancePools.get(contextual)).getInstance(poolKey);
	}

	@Override
	public boolean isActive() {
		return true;
	}

	public <T> PoolKey<T> allocateBean(Contextual<T> contextual) {
		Map<Contextual<?>, PoolKey<?>> beanKeys = activeBeanKeys.get();

		if (beanKeys.containsKey(contextual)) {
			return null;
		}

		InstancePool<T> instancePool = (InstancePool<T>) instancePools.computeIfAbsent(contextual, InstancePool::new);

		PoolKey<T> poolKey = instancePool.allocateInstance();

		beanKeys.put(contextual, poolKey);

		return poolKey;
	}

	public <T> void releaseBean(PoolKey<T> key) {
		if (key != null) {
			Map<Contextual<?>, PoolKey<?>> beanKeys = activeBeanKeys.get();

			beanKeys.remove(key);

			InstancePool<T> instancePool = (InstancePool<T>) instancePools.get(key);

			instancePool.releaseInstance(key);
		}
	}
}
