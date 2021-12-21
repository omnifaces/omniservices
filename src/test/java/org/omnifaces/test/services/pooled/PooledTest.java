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
package org.omnifaces.test.services.pooled;

import static org.jboss.shrinkwrap.api.ShrinkWrap.create;
import static org.jboss.shrinkwrap.api.asset.EmptyAsset.INSTANCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.enterprise.inject.spi.Extension;
import jakarta.inject.Inject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.omnifaces.services.pooled.PooledExtension;
import org.omnifaces.test.services.pooled.testing.DependencyInjectionArquillianExtension;

@ExtendWith(ArquillianExtension.class)
@ExtendWith(DependencyInjectionArquillianExtension.class)
@DisplayName("@Pooled")
public class PooledTest {

	@Deployment
	public static Archive<?> createDeployment() {
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
	@DisplayName("with a single instance configured will always returns the same instance.")
	public void bean_withASingleInstanceConfigured_willAlwaysCauseSameInstanceToBeUsed() {
		int identityHashCode = singleInstancePooledBean.getIdentityHashCode();

		assertEquals(identityHashCode, singleInstancePooledBean.getIdentityHashCode());
	}

	@Test
	@DisplayName(
			"with an invocation that throws an exception not in the destroyOn field does not destroy the instance.")
	public void bean_withInvocationThrowingANonDestroyingException_doesNotDestroyInstance() {
		int identityHashCode = singleInstancePooledBean.getIdentityHashCode();

		assertThrows(Exception.class, () -> singleInstancePooledBean.throwException(Exception::new));

		assertEquals(identityHashCode, singleInstancePooledBean.getIdentityHashCode());
	}

	@Test
	@DisplayName(
			"with an invocation that throws an exception explicitly listed in the dontDestroyOn field does not destroy the instance.")
	public void bean_withInvocationThrowingAnExplicitNonDestroyingException_doesNotDestroyInstance() {
		int identityHashCode = singleInstancePooledBean.getIdentityHashCode();

		assertThrows(IllegalArgumentException.class,
				() -> singleInstancePooledBean.throwException(IllegalArgumentException::new));

		assertEquals(identityHashCode, singleInstancePooledBean.getIdentityHashCode());
	}


	@Test
	@DisplayName(
			"with an invocation that throws an exception explicitly listed in the destroyOn field destroys the instance.")
	public void bean_withInvocationThrowingAnExplicitDestroyingException_destroysInstance() {
		int identityHashCode = singleInstancePooledBean.getIdentityHashCode();

		assertThrows(RuntimeException.class, () -> singleInstancePooledBean.throwException(RuntimeException::new));

		assertNotEquals(identityHashCode, singleInstancePooledBean.getIdentityHashCode());
	}
}
