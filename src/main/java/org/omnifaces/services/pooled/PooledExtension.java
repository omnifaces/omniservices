/*
 * Copyright 2020 OmniFaces
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

import static org.omnifaces.utils.annotation.Annotations.createAnnotationInstance;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessBean;

import org.omnifaces.services.util.AnnotatedTypeWrapper;

public class PooledExtension implements Extension {

	private final PooledContext pooledContext = new PooledContext();

	public void beforeBeanDiscovery(@Observes BeforeBeanDiscovery beforeBeanDiscovery) {
		beforeBeanDiscovery.addScope(Pooled.class, true, false);
	}

	public <T> void processAnnotatedType(@Observes ProcessAnnotatedType<T> processAnnotatedType) {

		if (processAnnotatedType.getAnnotatedType().isAnnotationPresent(Pooled.class)) {
			AnnotatedType<T> annotatedType = processAnnotatedType.getAnnotatedType();
			AnnotatedTypeWrapper<T> wrapper = new AnnotatedTypeWrapper<>(annotatedType);

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
}
