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
package org.omnifaces.services.test.plain;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;

import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Uses OmniServices without Jakarta Transactions (see PlainTest), and responds with the results.
 */
@WebServlet("/plain")
public class PlainServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Inject
    PlainPooled plainPooled;

    @Inject
    PlainService plainService;

    @Inject
    PlainLocked plainLocked;

    @Inject
    PlainAsync plainAsync;

    @Inject
    BeanManager beanManager;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        StringBuilder body = new StringBuilder();
        try {
            body.append(plainPooled.work()).append(" scope=").append(scope(PlainPooled.class)).append('\n');
            body.append(plainService.work()).append(" scope=").append(scope(PlainService.class)).append('\n');
            body.append(plainLocked.work()).append('\n');
            body.append(plainAsync.work(Thread.currentThread().getName()).get(30, SECONDS)).append('\n');
        } catch (Exception e) {
            body.append("failed: ").append(e).append('\n');
        }

        response.setContentType("text/plain");
        response.getWriter().write(body.toString());
    }

    private String scope(Class<?> beanClass) {
        return beanManager.resolve(beanManager.getBeans(beanClass)).getScope().getSimpleName();
    }
}
