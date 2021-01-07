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
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.AnnotatedType;

public class AnnotatedMethodWrapper<X> implements AnnotatedMethod<X> {

	private AnnotatedMethod<X> wrappedAnnotatedMethod;

	private Set<Annotation> annotations;

	public AnnotatedMethodWrapper(AnnotatedMethod<X> wrappedAnnotatedMethod) {
		this.wrappedAnnotatedMethod = wrappedAnnotatedMethod;

		annotations = new HashSet<>(wrappedAnnotatedMethod.getAnnotations());
	}

	@Override
	public List<AnnotatedParameter<X>> getParameters() {
		return wrappedAnnotatedMethod.getParameters();
	}

	@Override
	public AnnotatedType<X> getDeclaringType() {
		return wrappedAnnotatedMethod.getDeclaringType();
	}

	@Override
	public boolean isStatic() {
		return wrappedAnnotatedMethod.isStatic();
	}

	@Override
	public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
		for (Annotation annotation : annotations) {
			if (annotationType.isInstance(annotation)) {
				return annotationType.cast(annotation);
			}
		}

		return null;
	}

	@Override
	public Set<Annotation> getAnnotations() {
		return Collections.unmodifiableSet(annotations);
	}

	@Override
	public Type getBaseType() {
		return wrappedAnnotatedMethod.getBaseType();
	}

	@Override
	public Set<Type> getTypeClosure() {
		return wrappedAnnotatedMethod.getTypeClosure();
	}

	@Override
	public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
		for (Annotation annotation : annotations) {
			if (annotationType.isInstance(annotation)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public Method getJavaMember() {
		return wrappedAnnotatedMethod.getJavaMember();
	}

	public void addAnnotation(Annotation annotation) {
		annotations.add(annotation);
	}

	public void removeAnnotation(Annotation annotation) {
		annotations.remove(annotation);
	}

	public void removeAnnotation(Class<? extends Annotation> annotationType) {
		Annotation annotation = getAnnotation(annotationType);
		if (annotation != null ) {
			removeAnnotation(annotation);
		}
	}

}
