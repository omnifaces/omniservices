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
package org.omnifaces.test.services.pooled;

import static org.jboss.shrinkwrap.api.ShrinkWrap.create;
import static org.jboss.shrinkwrap.api.asset.EmptyAsset.INSTANCE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import javax.enterprise.inject.spi.Extension;
import javax.inject.Inject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.omnifaces.services.pooled.PooledExtension;


@RunWith(Arquillian.class)
public class PooledTest {

	@Deployment
	public static Archive<?>  createDeployment() {
		return create(WebArchive.class)
                 .addAsManifestResource(INSTANCE, "beans.xml")
                 .addClasses(SingleInstancePooledBean.class)
                 .addAsLibraries(create(JavaArchive.class)
                         .addAsManifestResource(INSTANCE, "beans.xml")
                         .addAsServiceProvider(Extension.class, PooledExtension.class)
                         .addPackages(true, "org.omnifaces.services.pooled")
                         .addPackages(true, "org.omnifaces.services.util")
                         )
                 .addAsLibraries(Maven.resolver()
                         .loadPomFromFile("pom.xml")
                         .resolve("org.omnifaces:omniutils")
                         .withoutTransitivity()
                         .asSingleFile());
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
