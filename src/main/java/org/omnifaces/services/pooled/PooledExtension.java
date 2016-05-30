package org.omnifaces.services.pooled;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessBean;

import org.omnifaces.services.util.AnnotatedTypeWrapper;
import org.omnifaces.utils.annotation.AnnotationUtils;

public class PooledExtension implements Extension {

	private final PooledContext pooledContext = new PooledContext();

	public void beforeBeanDiscovery(@Observes BeforeBeanDiscovery beforeBeanDiscovery) {
		beforeBeanDiscovery.addScope(Pooled.class, true, false);
	}

	public <T> void processAnnotatedType(@Observes ProcessAnnotatedType<T> processAnnotatedType) {

		if (processAnnotatedType.getAnnotatedType().isAnnotationPresent(Pooled.class)) {
			AnnotatedType<T> annotatedType = processAnnotatedType.getAnnotatedType();
			AnnotatedTypeWrapper<T> wrapper = new AnnotatedTypeWrapper<>(annotatedType);

			wrapper.addAnnotation(AnnotationUtils.createAnnotationInstance(PooledScopeEnabled.class));

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
