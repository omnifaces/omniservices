package org.omnifaces.cdi.pooled;

import java.util.Objects;

import javax.enterprise.context.spi.Contextual;

final class PoolKey<T> {

	private final Contextual<T> contextual;
	private final int index;

	public PoolKey(Contextual<T> contextual, int index) {
		this.contextual = contextual;
		this.index = index;
	}

	public Contextual<T> getContextual() {
		return contextual;
	}

	public int getIndex() {
		return index;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		PoolKey poolKey = (PoolKey) o;
		return index == poolKey.index &&
				Objects.equals(contextual, poolKey.contextual);
	}

	@Override
	public int hashCode() {
		return Objects.hash(contextual, index);
	}
}
