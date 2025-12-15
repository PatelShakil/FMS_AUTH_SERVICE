package utils;
import dto.ErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable ex) {
        int status = 500;

        if (ex instanceof jakarta.ws.rs.NotFoundException) {
            status = 404;
        } else if (ex instanceof jakarta.ws.rs.NotAuthorizedException) {
            status = 401;
        } else if (ex instanceof jakarta.ws.rs.ForbiddenException) {
            status = 403;
        } else if (ex instanceof jakarta.ws.rs.BadRequestException) {
            status = 400;
        }

        return Response
                .status(status)
                .entity(new ErrorResponse(status, ex.getMessage()))
                .type("application/json")
                .build();
    }
}
