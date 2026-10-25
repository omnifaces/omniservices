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

/**
 * Thrown when no instance of a {@link Pooled} bean becomes available within the bean's
 * {@link Pooled#instanceLockTimeout() instance lock timeout}.
 */
public class PoolLockTimeoutException extends RuntimeException {

	/**
	 * Creates the exception.
	 */
	public PoolLockTimeoutException() {
		super();
	}

	/**
	 * Creates the exception with a message.
	 *
	 * @param message the message
	 */
	public PoolLockTimeoutException(String message) {
		super(message);
	}

	/**
	 * Creates the exception with a message and a cause.
	 *
	 * @param message the message
	 * @param cause the cause
	 */
	public PoolLockTimeoutException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Creates the exception with a cause.
	 *
	 * @param cause the cause
	 */
	public PoolLockTimeoutException(Throwable cause) {
		super(cause);
	}
}
