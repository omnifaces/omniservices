package org.omnifaces.cdi.pooled;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.IntStream;

import javax.enterprise.context.spi.Context;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;

public class PooledContext implements Context {

	private final ThreadLocal<PooledScope> poolScope = ThreadLocal.withInitial(PooledScope::new);
	private final Map<Contextual<?>, InstancePool<?>> instancePools = new ConcurrentHashMap<>();

	// TODO would prefer some kind of two-way map for this
	private final Map<Contextual<?>, Object> dummyInstances = new ConcurrentHashMap<>();

	@Override
	public Class<? extends Annotation> getScope() {
		return Pooled.class;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
		if (contextual instanceof Bean) {
			PoolKey<T> poolKey = poolScope.get().getPoolKey(contextual);

			if (poolKey == null) {
				return (T) dummyInstances.computeIfAbsent(contextual, ctx -> contextual.create(creationalContext));
			}

			return ((InstancePool<T>)instancePools.get(poolKey.getContextual())).getInstance(poolKey, creationalContext);
		}

		// TODO add clear error message and pick better exception
		throw new IllegalArgumentException();
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T get(Contextual<T> contextual) {
		if (contextual instanceof Bean) {
			PoolKey<T> poolKey = poolScope.get().getPoolKey(contextual);

			if (poolKey == null) {
				return (T) dummyInstances.get(contextual);
			}

			return ((InstancePool<T>)instancePools.get(poolKey.getContextual())).getInstance(poolKey);
		}
		return null;
	}

	@Override
	public boolean isActive() {
		return true;
	}

	@SuppressWarnings("unchecked")
	<T> PoolKey<T> allocateBean(Contextual<T> contextual) {
		PoolKey<T> poolKey = ((InstancePool<T>) instancePools.computeIfAbsent(contextual, InstancePool::new)).allocateInstance();

		poolScope.get().setPoolKey(poolKey);

		return poolKey;
	}

	@SuppressWarnings("unchecked")
	<T> void releaseBean(PoolKey<T> poolKey) {
		poolScope.get().removePoolKey(poolKey);

		((InstancePool<T>) instancePools.get(poolKey.getContextual())).releaseInstance(poolKey);
	}

	boolean hasAllocatedInstanceOf(Bean<?> bean) {
		return poolScope.get().getPoolKey(bean) != null;
	}

	static class InstancePool<T> {

		private static final int MAX_NUMBER_OF_INSTANCES = 10;

		private final Contextual<T> contextual;
		private final Map<PoolKey<T>, T> instances = new ConcurrentHashMap<>();
		private final BlockingDeque<PoolKey<T>> freeInstanceKeys = new LinkedBlockingDeque<>();

		public InstancePool(Contextual<T> contextual) {
			this.contextual = contextual;

			IntStream.range(0, MAX_NUMBER_OF_INSTANCES)
			         .mapToObj(i -> new PoolKey<>(contextual, i))
			         .forEach(freeInstanceKeys::add);
		}

		public T getInstance(PoolKey<T> poolKey) {
			if (!poolKey.getContextual().equals(contextual)) {
				throw new IllegalArgumentException();
			}

			return instances.get(poolKey);
		}

		public T getInstance(PoolKey<T> poolKey, CreationalContext<T> context) {
			if (!poolKey.getContextual().equals(contextual)) {
				throw new IllegalArgumentException();
			}

			return instances.computeIfAbsent(poolKey, key -> contextual.create(context));
		}

		public PoolKey<T> allocateInstance() {
			try {
				return freeInstanceKeys.takeFirst();
			} catch (InterruptedException e) {
				throw new UncheckedInterruptedException(e);
			}
		}

		public void releaseInstance(PoolKey<T> key) {
			if (!contextual.equals(key.getContextual())) {
				throw new IllegalArgumentException();
			}

			freeInstanceKeys.addFirst(key);
		}
	}
}
