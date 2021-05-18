package io.smallrye.safer.annotations.test;

import io.smallrye.safer.annotations.TargetMethod;

@TargetMethod(parameterTypes = { Integer.class }, returnTypes = { void.class })
public @interface OverriddenMethodAnnotation {

}
