package api;

import com.shakilpatel.auth.service.ejb.AuthBean;
import com.shakilpatel.auth.service.ejb.UserBean;
import com.shakilpatel.auth.service.entity.User;
import dto.*;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJB;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import utils.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DistributorResource {

    @EJB
    private AuthBean authBean;

    @EJB
    private UserBean userBean;

    /**
     * Register as distributor (public - no auth required) POST
     * /api/auth/register/distributor
     */
    @POST
    @Path("/register/distributor")
    @PermitAll
    public Response registerDistributor(
            @Valid RegisterDistributorRequest request,
            @Context HttpServletRequest httpRequest) {

        try {
            AuthBean.RegistrationResult result = authBean.registerDistributor(
                    request.getEmail(),
                    request.getPassword(),
                    request.getFullName(),
                    request.getPhone(),
                    request.getAddress()
            );

            if (!result.isSuccess()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(ApiResponse.error(result.getMessage()))
                        .build();
            }

            return Response.status(Response.Status.CREATED)
                    .entity(ApiResponse.success(
                            Map.of(
                                    "userId", result.getUser().getId(),
                                    "email", result.getUser().getEmail(),
                                    "status", "PENDING",
                                    "message", "Application submitted successfully. You will be notified via email once approved."
                            )
                    ))
                    .build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Registration failed: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get pending distributors (PLATFORM_ADMIN only) GET
     * /api/auth/distributors/pending
     */
    @GET
    @Path("/distributors/pending")
    @RolesAllowed("PLATFORM_ADMIN")
    public Response getPendingDistributors() {
        try {
            List<User> pendingUsers = userBean.findPendingDistributors();

            List<DistributorDTO> distributors = pendingUsers.stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());

            return Response.ok(ApiResponse.success(
                    Map.of(
                            "distributors", distributors,
                            "total", distributors.size()
                    )
            )).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to fetch pending distributors: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get active distributors (for assignment dropdown) GET
     * /api/auth/distributors/active
     */
    @GET
    @Path("/distributors/active")
    @RolesAllowed({"PLATFORM_ADMIN", "ORG_OWNER", "ORG_ADMIN"})
    public Response getActiveDistributors() {
        try {
            List<User> activeUsers = userBean.findActiveDistributors();

            List<DistributorDTO> distributors = activeUsers.stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());

            return Response.ok(ApiResponse.success(
                    Map.of(
                            "distributors", distributors,
                            "total", distributors.size()
                    )
            )).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to fetch active distributors: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get all distributors  GET
     * /api/auth/distributors/all
     */
    @GET
    @Path("/distributors/all")
    @RolesAllowed({"PLATFORM_ADMIN"})
    public Response getAllDistributors() {
        try {
            List<User> activeUsers = userBean.findAllDistributors();

            List<DistributorDTO> distributors = activeUsers.stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());

            return Response.ok(ApiResponse.success(
                    Map.of(
                            "distributors", distributors,
                            "total", distributors.size()
                    )
            )).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to fetch active distributors: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Approve distributor (PLATFORM_ADMIN only) PUT
     * /api/auth/distributors/{userId}/approve
     */
    @PUT
    @Path("/distributors/{userId}/approve")
    @RolesAllowed("PLATFORM_ADMIN")
    public Response approveDistributor(
            @PathParam("userId") Long userId,
            @Valid ApproveDistributorRequest request,
            @Context SecurityContext securityContext) {

        try {
            Long approvedBy = Long.parseLong(securityContext.getUserPrincipal().getName());

            authBean.approveDistributor(userId, approvedBy);

            return Response.ok(ApiResponse.success(
                    null,
                    "Distributor approved successfully"
            )).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Approval failed: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Reject distributor (PLATFORM_ADMIN only) PUT
     * /api/auth/distributors/{userId}/reject
     */
    @PUT
    @Path("/distributors/{userId}/reject")
    @RolesAllowed("PLATFORM_ADMIN")
    public Response rejectDistributor(
            @PathParam("userId") Long userId,
            @Valid RejectDistributorRequest request,
            @Context SecurityContext securityContext) {

        try {
            Long rejectedBy = Long.parseLong(securityContext.getUserPrincipal().getName());

            authBean.rejectDistributor(userId, rejectedBy, request.getReason());

            return Response.ok(ApiResponse.success(
                    null,
                    "Distributor application rejected"
            )).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Rejection failed: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Suspend distributor (PLATFORM_ADMIN only) PUT
     * /api/auth/distributors/{userId}/suspend
     */
    @PUT
    @Path("/distributors/{userId}/suspend")
    @RolesAllowed("PLATFORM_ADMIN")
    public Response suspendDistributor(
            @PathParam("userId") Long userId,
            @Valid SuspendDistributorRequest request,
            @Context SecurityContext securityContext) {

        try {
            Long suspendedBy = Long.parseLong(securityContext.getUserPrincipal().getName());

            authBean.suspendDistributor(userId, suspendedBy, request.getReason());

            return Response.ok(ApiResponse.success(
                    null,
                    "Distributor suspended successfully"
            )).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Suspension failed: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Reactivate distributor (PLATFORM_ADMIN only) PUT
     * /api/auth/distributors/{userId}/reactivate
     */
    @PUT
    @Path("/distributors/{userId}/reactivate")
    @RolesAllowed("PLATFORM_ADMIN")
    public Response reactivateDistributor(
            @PathParam("userId") Long userId,
            @Context SecurityContext securityContext) {

        try {
            Long reactivatedBy = Long.parseLong(securityContext.getUserPrincipal().getName());

            authBean.reactivateDistributor(userId, reactivatedBy);

            return Response.ok(ApiResponse.success(
                    null,
                    "Distributor reactivated successfully"
            )).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ApiResponse.error(e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Reactivation failed: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get distributor stats (PLATFORM_ADMIN only) GET
     * /api/auth/distributors/stats
     */
    @GET
    @Path("/distributors/stats")
    @RolesAllowed("PLATFORM_ADMIN")
    public Response getDistributorStats() {
        try {
            Long pendingCount = userBean.countPendingDistributors();
            Long activeCount = (long) userBean.findActiveDistributors().size();
            Long rejectedCount = userBean.findByAccountStatus("REJECTED").stream()
                    .filter(u -> u.getUserRoles().stream()
                    .anyMatch(ur -> "DISTRIBUTOR".equals(ur.getRole().getRoleName())))
                    .count();
            Long suspendedCount = userBean.findByAccountStatus("SUSPENDED").stream()
                    .filter(u -> u.getUserRoles().stream()
                    .anyMatch(ur -> "DISTRIBUTOR".equals(ur.getRole().getRoleName())))
                    .count();

            return Response.ok(ApiResponse.success(
                    Map.of(
                            "pending", pendingCount,
                            "active", activeCount,
                            "rejected", rejectedCount,
                            "suspended", suspendedCount,
                            "total", pendingCount + activeCount + rejectedCount + suspendedCount
                    )
            )).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ApiResponse.error("Failed to fetch stats: " + e.getMessage()))
                    .build();
        }
    }

    // Helper method
    private DistributorDTO toDTO(User user) {
        return new DistributorDTO(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getProfileUrl(),
                user.getAccountStatus(),
                user.getAppliedAt(),
                user.getApprovedAt(),
                user.getApprovedBy(),
                user.getRejectionReason(),
                user.getCreatedAt()
        );
    }
}
