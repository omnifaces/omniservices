package org.omnifaces.services.pooled;

import java.util.function.Supplier;

@Pooled(destroyOn = RuntimeException.class, dontDestroyOn = IllegalArgumentException.class, maxNumberOfInstances = 1)
public class SingleInstancePooledBean {

	public int getIdentityHashCode() {
		return System.identityHashCode(this);
	}

	public <E extends Exception> void throwException(Supplier<E> exceptionSupplier) throws E {
		throw exceptionSupplier.get();
	}
}
