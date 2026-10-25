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

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Uses a pooled bean when it's destroyed, as an EJB singleton may. Whether the pool still accepts that depends on the
 * container (see ShutdownTest).
 */
@ApplicationScoped
public class PreDestroyCaller {

    @Inject
    SinglePoolBean singlePoolBean;

    /**
     * Creates the instance, so that it's destroyed when the application stops.
     */
    public void activate() {
    }

    @PreDestroy
    void callPooledBean() {
        ShutdownObserver.call("pre-destroy acquisition", singlePoolBean::ping, IllegalStateException.class);
    }
}
