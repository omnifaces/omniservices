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

	private final Map<Contextual<?>, Class<?>> proxyClasses = new ConcurrentHashMap<>();
	private final Map<Contextual<?>, InstancePool<?>> instancePools = new ConcurrentHashMap<>();
	public final ThreadLocal<PoolKey<?>> threadPoolKey = new ThreadLocal<>();

	@Override
	public Class<? extends Annotation> getScope() {
		return Pooled.class;
	}

	@Override
	public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
		if (contextual instanceof Bean) {
			PoolKey<T> poolKey = allocateBean(contextual);
			threadPoolKey.set(poolKey);
			return getBean(poolKey, creationalContext);
		}

		// TODO add clear error message and pick better exception
		throw new IllegalArgumentException();
	}

	@Override
	public <T> T get(Contextual<T> contextual) {
		// TODO return an available existing proxy in the current thread
		return null;
	}

	@Override
	public boolean isActive() {
		return true;
	}

	@SuppressWarnings("unchecked")
	public <T> PoolKey<T> allocateBean(Contextual<T> contextual) {
		return ((InstancePool<T>) instancePools.computeIfAbsent(contextual, InstancePool::new)).allocateInstance();
	}

	@SuppressWarnings("unchecked")
	public <T> void releaseBean(PoolKey<T> poolKey) {
		((InstancePool<T>) instancePools.get(poolKey.getContextual())).releaseInstance(poolKey);
	}

	@SuppressWarnings("unchecked")
	public <T> T getBean(PoolKey<T> poolKey, CreationalContext<T> creationalContext) {
		return ((InstancePool<T>) instancePools.get(poolKey.getContextual())).getInstance(poolKey, creationalContext);
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
