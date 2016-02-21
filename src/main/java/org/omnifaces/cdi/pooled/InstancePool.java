package org.omnifaces.cdi.pooled;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;

import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;

class InstancePool<T> {

	private static final int MAX_NUMBER_OF_INSTANCES = 10;

	private final Contextual<T> contextual;
	private final Map<PoolKey<T>, T> instances = new ConcurrentHashMap<>();
	private final Queue<PoolKey<T>> freeInstanceKeys = new ConcurrentLinkedQueue<>();

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
		return freeInstanceKeys.remove();
	}

	public void releaseInstance(PoolKey<T> key) {
		if (!contextual.equals(key.getContextual())) {
			throw new IllegalArgumentException();
		}

		freeInstanceKeys.add(key);
	}
}
