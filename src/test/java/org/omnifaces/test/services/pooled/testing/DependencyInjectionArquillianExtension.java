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
package org.omnifaces.test.services.pooled.testing;

import java.util.Optional;
import java.util.function.Predicate;

import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.InjectionTarget;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

/**
 * JUnit extension to enable CDI based dependency injection in test instances when running in an Arquillian container.
 */
public class DependencyInjectionArquillianExtension implements TestInstancePostProcessor {

	private static final Predicate<ExtensionContext> isInsideArquillian =
			(context) -> context.getConfigurationParameter("insideArquillian").map(Boolean::parseBoolean).orElse(false);

	@Override
	public void postProcessTestInstance(Object testInstance, ExtensionContext extensionContext) {
		getCdi(extensionContext).ifPresent(cdi -> {
			var beanManager = cdi.getBeanManager();
			var testClassType = beanManager.createAnnotatedType(testInstance.getClass());
			var injectionTargetFactory = beanManager.getInjectionTargetFactory(testClassType);
			var injectionTarget = injectionTargetFactory.createInjectionTarget(null);

			inject(injectionTarget, beanManager, testInstance);
		});
	}

	@SuppressWarnings("unchecked")
	private static <T> void inject(InjectionTarget<T> injectionTarget, BeanManager beanManager, Object instance) {
		injectionTarget.inject((T) instance, beanManager.createCreationalContext(null));
	}

	private static Optional<CDI<Object>> getCdi(ExtensionContext context) {
		if (context.getConfigurationParameter("insideArquillian").map(Boolean::parseBoolean).orElse(false)) {
			return Optional.of(CDI.current());
		}

		return Optional.empty();
	}

}
