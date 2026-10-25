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

import org.omnifaces.services.asynchronous.Asynchronous;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FireAndForget {

    @Inject
    Trace trace;

    @Asynchronous
    public void fire() {
        trace.add("fired thread=" + Trace.thread());
    }

    @Asynchronous
    public void fail() {
        trace.add("failing thread=" + Trace.thread());
        throw new IllegalStateException("Expected failure of a void asynchronous method");
    }
}
