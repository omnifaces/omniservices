package org.omnifaces.cdi.pooled;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.*;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.*;

public class PooledExtension implements Extension {

    public void beforeBeanDiscovery(@Observes BeforeBeanDiscovery beforeBeanDiscovery) {
        beforeBeanDiscovery.addScope(Pooled.class, true, false);
    }

    public void afterBeanDiscovery(@Observes AfterBeanDiscovery afterBeanDiscovery, BeanManager bm) {
        afterBeanDiscovery.addContext(new PooledContext());
    }

    public <T> void processAnnotatedType(
            @Observes ProcessAnnotatedType<T> processAnnotatedType) {

        AnnotatedType<T> annotatedType = processAnnotatedType
                .getAnnotatedType();

        if (annotatedType.isAnnotationPresent(Pooled.class)) {

            Annotation auditAnnotation = new Annotation() {
                @Override
                public Class<? extends Annotation> annotationType() {
                    return PooledInterceptorBinding.class;
                }
            };

            AnnotatedTypeWrapper<T> wrapper = new AnnotatedTypeWrapper<T>(
                    annotatedType, annotatedType.getAnnotations());
            wrapper.addAnnotation(auditAnnotation);

            processAnnotatedType.setAnnotatedType(wrapper);
        }

    }
    
}

class AnnotatedTypeWrapper<T> implements AnnotatedType<T> {

  private final AnnotatedType<T> wrapped;
  private final Set<Annotation> annotations;

  public AnnotatedTypeWrapper(AnnotatedType<T> wrapped,
      Set<Annotation> annotations) {
    this.wrapped = wrapped;
    this.annotations = new HashSet<>(annotations);
  }

  public void addAnnotation(Annotation annotation) {
    annotations.add(annotation);
  }

  @Override
  public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
    return wrapped.getAnnotation(annotationType);
  }

  @Override
  public Set<Annotation> getAnnotations() {
    return annotations;
  }

  @Override
  public Type getBaseType() {
    return wrapped.getBaseType();
  }

  @Override
  public Set<Type> getTypeClosure() {
    return wrapped.getTypeClosure();
  }

  @Override
  public boolean isAnnotationPresent(
      Class<? extends Annotation> annotationType) {
    for (Annotation annotation : annotations) {
      if (annotationType.isInstance(annotation)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Set<AnnotatedConstructor<T>> getConstructors() {
    return wrapped.getConstructors();
  }

  @Override
  public Set<AnnotatedField<? super T>> getFields() {
    return wrapped.getFields();
  }

  @Override
  public Class<T> getJavaClass() {
    return wrapped.getJavaClass();
  }

  @Override
  public Set<AnnotatedMethod<? super T>> getMethods() {
    return wrapped.getMethods();
  }

}