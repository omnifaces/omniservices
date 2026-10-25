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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.omnifaces.services.test.app.ProbeServlet;

/**
 * Runs the probes of {@link ProbeServlet} in the deployed application. Each probe checks one behavior of the pooled
 * scope, locks and asynchronous methods, server-side, and responds with PASS or FAIL, the reasons and a trace.
 */
@ExtendWith(ArquillianExtension.class)
@RunAsClient
@Order(1)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OmniServicesTest {

    @ArquillianResource
    private URL base;

    @Deployment(testable = false)
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class, "omniservices-test.war")
                .addPackage(ProbeServlet.class.getPackage())
                .addAsWebInfResource(new File("src/main/webapp/WEB-INF/beans.xml"), "beans.xml")
                .addAsResource(new File("src/main/resources/META-INF/microprofile-config.properties"), "META-INF/microprofile-config.properties");
    }

    @BeforeAll
    static void deleteShutdownEvents() throws Exception {
        Files.deleteIfExists(Path.of(System.getProperty("omniservices.test.events")));
    }

    // An asynchronous call is submitted at once while the sole pooled instance is busy
    @Test @Order(1) void busyPool() throws Exception { probe("busyPool"); }

    // An asynchronous call is submitted at once while the bean's write lock is held
    @Test @Order(2) void heldLock() throws Exception { probe("heldLock"); }

    // On the worker: dispatch instance, then pooled instance, lock, transaction, method, in that order
    @Test @Order(3) void pooledTrace() throws Exception { probe("pooledTrace"); }
    @Test @Order(4) void singletonTrace() throws Exception { probe("singletonTrace"); }

    // An asynchronous method calling itself asynchronously gets another thread and instance
    @Test @Order(5) void nested() throws Exception { probe("nested"); }

    // A returned future that isn't complete yet is resolved before the transaction ends and the instance is released
    @Test @Order(6) void incomplete() throws Exception { probe("incomplete"); }

    // Cancelling before the start prevents the invocation; after, it only sets wasCancelCalled
    @Test @Order(7) void cancelQueued() throws Exception { probe("cancelQueued"); }
    @Test @Order(8) void cancelRunning() throws Exception { probe("cancelRunning"); }

    // @Pooled through a stereotype: @Service, and one with pool settings
    @Test @Order(9) void stereotype() throws Exception { probe("stereotype"); }
    @Test @Order(10) void stereotypeSettings() throws Exception { probe("stereotypeSettings"); }

    @Test @Order(11) void voidMethods() throws Exception { probe("voidMethods"); }
    @Test @Order(12) void executor() throws Exception { probe("executor"); }

    // The caller's transaction isn't used by an asynchronous method
    @Test @Order(13) void callerTransaction() throws Exception { probe("callerTransaction"); }

    // A full executor rejects the call at the caller, without leaking a pooled instance (bounded executors only)
    @Test @Order(14) void rejection() throws Exception { probe("rejection"); }

    // Leaves work outstanding for the shutdown, which ShutdownTest checks
    @Test @Order(15) void interruptedResult() throws Exception { probe("interruptedResult"); }
    @Test @Order(16) void loopback() throws Exception { probe("loopback"); }
    // Last: it leaves work outstanding for ShutdownTest
    @Test @Order(17) void outstandingWork() throws Exception { probe("outstandingWork"); }

    private void probe(String name) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(base + "probe?name=" + name)).build(),
            HttpResponse.BodyHandlers.ofString());

        String body = response.body();
        System.out.println("=== " + name + " (" + response.statusCode() + ")\n" + body);
        assertTrue(body.startsWith("PASS"), body);
    }
}
