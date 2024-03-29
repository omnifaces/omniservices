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
package org.omnifaces.services.pooled;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.omnifaces.services.asynchronous.Asynchronous;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.interceptor.InterceptorBinding;

@InterceptorBinding
@Documented
@Inherited
@Retention(RUNTIME)
@Target({TYPE, METHOD})
public @interface PooledScopeEnabled {
    
    /**
     * Supports inline instantiation of the {@link PooledScopeEnabled} annotation.
     *
     */
    public static final class Literal extends AnnotationLiteral<PooledScopeEnabled> implements PooledScopeEnabled {
        private static final long serialVersionUID = 1L;

        /**
         * Instance of the {@link Asynchronous} annotation.
         */
        public static final Literal INSTANCE = new Literal();
    }
}
