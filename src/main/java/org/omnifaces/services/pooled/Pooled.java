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

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.concurrent.TimeUnit.MINUTES;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.NormalScope;

/**
 * Specify that a bean is to be pooled.
 * <p>
 * Pooled beans can have multiple instances that are shared between all threads of an application, but can never be
 * called by more than one thread at the same time. When a method call on a pooled bean is performed, an instance is
 * selected from the pool and is locked until the method call ends. When performing multiple method calls directly after
 * each other, multiple different beans may be used.
 * </p>
 */
@Inherited
@NormalScope
@Documented
@Target({TYPE, METHOD})
@Retention(RUNTIME)
public @interface Pooled {

	/**
	 * The maximum number of instances in the pool.
	 * @return the maximum number of instances
	 */
	int maxNumberOfInstances() default 10;

	/**
	 * The maximum amount of time to attempt to obtain a lock on an instance from the pool.
	 * @return the maximum amount of time to try to obtain a lock
	 */
	long instanceLockTimeout() default 5;

	/**
	 * The {@link TimeUnit} to use for the {@link #instanceLockTimeout()}
	 * @return the time unit to use
	 */
	TimeUnit instanceLockTimeoutUnit() default MINUTES;

	/**
	 * The types of {@link Throwable Throwables} which must cause the destruction of a pooled bean instance.
	 * <p>
	 * If the pooled bean instances throws any Throwable which is an instance of a type set in the destroyOn property,
	 * then the bean will be destroyed, except if the throwable is also an instance of a type set in the {@link
	 * #dontDestroyOn()} property.
	 * </p>
	 *
	 * @return The types of {@link Throwable Throwables} which must cause the destruction of a pooled bean instance
	 */
	Class[] destroyOn() default {};

	/**
	 * The types of {@link Throwable Throwables} which must not cause the destruction of a pooled bean instance.
	 * <p>
	 * If the {@link #destroyOn()} property is empty, but the dontDestroyOn property is not, then the bean will be
	 * destroyed for any Throwable that isn't an instance of a type in the dontDestroyOn property. When the {@link #destroyOn()} property is not empty, then the dontDestroyOn property takes precedence. If a pooled bean instance throws a throwable which is an instance of a type in both the destroyOn and dontDestroyOn properties, then the bean will not be destroyed.
	 * </p>
	 *
	 * @return The types of {@link Throwable Throwables} which must not cause the destruction of a pooled bean instance.
	 */
	Class[] dontDestroyOn() default {};

}
