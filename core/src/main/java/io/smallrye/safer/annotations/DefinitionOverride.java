package io.smallrye.safer.annotations;

/**
 * Implement this interface if you want to declare an override, for cases where you cannot place annotation
 * constraints on the target annotation:
 *
 * <pre>
 * &#64;OverrideTarget(ServerExceptionMapper.class)
 * &#64;TargetMethod(returnTypes = { Response.class, UniResponse.class }, parameterTypes = {
 *         ContainerRequestContext.class, UriInfo.class, HttpHeaders.class, Request.class,
 *         ResourceInfo.class, SimpleResourceInfo.class, RoutingContext.class })
 * public class ServerExceptionMapperOverride implements DefinitionOverride {
 * }
 * </pre>
 * 
 * Do not forget to list your implementations in a service file in
 * <tt>META-INF/services/io.smallrye.safer.annotations.DefinitionOverride</tt>.
 */
public interface DefinitionOverride {

}
