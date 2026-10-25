/*
 * Copyright 2021 OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.services.pooled;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import jakarta.enterprise.context.spi.AlterableContext;
import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.spi.Bean;

/**
 * The {@link AlterableContext} implementation for {@link Pooled} beans.
 * <p>
 * A thread that holds an instance of a pooled bean (see {@link PooledScopeInterceptor}) gets that instance. Any other
 * thread gets the bean's <em>dispatch instance</em>: one instance per bean, which only runs the interceptors that come
 * before {@link PooledScopeInterceptor}. That interceptor then takes an instance from the pool and invokes the method
 * on that instead.
 * <p>
 * Pools are created when they're first used. The pool settings are taken from the {@link Pooled} annotation on the
 * bean class or on its stereotypes, else the defaults apply.
 * <p>
 * When the application stops, the context is closed: no instances are taken from the pools anymore, and threads
 * waiting for one stop waiting. Instances that aren't in use are destroyed, those that are when they're returned, so an
 * instance is never destroyed while one of its methods runs. A bean's dispatch instance is destroyed with the last
 * instance of its pool, or, when a call after that created a new one, when the pool refuses the call.
 */
public class PooledContext implements AlterableContext {

	private final ThreadLocal<PooledScope> poolScope = ThreadLocal.withInitial(PooledScope::new);
	private final Map<Contextual<?>, InstancePool<?>> instancePools = new ConcurrentHashMap<>();

	private final Map<Contextual<?>, LazyInstance<?>> dispatchInstances = new ConcurrentHashMap<>();

	private volatile boolean closed;

	/**
	 * Creates the context. Used by CDI.
	 */
	public PooledContext() {
	}

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
				return ((LazyInstance<T>) dispatchInstances.computeIfAbsent(contextual, LazyInstance::new)).get(creationalContext);
			}

			return getInstancePool(contextual).getInstance(poolKey, creationalContext);
		}

		throw new IllegalArgumentException("Pooled scope only supports beans, not " + contextual);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T get(Contextual<T> contextual) {
		if (contextual instanceof Bean) {
			PoolKey<T> poolKey = poolScope.get().getPoolKey(contextual);

			if (poolKey == null) {
				LazyInstance<T> dispatchInstance = (LazyInstance<T>) dispatchInstances.get(contextual);
				return dispatchInstance == null ? null : dispatchInstance.get();
			}

			return getInstancePool(contextual).getInstance(poolKey);
		}

		return null;
	}

	@Override
	public boolean isActive() {
		return true;
	}

	<T> PoolKey<T> allocateBean(Contextual<T> contextual) {
		InstancePool<T> instancePool = getInstancePool(contextual);

		if (closed && instancePool.isIdle()) {
			// A call after the context was closed, which may have created a dispatch instance; the pool refuses it below
			destroyDispatchInstance(contextual);
		}

		PoolKey<T> poolKey = instancePool.allocateInstance();

		poolScope.get().setPoolKey(poolKey);

		return poolKey;
	}

	<T> void releaseBean(PoolKey<T> poolKey) {
		poolScope.get().removePoolKey(poolKey);

		InstancePool<T> instancePool = getInstancePool(poolKey.contextual());
		if (instancePool.releaseInstance(poolKey) && closed) {
			// The last instance in use was returned after the context was closed
			destroyDispatchInstance(poolKey.contextual());
		}
	}

	boolean hasAllocatedInstanceOf(Bean<?> bean) {
		return poolScope.get().getPoolKey(bean) != null;
	}

	<T> boolean mustDestroyBeanWhenCaught(Contextual<T> contextual, Throwable throwable) {
		return getInstancePool(contextual).mustDestroyBeanWhenCaught(throwable);
	}

	@Override
	public void destroy(Contextual<?> contextual) {
		PoolKey<?> poolKey = poolScope.get().getPoolKey(contextual);

		if (poolKey != null) {
			destroyInstance(poolKey);
		}
	}

	/**
	 * Closes the context: no instances are taken from the pools anymore, and threads waiting for one get an
	 * {@link IllegalStateException}.
	 */
	public void close() {
		closed = true;
		instancePools.values().forEach(InstancePool::close);
	}

	/**
	 * Waits until no pooled instance is in use, at most until the given deadline.
	 *
	 * @param deadline the deadline, as {@link System#nanoTime()}
	 * @return whether no pooled instance is in use
	 */
	public boolean awaitIdle(long deadline) {
		try {
			while (!instancePools.values().stream().allMatch(InstancePool::isIdle)) {
				if (System.nanoTime() >= deadline) {
					return false;
				}

				Thread.sleep(10);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		return true;
	}

	/**
	 * Destroys the instances that aren't in use: those in the pools, and the dispatch instances of beans none of whose
	 * instances are in use. Instances in use are destroyed when they're returned, as is the dispatch instance with
	 * the last of them. Called when the application stops, after {@link #close()}.
	 *
	 * @return the number of instances still in use
	 */
	public int destroyIdle() {
		int inUse = 0;

		for (InstancePool<?> instancePool : instancePools.values()) {
			int leased = instancePool.destroyIdle();
			if (leased == 0) {
				destroyDispatchInstance(instancePool.contextual);
			}
			inUse += leased;
		}

		// Beans whose pool was never used only have a dispatch instance
		for (Contextual<?> contextual : dispatchInstances.keySet()) {
			if (!instancePools.containsKey(contextual)) {
				destroyDispatchInstance(contextual);
			}
		}

		return inUse;
	}

	private void destroyDispatchInstance(Contextual<?> contextual) {
		LazyInstance<?> dispatchInstance = dispatchInstances.remove(contextual);
		if (dispatchInstance != null) {
			dispatchInstance.destroy();
		}
	}

	private <T> void destroyInstance(PoolKey<T> poolKey) {
		getInstancePool(poolKey.contextual()).destroyInstance(poolKey);
	}

	@SuppressWarnings("unchecked")
	private <T> InstancePool<T> getInstancePool(Contextual<T> contextual) {
		InstancePool<T> instancePool = (InstancePool<T>) instancePools.computeIfAbsent(contextual, c -> new InstancePool<>(contextual, getPoolSettings(contextual)));

		if (closed) {
			// E.g. a pool that's first used while the application stops
			instancePool.close();
		}

		return instancePool;
	}

	private static Pooled getPoolSettings(Contextual<?> contextual) {
		if (contextual instanceof Bean<?> bean) {
			Pooled pooled = findPooled(bean.getBeanClass().getAnnotations(), new HashSet<>());
			if (pooled != null) {
				return pooled;
			}
		}

		// E.g. the scope was added by an extension
		return Pooled.Literal.INSTANCE;
	}

	private static Pooled findPooled(Annotation[] annotations, Set<Class<?>> visitedStereotypes) {
		for (Annotation annotation : annotations) {
			if (annotation instanceof Pooled pooled) {
				return pooled;
			}

			Class<? extends Annotation> annotationType = annotation.annotationType();
			if (annotationType.isAnnotationPresent(Stereotype.class) && visitedStereotypes.add(annotationType)) {
				Pooled pooled = findPooled(annotationType.getAnnotations(), visitedStereotypes);
				if (pooled != null) {
					return pooled;
				}
			}
		}

		return null;
	}

	private static class InstancePool<T> {

		private final Contextual<T> contextual;
		private final Pooled poolSettings;

		private final Map<PoolKey<T>, LazyInstance<T>> instances = new ConcurrentHashMap<>();
		private final BlockingDeque<PoolKey<T>> freeInstanceKeys = new LinkedBlockingDeque<>();

		/** Taken by a thread waiting for an instance when the pool is closed; it puts it back for the next one */
		private final PoolKey<T> closedKey;

		private final AtomicInteger leased = new AtomicInteger();
		private boolean closed;

		InstancePool(Contextual<T> contextual, Pooled poolSettings) {
			this.contextual = contextual;
			this.poolSettings = poolSettings;

			IntStream.range(0, poolSettings.maxNumberOfInstances())
			         .mapToObj(i -> new PoolKey<>(contextual, i))
			         .forEach(freeInstanceKeys::add);
			closedKey = new PoolKey<>(contextual, -1);
		}

		T getInstance(PoolKey<T> poolKey) {
			if (!poolKey.contextual().equals(contextual)) {
				throw new IllegalArgumentException();
			}

			LazyInstance<T> instance = instances.get(poolKey);

			if (instance != null) {
				return instance.get();
			}

			return null;
		}

		T getInstance(PoolKey<T> poolKey, CreationalContext<T> context) {
			if (!poolKey.contextual().equals(contextual)) {
				throw new IllegalArgumentException();
			}

			return instances.computeIfAbsent(poolKey, key -> new LazyInstance<>(contextual)).get(context);
		}

		PoolKey<T> allocateInstance() {
			try {
				PoolKey<T> poolKey = freeInstanceKeys.poll(poolSettings.instanceLockTimeout(), poolSettings.instanceLockTimeoutUnit());

				if (poolKey == null) {
					// Unable to allocate an instance within the configured timeout
					throw new PoolLockTimeoutException();
				}

				synchronized (this) {
					if (closed) {
						if (poolKey != closedKey) {
							// Taken just before the pool was closed, so destroyIdle() may have missed its instance
							destroyInstance(poolKey);
						}

						// Back for the next waiting thread, which fails the same way
						freeInstanceKeys.addFirst(poolKey);
						throw new IllegalStateException("The pool of " + contextual + " is closed: the application is stopping");
					}

					leased.incrementAndGet();
				}

				return poolKey;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new UncheckedInterruptedException(e);
			}
		}

		/**
		 * Returns an instance to the pool or, when the pool is closed, destroys it.
		 *
		 * @return whether it was the last instance in use
		 */
		synchronized boolean releaseInstance(PoolKey<T> key) {
			if (!contextual.equals(key.contextual())) {
				throw new IllegalArgumentException();
			}

			if (closed) {
				destroyInstance(key);
			} else {
				freeInstanceKeys.addFirst(key);
			}

			return leased.decrementAndGet() == 0;
		}

		synchronized void close() {
			if (!closed) {
				closed = true;
				freeInstanceKeys.addFirst(closedKey);
			}
		}

		/**
		 * Destroys the instances that aren't in use.
		 *
		 * @return the number of instances in use
		 */
		synchronized int destroyIdle() {
			for (PoolKey<T> key : freeInstanceKeys) {
				if (key != closedKey) {
					destroyInstance(key);
				}
			}

			return leased.get();
		}

		void destroyInstance(PoolKey<T> key) {
			LazyInstance<T> instance = instances.remove(key);

			if (instance != null) {
				instance.destroy();
			}
		}

		boolean isIdle() {
			return leased.get() == 0;
		}

		boolean mustDestroyBeanWhenCaught(Throwable throwable) {
			for (Class throwableType: poolSettings.dontDestroyOn()) {
				if (throwableType.isInstance(throwable)) {
					return false;
				}
			}

			if (poolSettings.dontDestroyOn().length > 0 && poolSettings.destroyOn().length == 0) {
				return true;
			}

			for (Class throwableType: poolSettings.destroyOn()) {
				if (throwableType.isInstance(throwable)) {
					return true;
				}
			}

			return false;
		}
	}

	/**
	 * An instance, created on first use, with the creational context it was created with, so that it's destroyed with
	 * its dependent objects. It's created outside of any map update, so that its initialization can use other pooled
	 * beans.
	 */
	private static class LazyInstance<T> {

		private final Contextual<T> contextual;

		private T instance;
		private CreationalContext<T> creationalContext;

		LazyInstance(Contextual<T> contextual) {
			this.contextual = contextual;
		}

		synchronized T get() {
			return instance;
		}

		synchronized T get(CreationalContext<T> creationalContext) {
			if (instance == null) {
				instance = contextual.create(creationalContext);
				this.creationalContext = creationalContext;
			}

			return instance;
		}

		synchronized void destroy() {
			if (instance != null) {
				contextual.destroy(instance, creationalContext);
				instance = null;
				creationalContext = null;
			}
		}
	}
}
