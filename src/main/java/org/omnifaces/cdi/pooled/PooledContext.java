package org.omnifaces.cdi.pooled;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import javax.enterprise.context.spi.Context;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;

public class PooledContext implements Context {

	private final ThreadLocal<AtomicReference<PooledScope>> activeScope = ThreadLocal.withInitial(AtomicReference::new);

	private final Map<Contextual<?>, InstancePool<?>> instancePools = new ConcurrentHashMap<>();

	@Override
	public Class<? extends Annotation> getScope() {
		return Pooled.class;
	}

	@Override
	public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
		PooledScope pooledScope = activeScope.get().get();

		PoolKey<T> poolKey = pooledScope.getPoolKey(contextual);

		if (poolKey == null) {
			poolKey = ((InstancePool<T>)instancePools.get(contextual)).allocateInstance();
			pooledScope.setCurrentPoolKey(poolKey);
		}


		return ((InstancePool<T>) instancePools.get(contextual)).getInstance(poolKey, creationalContext);
	}

	@Override
	public <T> T get(Contextual<T> contextual) {
		PooledScope pooledScope = activeScope.get().get();

		PoolKey<T> poolKey = pooledScope.getPoolKey(contextual);

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

	public void pushNewScope() {
		activeScope.get().getAndUpdate(PooledScope::newPooledScope);
	}

	public void popScope() {
		activeScope.get().getAndUpdate(PooledScope::cleanCurrentScope);
	}

}
