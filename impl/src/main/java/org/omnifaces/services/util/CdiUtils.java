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
package org.omnifaces.services.util;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;

import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.interceptor.InvocationContext;

/**
 * Utilities for CDI, used by OmniServices and OmniBeans.
 */
public class CdiUtils {

    private CdiUtils() {
    }

    /**
     * Returns the interceptor binding of the given type of the intercepted method, i.e. the binding on the method, else
     * the one on its class (directly, or through a stereotype or another interceptor binding), including those added
     * by extensions.
     *
     * @param <T> the interceptor binding type
     * @param invocationContext the invocation context of the intercepted method
     * @param beanManager the bean manager
     * @param interceptedBean the intercepted bean
     * @param type the interceptor binding type
     * @return the interceptor binding
     * @throws IllegalStateException when the method has no interceptor binding of the given type
     */
    public static <T extends Annotation> T getInterceptorBindingAnnotation(InvocationContext invocationContext, BeanManager beanManager, Bean<?> interceptedBean, Class<T> type) {
        T binding = invocationContext.getInterceptorBinding(type);
        if (binding == null) {
            throw new IllegalStateException("@" + type + " not present on " + invocationContext.getMethod() + " of " + interceptedBean.getBeanClass());
        }

        return binding;
    }

    /**
     * Returns the annotation of the given type on the given class, directly or through its stereotypes.
     *
     * @param <A> the annotation type
     * @param beanManager the bean manager, to resolve stereotypes
     * @param annotatedClass the class
     * @param annotationType the annotation type
     * @return the annotation, if any
     */
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

    /**
     * Returns the annotation of the given type on the given annotated element, directly or through its stereotypes.
     *
     * @param <A> the annotation type
     * @param beanManager the bean manager, to resolve stereotypes
     * @param annotated the annotated element
     * @param annotationType the annotation type
     * @return the annotation, if any
     */
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
