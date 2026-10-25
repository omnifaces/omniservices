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

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.WARNING;

import java.util.logging.Logger;

import org.omnifaces.services.asynchronous.ExecutorBean;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.Destroyed;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeanManager;

/**
 * Stops OmniServices when the application stops, in two phases.
 * <ol>
 * <li>Before the application context is destroyed, no new asynchronous invocations are accepted, and those that
 * haven't started are cancelled. Asynchronous invocations that have started, and pooled instances in use, are waited
 * for, within a time limit. The system property {@value #TIMEOUT_PROPERTY} sets it in seconds; the default is
 * {@value #DEFAULT_TIMEOUT_SECONDS}.
 * <li>After the application context is destroyed, no instances are taken from the pools anymore. Pooled instances that
 * aren't in use are destroyed. Those still in use are destroyed when they're returned, so an instance is never
 * destroyed while one of its methods runs.
 * </ol>
 * So the {@code PreDestroy} methods of application scoped beans, such as EJB singletons mapped by OmniBeans, can still
 * use pooled beans, but not asynchronous methods. Weld, though, fires both events for a web application before it
 * destroys the application context (WELD-2282), so there they can't use pooled beans either.
 */
@Dependent
public class PooledContextShutdown {

	/** The system property with the time limit, in seconds */
	public static final String TIMEOUT_PROPERTY = "org.omnifaces.services.shutdown.timeout";

	/** The default time limit, in seconds */
	public static final long DEFAULT_TIMEOUT_SECONDS = 10;

	private static final Logger logger = Logger.getLogger(PooledContextShutdown.class.getName());

	/**
	 * Creates the observer. Used by CDI.
	 */
	public PooledContextShutdown() {
	}

	/**
	 * Stops the asynchronous invocations, and waits for those that have started and for pooled instances in use.
	 *
	 * @param event the event that the application context is about to be destroyed
	 * @param beanManager the bean manager
	 * @param executorBean the bean with the executor service for asynchronous methods
	 */
	public void stopAsynchronousInvocations(@Observes @BeforeDestroyed(ApplicationScoped.class) Object event, BeanManager beanManager, ExecutorBean executorBean) {
		long timeoutSeconds = Long.getLong(TIMEOUT_PROPERTY, DEFAULT_TIMEOUT_SECONDS);
		long deadline = System.nanoTime() + SECONDS.toNanos(timeoutSeconds);

		executorBean.shutdown(deadline);
		getPooledContext(beanManager).awaitIdle(deadline);
	}

	/**
	 * Closes the pools, and destroys the pooled instances.
	 *
	 * @param event the event that the application context was destroyed
	 * @param beanManager the bean manager
	 */
	public void destroyPooledInstances(@Observes @Destroyed(ApplicationScoped.class) Object event, BeanManager beanManager) {
		PooledContext pooledContext = getPooledContext(beanManager);
		pooledContext.close();

		int inUse = pooledContext.destroyIdle();
		if (inUse > 0) {
			logger.log(WARNING, inUse + " pooled instances still in use when the application stopped; they're destroyed when they're returned");
		}
	}

	private static PooledContext getPooledContext(BeanManager beanManager) {
		return (PooledContext) beanManager.getContext(Pooled.class);
	}
}
