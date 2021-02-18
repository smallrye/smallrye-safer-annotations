package io.smallrye.safer.annotations;

import static javax.lang.model.util.ElementFilter.methodsIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

public class SaferAnnotationProcessor extends AbstractProcessor {

    public SaferAnnotationProcessor() {
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton("*");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        doProcess(annotations, roundEnv);
        return false;
    }

    public void doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            if (annotation.getAnnotation(TargetMethod.class) != null) {
                handleTargetMethod(roundEnv, annotation);
            }
            if (annotation.getAnnotation(TargetAccessor.class) != null) {
                handleTargetAccessor(roundEnv, annotation);
            }
        }
    }

    private void handleTargetAccessor(RoundEnvironment roundEnv, TypeElement annotation) {
        // get the target method
        for (ExecutableElement i : methodsIn(roundEnv.getElementsAnnotatedWith(annotation))) {
            String name = i.getSimpleName().toString();
            if ((name.startsWith("get") && name.length() > 3)
                    || (name.startsWith("is") && name.length() > 2)) {
                TypeMirror returnType = i.getReturnType();
                if (returnType.getKind() == TypeKind.VOID) {
                    processingEnv.getMessager().printMessage(Kind.ERROR,
                            "Invalid getter return type: cannot be 'void'", i);
                }
                if (!i.getParameters().isEmpty()) {
                    processingEnv.getMessager().printMessage(Kind.ERROR,
                            "Getter cannot have parameters", i);
                }
            } else if (name.startsWith("set") && name.length() > 3) {
                TypeMirror returnType = i.getReturnType();
                if (returnType.getKind() != TypeKind.VOID) {
                    processingEnv.getMessager().printMessage(Kind.ERROR,
                            "Invalid setter return type: must be 'void'", i);
                }
                if (i.getParameters().size() != 1) {
                    processingEnv.getMessager().printMessage(Kind.ERROR,
                            "Setter must have a single parameter", i);
                }
            } else {
                processingEnv.getMessager().printMessage(Kind.ERROR,
                        "Invalid accessor name: " + name + " must start with 'get', 'is' or 'set'", i);
            }
        }
    }

    private void handleTargetMethod(RoundEnvironment roundEnv, TypeElement annotation) {
        // get the target method
        AnnotationMirror targetMethod = getAnnotation(TargetMethod.class.getName(), annotation);
        List<TypeMirror> allowedReturnTypes = new ArrayList<>();
        List<TypeMirror> allowedParameterTypes = new ArrayList<>();
        AnnotationValue returnTypes = getAnnotationValue("returnTypes", targetMethod);
        if (returnTypes != null) {
            for (AnnotationValue obj : (List<AnnotationValue>) returnTypes.getValue()) {
                TypeMirror returnType = (TypeMirror) obj.getValue();
                allowedReturnTypes.add(unwrapGenericType(returnType));
            }
        }
        AnnotationValue parameterTypes = getAnnotationValue("parameterTypes", targetMethod);
        if (parameterTypes != null) {
            for (AnnotationValue obj : (List<AnnotationValue>) parameterTypes.getValue()) {
                TypeMirror parameterType = (TypeMirror) obj.getValue();
                allowedParameterTypes.add(unwrapGenericType(parameterType));
            }
        }
        for (ExecutableElement i : methodsIn(roundEnv.getElementsAnnotatedWith(annotation))) {
            TypeMirror returnType = i.getReturnType();
            checkType(returnType, allowedReturnTypes, "return", i);
            for (VariableElement parameter : i.getParameters()) {
                TypeMirror parameterType = parameter.asType();
                checkType(parameterType, allowedParameterTypes, "parameter", parameter);
            }
        }
    }

    private TypeMirror unwrapGenericType(TypeMirror type) {
        if (type.getKind() == TypeKind.DECLARED) {
            TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
            TypeMirror superclass = typeElement.getSuperclass();
            if (superclass != null && superclass.getKind() == TypeKind.DECLARED) {
                DeclaredType supertype = ((DeclaredType) superclass);
                TypeElement supertypeElement = (TypeElement) supertype.asElement();
                // APT uses dots, and class.name has $
                if (supertypeElement.getQualifiedName().toString()
                        .equals(TargetMethod.GenericType.class.getName().replace('$', '.'))) {
                    return supertype.getTypeArguments().get(0);
                }
            }
        }
        return type;
    }

    private void checkType(TypeMirror checkedType, List<TypeMirror> allowedTypes, String kind, Element element) {
        boolean ok = false;
        for (TypeMirror allowedType : allowedTypes) {
            if (processingEnv.getTypeUtils().isSameType(allowedType, checkedType)) {
                // OK
                ok = true;
                break;
            }
        }
        if (!ok) {
            processingEnv.getMessager().printMessage(Kind.ERROR,
                    "Invalid " + kind + " type: must be one of: " + allowedTypes, element);
        }
    }

    private AnnotationValue getAnnotationValue(String value, AnnotationMirror annotated) {
        for (Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotated.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().toString().equals(value)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private AnnotationMirror getAnnotation(String targetAnnotation, TypeElement annotated) {
        for (AnnotationMirror annotationMirror : annotated.getAnnotationMirrors()) {
            DeclaredType annotationType = annotationMirror.getAnnotationType();
            TypeElement typeElement = (TypeElement) annotationType.asElement();
            if (typeElement.getQualifiedName().toString().contentEquals(targetAnnotation)) {
                return annotationMirror;
            }
        }
        return null;
    }
}
