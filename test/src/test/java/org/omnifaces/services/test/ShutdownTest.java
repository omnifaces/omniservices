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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Checks what happened when the application of {@link OmniServicesTest} stopped, as the application recorded it in a
 * file (see {@code Trace.record}). Its last probe left work outstanding; the shutdown time limit is 2 seconds.
 */
@Order(2)
public class ShutdownTest {

    @Test
    void shutdown() throws Exception {
        Path events = Path.of(System.getProperty("omniservices.test.events"));

        // The outstanding invocation ends about 5 seconds after the shutdown began, possibly after the undeployment
        List<String> read = List.of();
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            read = Files.exists(events) ? Files.readAllLines(events) : List.of();
            if (read.stream().filter(line -> line.startsWith("destroyed PooledService")).count() == 2) {
                break;
            }
            Thread.sleep(100);
        }
        List<String> lines = read;

        assertTrue(lines.contains("before-destroyed application scope"), "Application scope not destroyed: " + lines);

        // New asynchronous invocations were refused as soon as the application stopped, and queued ones never ran
        assertTrue(lines.contains("submission rejected"), "Submission not rejected: " + lines);
        assertTrue(lines.contains("late submission rejected"), "Late submission not rejected: " + lines);
        assertFalse(lines.contains("queued ran"), "Queued invocation ran: " + lines);

        // Pooled beans could be used until the application context was destroyed
        assertTrue(lines.contains("acquisition accepted"), "Acquisition not accepted: " + lines);
        assertTrue(lines.contains("late acquisition rejected"), "Late acquisition not rejected: " + lines);

        // On Quarkus, also by the PreDestroy methods of application scoped beans. Weld fires @Destroyed for a web
        // application before it destroys the application context, so on Piranha the pools were closed by then.
        String preDestroyAcquisition = System.getProperty("omniservices.test.preDestroyAcquisition");
        assertTrue(lines.contains("pre-destroy acquisition " + preDestroyAcquisition), "Pre-destroy acquisition not " + preDestroyAcquisition + ": " + lines);

        // The dispatch instance and the one pooled instance (maxNumberOfInstances = 1) of PooledService
        assertEquals(2, lines.stream().filter(line -> line.startsWith("destroyed PooledService")).count(), "PooledService instances destroyed: " + lines);

        // The instance that was still in use when the time limit passed was destroyed after it was done
        String instance = lines.stream().filter(line -> line.startsWith("outstanding started ")).findFirst()
                .orElseThrow(() -> new AssertionError("No outstanding invocation: " + lines)).substring("outstanding started ".length());
        int finished = lines.indexOf("outstanding finished " + instance);
        int destroyed = indexOfPrefix(lines, "destroyed PooledService " + instance);
        assertTrue(finished >= 0 && destroyed > finished, "Instance " + instance + " not destroyed after it was done: " + lines);
    }

    private static int indexOfPrefix(List<String> lines, String prefix) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }
}
