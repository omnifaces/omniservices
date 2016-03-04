package org.omnifaces.cdi.pooled;

import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.spi.Contextual;

class PooledScope {

	private final Map<Contextual<?>, PoolKey<?>> allocatedPoolKeys = new HashMap<>();

	public void setPoolKey(PoolKey<?> pookKey) {
		allocatedPoolKeys.put(pookKey.getContextual(), pookKey);
	}

	public void removePoolKey(PoolKey<?> poolKey) {
		allocatedPoolKeys.remove(poolKey.getContextual());
	}

	public <T> PoolKey<T> getPoolKey(Contextual<T> contextual) {
		return (PoolKey<T>) allocatedPoolKeys.get(contextual);
	}
}
