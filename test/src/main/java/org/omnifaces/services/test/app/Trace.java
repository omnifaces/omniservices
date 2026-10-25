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
package org.omnifaces.services.test.app;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Records what happens, and coordinates threads with latches.
 */
@ApplicationScoped
public class Trace {

    private final List<String> events = new ArrayList<>();
    private final ConcurrentHashMap<String, CountDownLatch> latches = new ConcurrentHashMap<>();

    public synchronized void add(String event) {
        events.add(event);
    }

    public synchronized List<String> events() {
        return new ArrayList<>(events);
    }

    public synchronized void clear() {
        events.clear();
    }

    public boolean await(Predicate<String> event, long millis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            if (events().stream().anyMatch(event)) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    public void newLatch(String name, int count) {
        latches.put(name, new CountDownLatch(count));
    }

    public long count(String name) {
        return latches.get(name).getCount();
    }

    public void countDown(String name) {
        latches.get(name).countDown();
    }

    public boolean await(String name, long millis) {
        try {
            return latches.get(name).await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Records an event in the file named by the system property {@code omniservices.test.events}, if set. For events
     * that happen while the application stops, which a test can only check afterwards.
     */
    /**
     * Returns the events recorded with {@link #record(String)} so far.
     */
    public static List<String> recorded() {
        String file = System.getProperty("omniservices.test.events");
        try {
            return file == null || !Files.exists(Path.of(file)) ? List.of() : Files.readAllLines(Path.of(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void record(String line) {
        String file = System.getProperty("omniservices.test.events");
        if (file == null) {
            return;
        }

        try {
            Files.writeString(Path.of(file), line + "\n", CREATE, APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A transaction key without spaces (Transact's toString has them).
     */
    public static String key(Object transactionKey) {
        return String.valueOf(transactionKey).replace(' ', '_');
    }

    public static String thread() {
        return Thread.currentThread().getName();
    }

    public static String id(Object object) {
        return Integer.toHexString(System.identityHashCode(object));
    }
}
