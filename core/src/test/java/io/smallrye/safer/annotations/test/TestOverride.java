package io.smallrye.safer.annotations.test;

import io.smallrye.safer.annotations.DefinitionOverride;
import io.smallrye.safer.annotations.OverrideTarget;
import io.smallrye.safer.annotations.TargetMethod;

@OverrideTarget(OverriddenMethodAnnotation.class)
@TargetMethod(parameterTypes = { String.class }, returnTypes = { String.class })
public class TestOverride implements DefinitionOverride {

}
