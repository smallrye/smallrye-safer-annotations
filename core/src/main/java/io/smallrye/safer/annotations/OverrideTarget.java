package io.smallrye.safer.annotations;

import java.lang.annotation.Annotation;

/**
 * When placing constraints on a {@link DefinitionOverride} you must declare an <code>OverrideTarget</code> to
 * specify which annotation you are constraining or whose constraints you are overriding.
 */
public @interface OverrideTarget {
    /**
     * The annotation type we aim to constrain.
     * 
     * @return The annotation type we aim to constrain.
     */
    Class<? extends Annotation> value();
}
