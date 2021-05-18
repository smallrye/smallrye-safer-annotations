package io.smallrye.safer.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * If you place this annotation on your method annotation, its use-sites will be checked by this APT plugin
 * for compatibility. If you cannot directly annotate your annotation, use {@link DefinitionOverride}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TargetMethod {

    /**
     * Indicates the allowed method return types.
     * 
     * @see GenericType How tospecify generic types
     * @return the allowed method return types
     */
    Class<?>[] returnTypes() default {};

    /**
     * Indicates the allowed method parameter types.
     * 
     * @see GenericType How to specify generic types
     * @return the allowed method parameter types
     */
    Class<?>[] parameterTypes() default {};

    /**
     * Use named subclasses of this type to pass generic types to {@link TargetMethod}:
     * 
     * <pre>
     * class UniResponse extends TargetMethod.GenericType&lt;Uni&lt;Response&gt;&gt;{}
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

    /**
     * Use named subclasses of this type to express that a parameter can be any subtype of the given type <tt>T</tt>:
     * 
     * <pre>
     * class ThrowableSubtype extends TargetMethod.Subtype&lt;Throwable&gt;{}
     * 
     * &#64;TargetMethod(parameterTypes = ThrowableSubtype.class)
     * &#64;Target(ElementType.METHOD)
     * public &#64;annotation ThrowableParameterMethod {}
     * </pre>
     *
     * @param <T> the type to allow, as well as any of its subtypes
     */
    public abstract class Subtype<T> {
    }
}
