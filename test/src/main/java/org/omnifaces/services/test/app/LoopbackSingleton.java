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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.omnifaces.services.lock.Lock.Type.READ;
import static org.omnifaces.services.lock.Lock.Type.WRITE;

import org.omnifaces.services.lock.IllegalLoopbackException;
import org.omnifaces.services.lock.Lock;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * A singleton with read locks, and write locks on some methods, whose methods call it again through another bean, so
 * through its client proxy. (Weld doesn't intercept calls on this, but does intercept calls through a client proxy,
 * also those that come back to an instance.) The locks time out after 2 seconds.
 */
@ApplicationScoped
@Lock(type = READ, accessTimeout = 2, unit = SECONDS)
public class LoopbackSingleton {

    @Inject
    LoopbackRelay relay;

    public String read() {
        return "read";
    }

    @Lock(type = WRITE, accessTimeout = 2, unit = SECONDS)
    public String write() {
        return "written";
    }

    public String readThenWrite() {
        try {
            return relay.write();
        } catch (IllegalLoopbackException e) {
            return "refused";
        }
    }

    public String readThenRead() {
        return relay.read();
    }

    @Lock(type = WRITE, accessTimeout = 2, unit = SECONDS)
    public String writeThenWrite() {
        return relay.write();
    }

    @Lock(type = WRITE, accessTimeout = 2, unit = SECONDS)
    public String writeThenRead() {
        return relay.read();
    }
}
