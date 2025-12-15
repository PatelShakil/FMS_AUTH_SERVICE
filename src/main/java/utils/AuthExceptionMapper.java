package utils;

import jakarta.security.enterprise.AuthenticationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class AuthExceptionMapper implements ExceptionMapper<AuthenticationException> {

    @Override
    public Response toResponse(jakarta.security.enterprise.AuthenticationException ex) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of(
                        "error", "Unauthorized",
                        "message", ex.getMessage()
                ))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}

