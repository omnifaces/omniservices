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
package org.omnifaces.test.enterprise;

import static java.util.logging.Level.SEVERE;

import java.util.concurrent.Future;
import java.util.logging.Logger;

import jakarta.ejb.AsyncResult;
import jakarta.ejb.Asynchronous;
import jakarta.ejb.Stateless;

@Stateless
public class AsyncBean {
    private static final Logger LOGGER = Logger.getLogger(AsyncBean.class.getName());

    @Asynchronous
    public Future<Integer> multiply(int number1, int number2) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ex) {
            Thread.interrupted();
            LOGGER.log(SEVERE, null, ex);
        }

        return new AsyncResult<>(number1 * number2);
    }

}