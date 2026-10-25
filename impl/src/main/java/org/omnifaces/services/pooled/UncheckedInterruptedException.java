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

// TODO move to OmniUtils??
/**
 * Thrown when a thread is interrupted while it waits for an instance of a {@link Pooled} bean.
 */
public class UncheckedInterruptedException extends RuntimeException {

	/**
	 * Creates the exception with a message and a cause.
	 *
	 * @param message the message
	 * @param cause the cause, normally an {@link InterruptedException}
	 */
	public UncheckedInterruptedException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Creates the exception with a cause.
	 *
	 * @param cause the cause, normally an {@link InterruptedException}
	 */
	public UncheckedInterruptedException(Throwable cause) {
		super(cause);
	}

}
