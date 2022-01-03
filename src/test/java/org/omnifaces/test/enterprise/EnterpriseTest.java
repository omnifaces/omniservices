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
package org.omnifaces.test.enterprise;

import static org.jboss.shrinkwrap.api.ShrinkWrap.create;
import static org.jboss.shrinkwrap.api.asset.EmptyAsset.INSTANCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.omnifaces.services.pooled.PooledExtension;
import org.omnifaces.test.services.pooled.testing.DependencyInjectionArquillianExtension;

import jakarta.ejb.EJB;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.inject.Inject;

@ExtendWith(ArquillianExtension.class)
@ExtendWith(DependencyInjectionArquillianExtension.class)
@DisplayName("Enterprise Beans - Async")
public class EnterpriseTest {

    @Inject
    private AsyncBean asyncBean;

    @EJB
    private SingletonBean singletonBean;

    @Deployment
    public static WebArchive createDeployment() {
        return create(WebArchive.class)
                .addAsManifestResource(INSTANCE, "beans.xml")
                .addClasses(AsyncBean.class, SingletonBean.class)
                .addAsLibraries(create(JavaArchive.class)
                        .addAsManifestResource(INSTANCE, "beans.xml")
                        .addAsServiceProvider(Extension.class, PooledExtension.class)
                        .addPackages(true, "org.omnifaces.services.asynchronous")
                        .addPackages(true, "org.omnifaces.services.pooled")
                        .addPackages(true, "org.omnifaces.services.util")
                )
                .addAsLibraries(Maven.resolver()
                        .loadPomFromFile("pom.xml")
                        .resolve("org.omnifaces:omniutils")
                        .withoutTransitivity()
                        .asSingleFile())
                        ;
    }

    @Test
    public void testAsync() throws ExecutionException, InterruptedException {
        Future<Integer> resultFuture = asyncBean.multiply(3, 4);
        assertFalse(resultFuture.isDone());

        while (!resultFuture.isDone()) {
            Thread.sleep(10);
        }

        assertEquals(12, resultFuture.get());
    }

    @Test
    @Order(1)
    public void testSingleton1() throws ExecutionException, InterruptedException {
        singletonBean.addValue("foo");

        assertEquals("foo", singletonBean.getAccumulatedValues());
    }

    @Test
    @Order(2)
    public void testSingleton2() throws ExecutionException, InterruptedException {
        singletonBean.addValue(" bar");

        assertEquals("foo bar", singletonBean.getAccumulatedValues());
    }

}
