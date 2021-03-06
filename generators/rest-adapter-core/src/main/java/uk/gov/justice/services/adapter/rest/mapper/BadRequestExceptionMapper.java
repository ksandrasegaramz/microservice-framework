package uk.gov.justice.services.adapter.rest.mapper;

import static javax.json.Json.createObjectBuilder;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.status;
import static uk.gov.justice.services.core.json.JsonValidationLogger.toJsonObject;

import uk.gov.justice.services.adapter.rest.exception.BadRequestException;

import javax.inject.Inject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.everit.json.schema.ValidationException;
import org.slf4j.Logger;

@Provider
public class BadRequestExceptionMapper implements ExceptionMapper<BadRequestException> {

    @Inject
    Logger logger;

    @Override
    public Response toResponse(final BadRequestException exception) {
        logger.debug("Bad Request", exception);

        final JsonObjectBuilder builder = createObjectBuilder().add("error", exception.getMessage());

        if (exception.getCause() instanceof ValidationException) {
            builder.add("validationErrors", toJsonObject((ValidationException) exception.getCause()));
        }

        return status(BAD_REQUEST)
                .entity(builder.build().toString())
                .build();
    }

}
