package org.omnifaces.services;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.enterprise.inject.Stereotype;
import javax.transaction.Transactional;

import org.omnifaces.services.pooled.Pooled;

@Stereotype
@Pooled
@Transactional(rollbackOn = Throwable.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface Service {

}
