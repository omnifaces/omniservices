/*
 * Copyright 2021, 2023 OmniFaces
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
package org.omnifaces.services.util;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.interceptor.InvocationContext;

public class CdiUtils {

    public static <T> T getInterceptorBindingAnnotation(InvocationContext invocationContext, BeanManager beanManager, Bean<?> interceptedBean, Class<T> type) {
        Optional<T> optionalAnnotation = getAnnotation(beanManager, interceptedBean.getBeanClass(), (Class) type);
        if (optionalAnnotation.isPresent()) {
            return optionalAnnotation.get();
        }

        @SuppressWarnings("unchecked")
        Set<Annotation> bindings = (Set<Annotation>) invocationContext.getContextData().get("org.jboss.weld.interceptor.bindings");
        if (bindings != null) {
            optionalAnnotation = bindings.stream()
                    .filter(annotation -> annotation.annotationType().equals(type))
                    .findAny()
                    .map(annotation -> type.cast(annotation));

            if (optionalAnnotation.isPresent()) {
                return optionalAnnotation.get();
            }
        }

        throw new IllegalStateException("@" + type + " not present on " + interceptedBean.getBeanClass());
    }

    public static <A extends Annotation> Optional<A> getAnnotation(BeanManager beanManager, Class<?> annotatedClass, Class<A> annotationType) {

        if (annotatedClass.isAnnotationPresent(annotationType)) {
            return Optional.of(annotatedClass.getAnnotation(annotationType));
        }

        Queue<Annotation> annotations = new LinkedList<>(Arrays.asList(annotatedClass.getAnnotations()));

        while (!annotations.isEmpty()) {
            Annotation annotation = annotations.remove();

            if (annotation.annotationType().equals(annotationType)) {
                return Optional.of(annotationType.cast(annotation));
            }

            if (beanManager.isStereotype(annotation.annotationType())) {
                annotations.addAll(
                    beanManager.getStereotypeDefinition(
                        annotation.annotationType()
                    )
                );
            }
        }

        return Optional.empty();
    }

    public static <A extends Annotation> Optional<A> getAnnotation(BeanManager beanManager, Annotated annotated, Class<A> annotationType) {

        annotated.getAnnotation(annotationType);

        if (annotated.getAnnotations().isEmpty()) {
            return Optional.empty();
        }

        if (annotated.isAnnotationPresent(annotationType)) {
            return Optional.of(annotated.getAnnotation(annotationType));
        }

        Queue<Annotation> annotations = new LinkedList<>(annotated.getAnnotations());

        while (!annotations.isEmpty()) {
            Annotation annotation = annotations.remove();

            if (annotation.annotationType().equals(annotationType)) {
                return Optional.of(annotationType.cast(annotation));
            }

            if (beanManager.isStereotype(annotation.annotationType())) {
                annotations.addAll(
                    beanManager.getStereotypeDefinition(
                        annotation.annotationType()
                    )
                );
            }
        }

        return Optional.empty();
    }

}
