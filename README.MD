Safer Annotations helps you define constraints on your annotations that are richer than the
stock Java constraints (type, method, field…).

# Usage, for library authors

Import the Safer Annotations module in your `pom.xml`:

```xml
<dependency>
    <groupId>io.smallrye</groupId>
    <artifactId>smallrye-safer-annotations</artifactId>
    <version>1.0.0</version>
</dependency>
```

And start annotating your library annotations with safer constraints:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
// Restrict the type signature of target methods
@TargetMethod(
  /* set of allowed return types */
  returnTypes = { void.class, Response.class, UniResponse.class, OptionalResponse.class }, 
  /* set of allowed parameter types */
  parameterTypes = { ContainerRequestContext.class, ContainerResponseContext.class, ResourceInfo.class, SimpleResourceInfo.class}
)
public @interface ServerResponseFilter {
}

// Allows us to pass generic types to the @TargetMethod annotation
class UniResponse extends TargetMethod.GenericType<Uni<Response>> {
}

class OptionalResponse extends TargetMethod.GenericType<Optional<Response>> {
}
```

# Usage, for your library's users

Tell your build tool to invoke our compiler plugin:

```xml
<build>
    <plugins>
        <plugin>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>${compiler-plugin.version}</version>
            <configuration>
                <annotationProcessorPaths>
                    <annotationProcessorPath>
                        <groupId>io.smallrye</groupId>
                        <artifactId>smallrye-safer-annotations</artifactId>
                        <version>1.0.0</version>
                    </annotationProcessorPath>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

Now your users can get compile-time errors if they misplace your annotations:

```java
@ServerResponseFilter
// ERROR: Invalid parameter type: must be one of: […]
public void myFilter(NonSupportedType something){
}
```

# Supported annotation constraints

- `@TargetAccessor`: annotated method must be a Java Bean getter or setter
- `@TargetMethod`: annotated method must have a compatible method signature
