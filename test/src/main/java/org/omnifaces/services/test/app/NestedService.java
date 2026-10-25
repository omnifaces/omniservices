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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.omnifaces.services.asynchronous.Asynchronous;
import org.omnifaces.services.pooled.Pooled;

import jakarta.inject.Inject;

/**
 * A pool of two instances, with an asynchronous method that calls itself asynchronously.
 */
@Pooled(maxNumberOfInstances = 2, instanceLockTimeout = 30, instanceLockTimeoutUnit = SECONDS)
public class NestedService {

    @Inject
    Trace trace;

    @Inject
    NestedService self;

    @Asynchronous
    public Future<String> nested(int depth) throws Exception {
        trace.add("method nested depth=" + depth + " thread=" + Trace.thread() + " target=" + Trace.id(this));

        if (depth == 0) {
            return CompletableFuture.completedFuture("inner@" + Trace.thread());
        }

        Future<String> inner = self.nested(depth - 1);
        trace.add("submitted inner, done=" + inner.isDone());

        return CompletableFuture.completedFuture("outer@" + Trace.thread() + " -> " + inner.get(30, SECONDS));
    }
}
