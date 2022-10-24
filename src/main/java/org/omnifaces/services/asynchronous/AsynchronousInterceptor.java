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
package org.omnifaces.services.asynchronous;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

import java.io.Serializable;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Interceptor
@Asynchronous
@Priority(PLATFORM_BEFORE + 1)
public class AsynchronousInterceptor implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private ExecutorBean executorBean;

    @Inject
    private Provider<RequestContextController> requestContextControllerProvider;

    @AroundInvoke
    public Object submitAsync(InvocationContext ctx) throws Exception {
        return new FutureDelegator(executorBean.getExecutorService().submit(() -> {
            RequestContextController requestContextController = requestContextControllerProvider.get();
            requestContextController.activate();
            try {
                return ctx.proceed();
            } finally {
                requestContextController.deactivate();
            }
        }));
    }

}