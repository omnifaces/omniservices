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
package org.omnifaces.services;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

import org.omnifaces.services.asynchronous.AsynchronousInterceptor;
import org.omnifaces.services.asynchronous.AsynchronousResultInterceptor;
import org.omnifaces.services.asynchronous.ExecutorBean;
import org.omnifaces.services.lock.LockInterceptor;
import org.omnifaces.services.pooled.Pooled;
import org.omnifaces.services.pooled.PooledContext;
import org.omnifaces.services.pooled.PooledContextShutdown;
import org.omnifaces.services.pooled.PooledScopeEnabled;
import org.omnifaces.services.pooled.PooledScopeInterceptor;

import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.inject.Stereotype;
import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.Discovery;
import jakarta.enterprise.inject.build.compatible.spi.Enhancement;
import jakarta.enterprise.inject.build.compatible.spi.MetaAnnotations;
import jakarta.enterprise.inject.build.compatible.spi.ScannedClasses;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.inject.Scope;
import jakarta.interceptor.InterceptorBinding;

/**
 * Registers the OmniServices scope, interceptors and beans. A build compatible extension, so it runs on CDI Lite
 * implementations (such as Quarkus' ArC) and on CDI Full implementations alike.
 */
public class OmniServicesExtension implements BuildCompatibleExtension {

    /**
     * The classes of this library that are beans. The library isn't a bean archive, so they're added explicitly.
     */
    private static final Class<?>[] BEAN_CLASSES = {
        AsynchronousInterceptor.class,
        AsynchronousResultInterceptor.class,
        ExecutorBean.class,
        LockInterceptor.class,
        PooledContextShutdown.class,
        PooledScopeInterceptor.class
    };

    /**
     * Creates the extension. Used by CDI.
     */
    public OmniServicesExtension() {
    }

    /**
     * Registers the pooled scope, the {@link Service} stereotype, and the beans and interceptors of this library.
     *
     * @param metaAnnotations to register the scope and the stereotype with
     * @param scannedClasses to add the beans and interceptors to
     */
    @Discovery
    public void registerBeansAndScopes(MetaAnnotations metaAnnotations, ScannedClasses scannedClasses) {
        metaAnnotations.addContext(Pooled.class, PooledContext.class);
        registerServiceStereotype(metaAnnotations);

        for (Class<?> beanClass : BEAN_CLASSES) {
            scannedClasses.add(beanClass.getName());
        }
    }

    /**
     * Registers the {@link Service} stereotype. This library has an extension and no beans.xml, so it's not a bean
     * archive, and a CDI Lite implementation (ArC) doesn't know about stereotypes declared in it otherwise.
     * <p>
     * Its scope and interceptor bindings are set explicitly: implementations differ in whether the configuration starts
     * with the meta-annotations declared on the annotation (ArC) or with none (Weld). They're read by reflection, which
     * leaves out {@code @Transactional} when the Jakarta Transactions API isn't available.
     */
    private static void registerServiceStereotype(MetaAnnotations metaAnnotations) {
        ClassConfig service = metaAnnotations.addStereotype(Service.class);

        for (Annotation annotation : Service.class.getAnnotations()) {
            Class<? extends Annotation> type = annotation.annotationType();

            if (type.isAnnotationPresent(NormalScope.class) || type.isAnnotationPresent(Scope.class) || type.isAnnotationPresent(InterceptorBinding.class)) {
                service.removeAnnotation(existing -> existing.name().equals(type.getName()))
                       .addAnnotation(annotation);
            }
        }
    }

    /**
     * Binds the pooled scope interceptor to beans with the {@link Pooled} scope, declared directly or by a stereotype
     * (such as {@link Service}). A bean that's pooled by a stereotype also gets the stereotype's {@link Pooled}
     * annotation itself: ArC resolves the scopes of stereotypes before it registers custom contexts, so it would make
     * such a bean {@code Dependent}. The pool settings are read from the bean class at runtime, so still from the
     * stereotype.
     *
     * @param classConfig a class to inspect
     */
    // Not filtered with withAnnotations = Pooled.class: Weld's translator doesn't look at meta-annotations for it, so it
    // would skip classes that are pooled through a stereotype
    @Enhancement(types = Object.class, withSubtypes = true)
    public void bindPooledScopeInterceptor(ClassConfig classConfig) {
        ClassInfo classInfo = classConfig.info();
        if (!classInfo.isPlainClass() || classInfo.hasAnnotation(PooledScopeEnabled.class)) {
            return;
        }

        if (classInfo.hasAnnotation(Pooled.class)) {
            classConfig.addAnnotation(PooledScopeEnabled.Literal.INSTANCE);
        } else if (!declaresScope(classInfo)) {
            AnnotationInfo pooled = findPooledInStereotypes(classInfo.annotations(), new HashSet<>());
            if (pooled != null) {
                classConfig.addAnnotation(pooled);
                classConfig.addAnnotation(PooledScopeEnabled.Literal.INSTANCE);
            }
        }
    }

    private static boolean declaresScope(ClassInfo classInfo) {
        for (AnnotationInfo annotation : classInfo.annotations()) {
            ClassInfo declaration = getDeclaration(annotation);
            if (declaration != null && (declaration.hasAnnotation(NormalScope.class) || declaration.hasAnnotation(Scope.class))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the {@link Pooled} annotation of the first stereotype that declares it, among the given annotations or the
     * stereotypes they declare in turn.
     */
    private static AnnotationInfo findPooledInStereotypes(Iterable<AnnotationInfo> annotations, Set<String> visitedStereotypes) {
        for (AnnotationInfo annotation : annotations) {
            ClassInfo declaration = getDeclaration(annotation);
            if (declaration != null && declaration.hasAnnotation(Stereotype.class) && visitedStereotypes.add(declaration.name())) {
                AnnotationInfo pooled = declaration.annotation(Pooled.class);
                if (pooled == null) {
                    pooled = findPooledInStereotypes(declaration.annotations(), visitedStereotypes);
                }
                if (pooled != null) {
                    return pooled;
                }
            }
        }

        return null;
    }

    private static ClassInfo getDeclaration(AnnotationInfo annotation) {
        try {
            return annotation.declaration();
        } catch (IllegalStateException e) {
            // The annotation type isn't known to the container (ArC: not in the index), so it's not a stereotype
            return null;
        }
    }
}
