package utils;
import dto.ErrorResponse;
import io.smallrye.jwt.build.JwtException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import io.smallrye.jwt.auth.principal.ParseException;

@Provider
public class JwtExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception ex) {

        if (ex instanceof JwtException || ex instanceof ParseException) {
            return Response
                    .status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse(401, "Invalid or missing token"))
                    .type("application/json")
                    .build();
        }

        // let global mapper handle others
        return new GlobalExceptionMapper().toResponse(ex);
    }
}
