package uk.gov.justice.services.adapters.rest.generator;

import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.interfaceBuilder;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PUBLIC;
import static org.apache.commons.lang.StringUtils.defaultIfBlank;
import static org.apache.commons.lang.StringUtils.strip;
import static uk.gov.justice.services.adapters.rest.generator.Generators.byMimeTypeOrder;
import static uk.gov.justice.services.adapters.rest.generator.Generators.resourceInterfaceNameOf;
import static uk.gov.justice.services.adapters.rest.helper.Multiparts.isMultipartResource;
import static uk.gov.justice.services.generators.commons.helper.Actions.responseMimeTypesOf;
import static uk.gov.justice.services.generators.commons.helper.Names.DEFAULT_ANNOTATION_PARAMETER;
import static uk.gov.justice.services.generators.commons.helper.Names.GENERIC_PAYLOAD_ARGUMENT_NAME;
import static uk.gov.justice.services.generators.commons.helper.Names.resourceMethodNameFrom;
import static uk.gov.justice.services.generators.commons.helper.Names.resourceMethodNameWithNoMimeTypeFrom;

import uk.gov.justice.services.adapter.rest.annotation.PATCH;
import uk.gov.justice.services.generators.commons.helper.RestResourceBaseUri;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.json.JsonObject;
import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.raml.model.Action;
import org.raml.model.ActionType;
import org.raml.model.MimeType;
import org.raml.model.Raml;
import org.raml.model.Resource;
import org.raml.model.parameter.QueryParameter;
import org.raml.model.parameter.UriParameter;

/**
 * Internal code generation class for generating the JAX-RS interface.
 */
class JaxRsInterfaceGenerator {

    private static final String ANNOTATION_FORMAT = "$S";
    private static final String MULTIPART_FORM_DATA_INPUT = "multipartFormDataInput";

    /**
     * Generate Java code for a Raml structure
     *
     * @param raml {@link Raml ) structure to generate code from
     * @return a list of {@link TypeSpec } that represent Java classes
     */
    public List<TypeSpec> generateFor(final Raml raml) {
        final Collection<Resource> resources = raml.getResources().values();
        return resources.stream()
                .map((resource) -> generateFor(resource, new RestResourceBaseUri(raml.getBaseUri())))
                .collect(toList());
    }

    /**
     * Create an interface for the specified {@link Resource}
     *
     * @param resource the resource to generate as an implementation class
     * @param baseUri
     * @return a {@link TypeSpec} that represents the implementation class
     */
    private TypeSpec generateFor(final Resource resource, final RestResourceBaseUri baseUri) {
        final TypeSpec.Builder interfaceSpecBuilder = interfaceSpecFor(resource, baseUri);

        resource.getActions().values().forEach(action ->
                interfaceSpecBuilder.addMethods(methodsOf(action)));

        return interfaceSpecBuilder.build();
    }

    /**
     * Process the body or bodies for each httpAction.
     *
     * @param action the httpAction to methodsOf
     * @return the list of {@link MethodSpec} that represents each method for the httpAction
     */
    private List<MethodSpec> methodsOf(final Action action) {
        final Collection<MimeType> responseMimeTypes = responseMimeTypesOf(action);

        if (!action.hasBody()) {
            return singletonList(processNoActionBody(action, responseMimeTypes));
        } else {
            return processOneOrMoreActionBodies(action, responseMimeTypes);
        }
    }

    /**
     * Process an httpAction with no body.
     *
     * @param action the httpAction to process
     * @return the {@link MethodSpec} that represents a method for the httpAction
     */
    private MethodSpec processNoActionBody(final Action action,
                                           final Collection<MimeType> responseMimeTypes) {
        final String resourceMethodName = resourceMethodNameWithNoMimeTypeFrom(action);
        return generateResourceMethod(action, resourceMethodName, responseMimeTypes).build();
    }

    /**
     * Process an httpAction with one or more bodies.
     *
     * @param action the httpAction to process
     * @return the list of {@link MethodSpec} that represents each method for the httpAction
     */
    private List<MethodSpec> processOneOrMoreActionBodies(final Action action,
                                                          final Collection<MimeType> responseMimeTypes) {
        return action.getBody().values().stream()
                .sorted(byMimeTypeOrder())
                .map(bodyMimeType -> {
                    final String resourceMethodName = resourceMethodNameFrom(action, bodyMimeType);
                    final MethodSpec.Builder methodBuilder = generateResourceMethod(action, resourceMethodName, responseMimeTypes);
                    return addToMethodWithMimeType(methodBuilder, bodyMimeType).build();
                }).collect(toList());
    }

    /**
     * Creates a {@link TypeSpec.Builder} from an initial template of an interface
     *
     * @param resource the resource to generate as an interface
     * @param baseUri
     * @return a {@link TypeSpec.Builder} that represents the interface
     */
    private TypeSpec.Builder interfaceSpecFor(final Resource resource, final RestResourceBaseUri baseUri) {
        return interfaceBuilder(resourceInterfaceNameOf(resource, baseUri))
                .addModifiers(PUBLIC)
                .addAnnotation(AnnotationSpec.builder(Path.class)
                        .addMember(DEFAULT_ANNOTATION_PARAMETER, ANNOTATION_FORMAT, pathAnnotationFor(resource))
                        .build());
    }

    /**
     * Generate path annotation for a resource.
     *
     * @param resource generate for this resource
     * @return the path annotation string
     */
    private String pathAnnotationFor(final Resource resource) {
        return defaultIfBlank(strip(resource.getRelativeUri(), "/"), "/");
    }

    /**
     * Add MimeType specific annotation and parameter to method.
     *
     * @param methodBuilder add annotation and parameter to this method builder
     * @return the method builder
     */
    private MethodSpec.Builder addToMethodWithMimeType(final MethodSpec.Builder methodBuilder,
                                                       final MimeType bodyMimeType) {
        methodBuilder
                .addAnnotation(AnnotationSpec.builder(Consumes.class)
                        .addMember(DEFAULT_ANNOTATION_PARAMETER, ANNOTATION_FORMAT, bodyMimeType.getType())
                        .build());

        if (isMultipartResource(bodyMimeType)) {
            methodBuilder.addParameter(ParameterSpec
                    .builder(MultipartFormDataInput.class, MULTIPART_FORM_DATA_INPUT)
                    .addAnnotation(MultipartForm.class)
                    .build());
        } else if (bodyMimeType.getSchema() != null) {
            methodBuilder.addParameter(ParameterSpec
                    .builder(JsonObject.class, GENERIC_PAYLOAD_ARGUMENT_NAME)
                    .build());
        }

        return methodBuilder;
    }

    /**
     * Generate a method for each {@link Action}.
     *
     * @param action             the httpAction to generate as a method
     * @param resourceMethodName the resource method name to generate
     * @return a {@link MethodSpec} that represents the generated method
     * @throws IllegalStateException if httpAction type is not GET or POST
     */
    private MethodSpec.Builder generateResourceMethod(final Action action,
                                                      final String resourceMethodName,
                                                      final Collection<MimeType> responseMimeTypes) {

        final Map<String, QueryParameter> queryParams = action.getQueryParameters();
        final Map<String, UriParameter> pathParams = action.getResource().getUriParameters();

        return methodBuilder(resourceMethodName)
                .addModifiers(PUBLIC, ABSTRACT)
                .addAnnotation(annotationFor(action.getType()))
                .addAnnotations(annotationsForProduces(responseMimeTypes))
                .addParameters(methodPathParams(pathParams.keySet()))
                .addParameters(methodQueryParams(queryParams.keySet()))
                .returns(Response.class);
    }

    /**
     * Generate HttpMethod annotation for a given {@link ActionType}.
     *
     * @param actionType the action type to generate the annoation for
     * @return the annotaion representing the HttpMethod type
     */
    private AnnotationSpec annotationFor(final ActionType actionType) {
        switch (actionType) {
            case DELETE:
                return AnnotationSpec.builder(javax.ws.rs.DELETE.class).build();

            case GET:
                return AnnotationSpec.builder(javax.ws.rs.GET.class).build();

            case PATCH:
                return AnnotationSpec.builder(PATCH.class).build();

            case POST:
                return AnnotationSpec.builder(javax.ws.rs.POST.class).build();

            case PUT:
                return AnnotationSpec.builder(javax.ws.rs.PUT.class).build();

            default:
                throw new IllegalStateException(String.format("Unsupported httpAction type %s", actionType));
        }
    }

    /**
     * Generate code to add all query parameters to the params map.
     *
     * @param queryParams the query param names to add to the map
     * @return the {@link CodeBlock} that represents the generated code
     */
    private List<ParameterSpec> methodQueryParams(final Set<String> queryParams) {
        return methodParams(queryParams, QueryParam.class);
    }

    /**
     * Generate method parameters for all the path params.
     *
     * @param pathParams the path param names to generate
     * @return list of {@link ParameterSpec} that represent the method parameters
     */
    private List<ParameterSpec> methodPathParams(final Set<String> pathParams) {
        return methodParams(pathParams, PathParam.class);
    }

    /**
     * Generate code to add all parameters to the params map.
     *
     * @param params the param names to add to the map
     * @return the {@link CodeBlock} that represents the generated code
     */
    private List<ParameterSpec> methodParams(final Collection<String> params, final Class<?> paramAnnotationClass) {
        return params.stream().map(name ->
                ParameterSpec
                        .builder(String.class, name)
                        .addAnnotation(AnnotationSpec.builder(paramAnnotationClass)
                                .addMember(DEFAULT_ANNOTATION_PARAMETER, ANNOTATION_FORMAT, name)
                                .build())
                        .build()
        ).collect(toList());
    }

    /**
     * Generates the Produces annotation for all the response mime types.
     *
     * @param responseMimeTypes generate annotations for each responseMimeType
     * @return the list of {@link AnnotationSpec} that represent the produces annotations
     */
    private List<AnnotationSpec> annotationsForProduces(final Collection<MimeType> responseMimeTypes) {
        final List<AnnotationSpec> specs = new ArrayList<>();

        if (!responseMimeTypes.isEmpty()) {
            final AnnotationSpec.Builder annotationBuilder = AnnotationSpec.builder(Produces.class);

            responseMimeTypes.forEach(responseMimeType ->
                    annotationBuilder.addMember(DEFAULT_ANNOTATION_PARAMETER, ANNOTATION_FORMAT, responseMimeType.getType()));

            specs.add(annotationBuilder.build());
        }

        return specs;
    }


}
