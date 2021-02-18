package io.smallrye.safer.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * If you place this annotation on your method annotation, its use-sites will be checked by this APT plugin
 * for compatibility.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TargetMethod {

    /**
     * Indicates the allowed method return types.
     * 
     * @see GenericType How tospecify generic types
     */
    Class<?>[] returnTypes() default {};

    /**
     * Indicates the allowed method parameter types.
     * 
     * @see GenericType How to specify generic types
     */
    Class<?>[] parameterTypes() default {};

    /**
     * Use named subclasses of this type to pass generic types to {@link TargetMethod}:
     * 
     * <pre>
     * class UniResponse extends TargetMethod.GenericType&lt;Uni&lt;Response>>{}
     * 
     * &#64;TargetMethod(returnTypes = UniResponse.class)
     * &#64;Target(ElementType.METHOD)
     * public &#64;annotation UniResponseMethod {}
     * </pre>
     *
     * @param <T> the generic type you want to pass to {@link TargetMethod}.
     */
    public abstract class GenericType<T> {
    }
}
