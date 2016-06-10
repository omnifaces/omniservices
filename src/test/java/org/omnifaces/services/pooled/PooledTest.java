/*
 * Copyright 2016 OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
