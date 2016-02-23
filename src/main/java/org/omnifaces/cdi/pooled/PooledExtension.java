package org.omnifaces.cdi.pooled;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;

public class PooledExtension implements Extension {


	public void beforeBeanDiscovery(@Observes BeforeBeanDiscovery beforeBeanDiscovery) {
		beforeBeanDiscovery.addScope(Pooled.class, true, false);
	}

	public void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery) {
		afterBeanDiscovery.addContext(new PooledContext());
	}
}
