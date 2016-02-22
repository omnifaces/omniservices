package org.omnifaces.cdi.pooled;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.enterprise.context.spi.Contextual;

public class PooledScope {

	private final Map<Contextual<?>, PoolKey<?>> activePoolKeys;

	private Optional<Contextual<?>> currentContextual = Optional.empty();

	private final PooledScope parentScope;

	private PooledScope() {
		activePoolKeys = new HashMap<>();
		parentScope = null;
	}

	private PooledScope(PooledScope parentScope) {
		activePoolKeys = parentScope.activePoolKeys;
		this.parentScope = parentScope;
	}

	public static PooledScope newPooledScope(PooledScope parentScope) {
		if (parentScope == null) {
			return new PooledScope();
		}

		return new PooledScope(parentScope);
	}

	public <T> PoolKey<T> getPoolKey(Contextual<T> contextual) {
		return (PoolKey<T>) activePoolKeys.get(contextual);
	}

	public <T> void setCurrentPoolKey(PoolKey<T> poolKey) {
		if (currentContextual.isPresent()) {
			// TODO error message
			throw new IllegalArgumentException();
		}

		activePoolKeys.put(poolKey.getContextual(), poolKey);
		currentContextual = Optional.of(poolKey.getContextual());
	}

	public PooledScope cleanCurrentScope() {
		currentContextual.ifPresent(activePoolKeys::remove);
		return parentScope;
	}
}
