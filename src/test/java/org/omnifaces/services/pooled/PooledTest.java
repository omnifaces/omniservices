package org.omnifaces.services.pooled;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import javax.enterprise.inject.spi.Extension;
import javax.inject.Inject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.omnifaces.services.util.AnnotatedMethodWrapper;
import org.omnifaces.services.util.AnnotatedTypeWrapper;

@RunWith(Arquillian.class)
public class PooledTest {

	@Deployment
	public static JavaArchive createDeployment() {
		return ShrinkWrap.create(JavaArchive.class)
		                 .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
		                 .addClasses(Pooled.class, PooledContext.class, PooledExtension.class, PooledScope.class,
		                             PooledScopeEnabled.class, PooledScopeInterceptor.class, PoolKey.class,
		                             PoolLockTimeoutException.class, UncheckedInterruptedException.class,
		                             AnnotatedMethodWrapper.class, AnnotatedTypeWrapper.class,
		                             SingleInstancePooledBean.class)
		                 .addAsServiceProvider(Extension.class, PooledExtension.class);
	}

	@Inject
	private SingleInstancePooledBean singleInstancePooledBean;

	@Test
	public void testPooledBean() {
		int identityHashCode = singleInstancePooledBean.getIdentityHashCode();

		assertEquals(identityHashCode, singleInstancePooledBean.getIdentityHashCode());
	}

	@Test
	public void testDestroyOn() {
		int identityHashCode = singleInstancePooledBean.getIdentityHashCode();

		try {
			singleInstancePooledBean.throwException(Exception::new);
		}
		catch (Exception e) {
		}

		assertEquals(identityHashCode, singleInstancePooledBean.getIdentityHashCode());

		try {
			singleInstancePooledBean.throwException(IllegalArgumentException::new);
		}
		catch (IllegalArgumentException e) {
		}

		assertEquals(identityHashCode, singleInstancePooledBean.getIdentityHashCode());

		try {
			singleInstancePooledBean.throwException(RuntimeException::new);
		}
		catch (RuntimeException e) {
		}

		assertNotEquals(identityHashCode, singleInstancePooledBean.getIdentityHashCode());
	}
}
