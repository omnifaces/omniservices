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
	private final Map<Contextual<?>, Class<?>> proxyClasses = new ConcurrentHashMap<>();
	private final Map<Contextual<?>, InstancePool<?>> instancePools = new ConcurrentHashMap<>();

	// TODO would prefer some kind of two-way map for this
	private final Map<Contextual<?>, Object> dummyInstances = new ConcurrentHashMap<>();
	private final Map<Object, Bean<?>> beansByDummyInstance = new ConcurrentHashMap<>();

	@Override
	public Class<? extends Annotation> getScope() {
		return Pooled.class;
	}

	@Override
	public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
		if (contextual instanceof Bean) {
			PoolKey<T> poolKey = poolScope.get().getPoolKey(contextual);

			if (poolKey == null) {
				return getDummyInstance((Bean<T>) contextual);
			}

			return ((InstancePool<T>)instancePools.get(poolKey.getContextual())).getInstance(poolKey, creationalContext);
		}

		// TODO add clear error message and pick better exception
		throw new IllegalArgumentException();
	}

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
		return ((InstancePool<T>) instancePools.computeIfAbsent(contextual, InstancePool::new)).allocateInstance();
	}

	@SuppressWarnings("unchecked")
	<T> void releaseBean(PoolKey<T> poolKey) {
		((InstancePool<T>) instancePools.get(poolKey.getContextual())).releaseInstance(poolKey);
	}

	private <T> T getDummyInstance(Bean<T> contextual) {
		return (T) dummyInstances.computeIfAbsent(contextual, contextual1 -> {
			try {
				Object dummyInstance = contextual.getBeanClass().newInstance();

				beansByDummyInstance.putIfAbsent(dummyInstance, contextual);

				return dummyInstance;
			}
			catch (InstantiationException |IllegalAccessException e) {
				// TODO add custom exception type
				throw new RuntimeException(e);
			}
		});
	}

	@SuppressWarnings("unchecked")
	public <T> T getBean(PoolKey<T> poolKey, CreationalContext<T> creationalContext) {
		return ((InstancePool<T>) instancePools.get(poolKey.getContextual())).getInstance(poolKey, creationalContext);
	}

	boolean isDummy(Object target) {
		return beansByDummyInstance.containsKey(dummyInstances);
	}

	Bean<?> getBeanByDummyInstance(Object target) {
		return beansByDummyInstance.get(target);
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
