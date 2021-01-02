/*
 * Copyright 2020 OmniFaces
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
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.IntStream;

import javax.enterprise.context.spi.AlterableContext;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;

/**
 * The {@link AlterableContext} implementation for {@link Pooled} beans.
 */
public class PooledContext implements AlterableContext {

	private final ThreadLocal<PooledScope> poolScope = ThreadLocal.withInitial(PooledScope::new);
	private final Map<Contextual<?>, InstancePool<?>> instancePools = new ConcurrentHashMap<>();

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

			return ((InstancePool<T>) instancePools.get(poolKey.contextual())).getInstance(poolKey, creationalContext);
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

			return ((InstancePool<T>) instancePools.get(poolKey.contextual())).getInstance(poolKey);
		}
		return null;
	}

	@Override
	public boolean isActive() {
		return true;
	}

	@SuppressWarnings("unchecked")
	<T> PoolKey<T> allocateBean(Contextual<T> contextual) {
		PoolKey<T> poolKey = ((InstancePool<T>) instancePools.get(contextual)).allocateInstance();

		poolScope.get().setPoolKey(poolKey);

		return poolKey;
	}

	@SuppressWarnings("unchecked")
	<T> void releaseBean(PoolKey<T> poolKey) {
		poolScope.get().removePoolKey(poolKey);

		((InstancePool<T>) instancePools.get(poolKey.contextual())).releaseInstance(poolKey);
	}

	boolean hasAllocatedInstanceOf(Bean<?> bean) {
		return poolScope.get().getPoolKey(bean) != null;
	}

	<T> void createInstancePool(Contextual<T> contextual, Pooled poolSettings) {
		instancePools.put(contextual, new InstancePool<>(contextual, poolSettings));
	}

	<T> boolean mustDestroyBeanWhenCaught(Contextual<T> contextual, Throwable throwable) {
		return instancePools.get(contextual).mustDestroyBeanWhenCaught(throwable);
	}

	@Override
	public void destroy(Contextual<?> contextual) {
		PoolKey<?> poolKey = poolScope.get().getPoolKey(contextual);

		if (poolKey != null) {
			destroyInstance(poolKey);
		}
	}

	private <T> void destroyInstance(PoolKey<T> poolKey) {
		@SuppressWarnings("unchecked")
		InstancePool<T> instancePool = (InstancePool<T>) instancePools.get(poolKey.contextual());

		instancePool.destroyInstance(poolKey);
	}

	private static class InstancePool<T> {

		private final Contextual<T> contextual;
		private final Pooled poolSettings;

		private final Map<PoolKey<T>, Instance<T>> instances = new ConcurrentHashMap<>();
		private final BlockingDeque<PoolKey<T>> freeInstanceKeys = new LinkedBlockingDeque<>();

		InstancePool(Contextual<T> contextual, Pooled poolSettings) {
			this.contextual = contextual;
			this.poolSettings = poolSettings;

			IntStream.range(0, poolSettings.maxNumberOfInstances())
			         .mapToObj(i -> new PoolKey<>(contextual, i))
			         .forEach(freeInstanceKeys::add);
		}

		T getInstance(PoolKey<T> poolKey) {
			if (!poolKey.contextual().equals(contextual)) {
				throw new IllegalArgumentException();
			}

			Instance<T> instance = instances.get(poolKey);

			if (instance != null) {
				return instance.getInstance();
			}

			return null;
		}

		T getInstance(PoolKey<T> poolKey, CreationalContext<T> context) {
			if (!poolKey.contextual().equals(contextual)) {
				throw new IllegalArgumentException();
			}

			return instances.computeIfAbsent(poolKey, key -> new Instance<>(contextual, context)).getInstance();
		}

		PoolKey<T> allocateInstance() {
			try {
				PoolKey<T> poolKey = freeInstanceKeys.poll(poolSettings.instanceLockTimeout(), poolSettings.instanceLockTimeoutUnit());

				if (poolKey == null) {
					// Unable to allocate an instance within the configured timeout
					throw new PoolLockTimeoutException();
				}

				return poolKey;
			} catch (InterruptedException e) {
				throw new UncheckedInterruptedException(e);
			}
		}

		void releaseInstance(PoolKey<T> key) {
			if (!contextual.equals(key.contextual())) {
				throw new IllegalArgumentException();
			}

			freeInstanceKeys.addFirst(key);
		}

		void destroyInstance(PoolKey<T> key) {
			Instance<T> instance = instances.remove(key);

			instance.destroy(contextual);
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

	private static class Instance<T> {

		private final T instance;
		private final CreationalContext<T> creationalContext;

		Instance(Contextual<T> contextual, CreationalContext<T> creationalContext) {
			this.instance = contextual.create(creationalContext);
			this.creationalContext = creationalContext;
		}

		T getInstance() {
			return instance;
		}

		CreationalContext<T> getCreationalContext() {
			return creationalContext;
		}

		void destroy(Contextual<T> contextual) {
			contextual.destroy(instance, creationalContext);
		}
	}
}
