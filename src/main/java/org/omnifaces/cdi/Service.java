package org.omnifaces.cdi;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.enterprise.inject.Stereotype;
import javax.transaction.Transactional;

import org.omnifaces.cdi.pooled.Pooled;

@Stereotype
@Pooled
@Transactional
@Target(TYPE)
@Retention(RUNTIME)
public @interface Service {

}
