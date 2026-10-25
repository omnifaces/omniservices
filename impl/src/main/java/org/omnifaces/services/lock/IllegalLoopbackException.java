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
package org.omnifaces.services.lock;

/**
 * Thrown when a thread that holds the read lock of a {@link Lock} bean instance calls a method of it that needs the
 * write lock, directly or through other beans. A read lock isn't upgraded, so the thread would wait for itself.
 */
public class IllegalLoopbackException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message the message
     */
    public IllegalLoopbackException(String message) {
        super(message);
    }
}
