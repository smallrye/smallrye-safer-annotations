package io.smallrye.safer.annotations.test;

import java.util.List;

import io.smallrye.safer.annotations.TargetMethod;
import io.smallrye.safer.annotations.TargetMethod.GenericType;

class ListOfInteger extends GenericType<List<Integer>> {
}

@TargetMethod(parameterTypes = { Integer.class, ListOfInteger.class }, returnTypes = { void.class, String.class,
        ListOfInteger.class })
public @interface MethodAnnotation {

}
