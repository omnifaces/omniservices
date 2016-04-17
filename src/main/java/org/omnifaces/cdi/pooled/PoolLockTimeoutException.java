package org.omnifaces.cdi.pooled;

public class PoolLockTimeoutException extends RuntimeException {

	public PoolLockTimeoutException() {
		super();
	}

	public PoolLockTimeoutException(String message) {
		super(message);
	}

	public PoolLockTimeoutException(String message, Throwable cause) {
		super(message, cause);
	}

	public PoolLockTimeoutException(Throwable cause) {
		super(cause);
	}
}
