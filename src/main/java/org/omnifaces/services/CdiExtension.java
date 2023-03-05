/*
 * Copyright 2021, 2023 OmniFaces
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
package org.omnifaces.services;

import static org.omnifaces.utils.annotation.Annotations.createAnnotationInstance;

import org.omnifaces.services.asynchronous.AsynchronousInterceptor;
import org.omnifaces.services.asynchronous.ExecutorBean;
import org.omnifaces.services.lock.Lock;
import org.omnifaces.services.lock.LockInterceptor;
import org.omnifaces.services.pooled.Pooled;
import org.omnifaces.services.pooled.PooledContext;
import org.omnifaces.services.pooled.PooledScopeEnabled;
import org.omnifaces.services.util.AnnotatedTypeWrapper;

import jakarta.ejb.Asynchronous;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessBean;

public class CdiExtension implements Extension {

	private final PooledContext pooledContext = new PooledContext();

	public void beforeBeanDiscovery(@Observes BeforeBeanDiscovery beforeBeanDiscovery, BeanManager beanManager) {
	    addAnnotatedTypes(beforeBeanDiscovery, beanManager,
            Asynchronous.class,
            AsynchronousInterceptor.class,

            Lock.class,
            LockInterceptor.class,

            ExecutorBean.class);

		beforeBeanDiscovery.addScope(
	        Pooled.class, true, false);
	}

	public <T> void processAnnotatedType(@Observes ProcessAnnotatedType<T> processAnnotatedType) {
		if (processAnnotatedType.getAnnotatedType().isAnnotationPresent(Pooled.class)) {
			AnnotatedType<T> annotatedType = processAnnotatedType.getAnnotatedType();
			AnnotatedTypeWrapper<T> wrapper = new AnnotatedTypeWrapper<T>(annotatedType);

			wrapper.addAnnotation(createAnnotationInstance(PooledScopeEnabled.class));

			processAnnotatedType.setAnnotatedType(wrapper);
		}

	}

	public <T> void processBean(@Observes ProcessBean<T> processBean) {
		Pooled pooled = processBean.getAnnotated().getAnnotation(Pooled.class);

		if (pooled != null) {
			pooledContext.createInstancePool(processBean.getBean(), pooled);
		}
	}

	public void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery) {
		afterBeanDiscovery.addContext(pooledContext);
	}

	 public static void addAnnotatedTypes(BeforeBeanDiscovery beforeBean, BeanManager beanManager, Class<?>... types) {
	        for (Class<?> type : types) {
	            beforeBean.addAnnotatedType(beanManager.createAnnotatedType(type), "Omniservices " + type.getName());
	        }
	    }
}
