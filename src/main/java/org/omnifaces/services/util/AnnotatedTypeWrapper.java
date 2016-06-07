/*
 * Copyright 2016 OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.omnifaces.services.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

import javax.enterprise.inject.spi.AnnotatedConstructor;
import javax.enterprise.inject.spi.AnnotatedField;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;

public class AnnotatedTypeWrapper<T> implements AnnotatedType<T> {

	private AnnotatedType<T> wrappedAnnotatedType;

	private Set<Annotation> annotations = new HashSet<>();
	private Set<AnnotatedMethod<? super T>> annotatedMethods = new HashSet<>();
	private Set<AnnotatedField<? super T>> annotatedFields = new HashSet<>();

	public AnnotatedTypeWrapper(AnnotatedType<T> wrappedAnnotatedType) {
		this.wrappedAnnotatedType = wrappedAnnotatedType;

		annotations.addAll(wrappedAnnotatedType.getAnnotations());
		annotatedMethods.addAll(wrappedAnnotatedType.getMethods());
		annotatedFields.addAll(wrappedAnnotatedType.getFields());
	}

	@Override
	public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
		return wrappedAnnotatedType.getAnnotation(annotationType);
	}

	@Override
	public Set<Annotation> getAnnotations() {
		return annotations;
	}

	@Override
	public Type getBaseType() {
		return wrappedAnnotatedType.getBaseType();
	}

	@Override
	public Set<AnnotatedConstructor<T>> getConstructors() {
		return wrappedAnnotatedType.getConstructors();
	}

	@Override
	public Set<AnnotatedField<? super T>> getFields() {
		return annotatedFields;
	}

	@Override
	public Class<T> getJavaClass() {
		return wrappedAnnotatedType.getJavaClass();
	}

	@Override
	public Set<AnnotatedMethod<? super T>> getMethods() {
		return annotatedMethods;
	}

	@Override
	public Set<Type> getTypeClosure() {
		return wrappedAnnotatedType.getTypeClosure();
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
