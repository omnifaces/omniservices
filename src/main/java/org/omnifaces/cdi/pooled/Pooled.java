package org.omnifaces.cdi.pooled;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.enterprise.context.NormalScope;

@Inherited
@NormalScope
@Documented
@Target(TYPE)
@Retention(RUNTIME)
public @interface Pooled {

}
