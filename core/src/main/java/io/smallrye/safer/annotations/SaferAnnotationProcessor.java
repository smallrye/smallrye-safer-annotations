package io.smallrye.safer.annotations;

import static javax.lang.model.util.ElementFilter.methodsIn;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

public class SaferAnnotationProcessor extends AbstractProcessor {

    public static class ExactMatcher implements Matcher {

        private TypeMirror typeMirror;

        public ExactMatcher(TypeMirror typeMirror) {
            this.typeMirror = typeMirror;
        }

        @Override
        public boolean matches(ProcessingEnvironment processingEnv, TypeMirror checkedType) {
            return processingEnv.getTypeUtils().isSameType(typeMirror, checkedType);
        }

        // This is used in error reporting
        @Override
        public String toString() {
            return typeMirror.toString();
        }
    }

    public static class SubtypeMatcher implements Matcher {

        private TypeMirror typeMirror;

        public SubtypeMatcher(TypeMirror typeMirror) {
            this.typeMirror = typeMirror;
        }

        @Override
        public boolean matches(ProcessingEnvironment processingEnv, TypeMirror checkedType) {
            // we want to check that checkedType (the param type used by the user) is a subtype of
            // our typeMirror (the declared allowed type)
            return processingEnv.getTypeUtils().isSubtype(checkedType, typeMirror);
        }

        // This is used in error reporting
        @Override
        public String toString() {
            return "subtype of " + typeMirror;
        }
    }

    public interface Matcher {
        boolean matches(ProcessingEnvironment processingEnv, TypeMirror checkedType);
    }

    private Map<Name, TypeElement> targetMethodOverrides = new HashMap<>();
    private Set<String> loadedOverrides = new HashSet<>();

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
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        /**
         * I tried using the APT API from Filer to load the services, but it can only load a single file.
         * I also tried accessing the JavaFileManager but it requires reflection to get to it, and differs
         * in javac/ecj, and this reflection triggers a huge warning in JDK9.
         * I even tried setting up a JavaFileManager via ToolProvider and the ProcessingEnvironment.getOptions()
         * but turns out this map is empty.
         * The java.classpath system property is empty when invoked via Maven.
         * The base TCCL doesn't allow us to load the service. Even the ClassLoader from the JavaFileManager
         * don't allow us to use the ServiceLoader API, so this CL is the only one I found to work in Maven
         * and Eclipse, and in a project where the service file is in the source path, as well as one where it
         * comes from external libs.
         * In short: you can try to improve this, but good luck.
         */
        // First try to load it via the filer, because this works in some cases where the ServiceLoader doesn't, even though
        // there can be only a single file, but in case your overrides are in your current project, they won't be compiled yet
        // so you can only load them via the Javac Mirror API, and not the ServiceLoader, which requires them to be compiled.
        try {
            FileObject resource = this.processingEnv.getFiler().getResource(StandardLocation.CLASS_PATH, "",
                    "META-INF/services/" + DefinitionOverride.class.getName());
            try (BufferedReader reader = new BufferedReader(resource.openReader(true))) {
                String line = null;
                while ((line = reader.readLine()) != null) {
                    String className = line.trim();
                    loadOverrideClass(className);
                }
            }
        } catch (FileNotFoundException e) {
            // ignore this one, it's fine if it doesn't exist
        } catch (IOException e) {
            // Shrug
            e.printStackTrace();
        }
        // Now do the real thing in most cases.
        Iterator<DefinitionOverride> services = ServiceLoader.load(DefinitionOverride.class,
                SaferAnnotationProcessor.class.getClassLoader()).iterator();
        while (services.hasNext()) {
            // This is how we get around missing classes per entry, since there's no API to get us the class names
            // rather than instances
            try {
                DefinitionOverride override = services.next();
                loadOverrideClass(override.getClass().getName());
            } catch (ServiceConfigurationError x) {
                // I kid you not, there's no accessor for the service type
                // format: io.smallrye.safer.annotations.DefinitionOverride: Provider io.quarkus.resteasy.reactive.server.runtime.ServerExceptionMapperOverride not found
                String message = x.getMessage();
                String prefix = DefinitionOverride.class.getName() + ": Provider ";
                String suffix = " not found";
                // Ignore those we already loaded above, and NOTE the others: let's not break the build for this
                if (message.startsWith(prefix) && message.endsWith(suffix)) {
                    String className = message.substring(prefix.length(), message.length() - suffix.length());
                    if (loadedOverrides.contains(className)) {
                        // ignore it
                    } else {
                        // ignore it and move on to the next one
                        processingEnv.getMessager().printMessage(Kind.NOTE, "Failed to load service provider: " + className);
                    }
                } else {
                    // ignore it and move on to the next one
                    processingEnv.getMessager().printMessage(Kind.NOTE, "Failed to load service provider: " + x.getMessage());
                }
            }
        }
    }

    private void loadOverrideClass(String className) {
        // do not load overrides twice
        if (!loadedOverrides.add(className)) {
            return;
        }
        TypeElement typeElement = this.processingEnv.getElementUtils().getTypeElement(className);
        if (typeElement == null) {
            processingEnv.getMessager().printMessage(Kind.WARNING,
                    "Failed to load override class: " + className);
            return;
        }
        OverrideTarget target = typeElement.getAnnotation(OverrideTarget.class);
        if (target == null) {
            processingEnv.getMessager().printMessage(Kind.ERROR,
                    "Classes implementing DefinitionOverride must have an @OverrideTarget annotation", typeElement);
            return;
        }
        try {
            // This API is brain dead: any attempt to load a Class from an annotation mirror will throw a MirroredTypeException
            // from which we can obtain the DeclaredType
            Class<?> value = target.value();
            if (value == null) {
                processingEnv.getMessager().printMessage(Kind.ERROR,
                        "Classes implementing DefinitionOverride must have an @OverrideTarget annotation",
                        typeElement);
                return;
            }
        } catch (MirroredTypeException x) {
            // See previous note, this is the normal flow, duh :(
            DeclaredType dt = (DeclaredType) x.getTypeMirror();
            TypeElement elem = (TypeElement) dt.asElement();
            targetMethodOverrides.put(elem.getQualifiedName(), typeElement);
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        doProcess(annotations, roundEnv);
        return false;
    }

    public void doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            if (hasAnnotation(annotation, TargetMethod.class)) {
                handleTargetMethod(roundEnv, annotation);
            }
            if (hasAnnotation(annotation, TargetAccessor.class)) {
                handleTargetAccessor(roundEnv, annotation);
            }
        }
    }

    private boolean hasAnnotation(TypeElement annotation, Class<? extends Annotation> metaAnnotation) {
        TypeElement override = targetMethodOverrides.get(annotation.getQualifiedName());
        if (override != null && override.getAnnotation(metaAnnotation) != null) {
            return true;
        }
        return annotation.getAnnotation(metaAnnotation) != null;
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
        List<Matcher> allowedReturnTypes = new ArrayList<>();
        List<Matcher> allowedParameterTypes = new ArrayList<>();
        AnnotationValue returnTypes = getAnnotationValue("returnTypes", targetMethod);
        if (returnTypes != null) {
            for (AnnotationValue obj : (List<AnnotationValue>) returnTypes.getValue()) {
                TypeMirror returnType = (TypeMirror) obj.getValue();
                allowedReturnTypes.add(makeTypeMatcher(returnType));
            }
        }
        AnnotationValue parameterTypes = getAnnotationValue("parameterTypes", targetMethod);
        if (parameterTypes != null) {
            for (AnnotationValue obj : (List<AnnotationValue>) parameterTypes.getValue()) {
                TypeMirror parameterType = (TypeMirror) obj.getValue();
                allowedParameterTypes.add(makeTypeMatcher(parameterType));
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

    private Matcher makeTypeMatcher(TypeMirror type) {
        if (type.getKind() == TypeKind.DECLARED) {
            TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
            TypeMirror superclass = typeElement.getSuperclass();
            if (superclass != null && superclass.getKind() == TypeKind.DECLARED) {
                DeclaredType supertype = ((DeclaredType) superclass);
                TypeElement supertypeElement = (TypeElement) supertype.asElement();
                String qualifiedName = supertypeElement.getQualifiedName().toString();
                // APT uses dots, and class.name has $
                if (qualifiedName.equals(TargetMethod.GenericType.class.getName().replace('$', '.'))) {
                    return new ExactMatcher(supertype.getTypeArguments().get(0));
                } else if (qualifiedName.equals(TargetMethod.Subtype.class.getName().replace('$', '.'))) {
                    return new SubtypeMatcher(supertype.getTypeArguments().get(0));
                }
            }
        }
        return new ExactMatcher(type);
    }

    private void checkType(TypeMirror checkedType, List<Matcher> allowedTypes, String kind, Element element) {
        boolean ok = false;
        for (Matcher allowedType : allowedTypes) {
            if (allowedType.matches(processingEnv, checkedType)) {
                // OK
                ok = true;
                break;
            }
        }
        if (!ok) {
            processingEnv.getMessager().printMessage(Kind.ERROR,
                    "Invalid " + kind + " type: '" + checkedType + "' must be one of: "
                            + allowedTypes,
                    element);
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
        TypeElement override = targetMethodOverrides.get(annotated.getQualifiedName());
        if (override != null)
            annotated = override;
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
