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
package org.omnifaces.services.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.omnifaces.services.test.plain.PlainServlet;

/**
 * Checks that OmniServices works without a Jakarta Transactions implementation: on Quarkus without
 * quarkus-narayana-jta (the API itself always comes with ArC). It runs in a separate execution, with other Quarkus
 * dependencies (see the POM).
 */
@ExtendWith(ArquillianExtension.class)
@RunAsClient
public class PlainTest {

    @ArquillianResource
    URL base;

    @Deployment(testable = false)
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class, "omniservices-plain.war")
                .addPackage(PlainServlet.class.getPackage())
                .addAsWebInfResource(new File("src/main/webapp/WEB-INF/beans.xml"), "beans.xml");
    }

    @Test
    void withoutTransactions() throws Exception {
        String body = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(base + "plain")).build(),
            HttpResponse.BodyHandlers.ofString()).body();

        System.out.println("=== withoutTransactions\n" + body);
        assertEquals("pooled scope=Pooled\nservice scope=Pooled\nlocked\nasync\n", body);
    }
}
