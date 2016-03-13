package org.omnifaces.cdi.pooled;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import javax.enterprise.context.spi.Context;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;

public class PooledContext implements Context {

	private final ThreadLocal<AtomicReference<PooledScope>> pooledScope = ThreadLocal.withInitial(AtomicReference<PooledScope>::new);
	private final Map<Contextual<?>, Class<?>> proxyClasses = new ConcurrentHashMap<>();
	private final Map<Contextual<?>, InstancePool<?>> instancePools = new ConcurrentHashMap<>();

	// TODO would prefer some kind of two-way map for this
	private final Map<Contextual<?>, Object> dummyInstances = new ConcurrentHashMap<>();
	private final Map<Object, Bean<?>> beansByDummyInstance = new ConcurrentHashMap<>();

	@Override
	public Class<? extends Annotation> getScope() {
		return Pooled.class;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T get(Contextual<T> contextual, CreationalContext<T> creationalContext) {
		if (contextual instanceof Bean) {
			PooledScope activePooledScope = getActivePooledScope();

			if (activePooledScope == null) {
				return (T) dummyInstances.computeIfAbsent(contextual, ctx -> contextual.create(creationalContext));
			}

			PoolKey<T> poolKey = activePooledScope.getPoolKey(contextual);

			if (poolKey == null) {
				poolKey = allocateBean(contextual);
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
			PooledScope pooledScope = getActivePooledScope();

			if (pooledScope == null) {
				return (T) dummyInstances.get(contextual);
			}

 			PoolKey<T> poolKey = pooledScope.getPoolKey(contextual);

			if (poolKey == null) {
				// Don't allocate a new instance here, as instances are created lazily, we may need to instantiate it
				return null;
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
	private <T> PoolKey<T> allocateBean(Contextual<T> contextual) {
		return ((InstancePool<T>) instancePools.computeIfAbsent(contextual, InstancePool::new)).allocateInstance();
	}


	@SuppressWarnings("unchecked")
	private <T> void releaseBean(PoolKey<T> poolKey) {
		((InstancePool<T>) instancePools.get(poolKey.getContextual())).releaseInstance(poolKey);
	}

	void startPooledScope() {
		pooledScope.get().compareAndSet(null, new PooledScope());
	}

	void endPooledScope() {
		AtomicReference<PooledScope> pooledScopeAtomicReference = pooledScope.get();

		pooledScopeAtomicReference.get().getAllocatedPoolKeys().forEach(this::releaseBean);

		pooledScopeAtomicReference.set(null);
	}

	boolean isPooledScopeActive() {
		return getActivePooledScope() != null;
	}

	private PooledScope getActivePooledScope() {
		return pooledScope.get().get();
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
