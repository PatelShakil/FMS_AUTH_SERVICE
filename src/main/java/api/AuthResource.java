package api;

import dto.*;
import com.shakilpatel.auth.service.ejb.*;
import com.shakilpatel.auth.service.entity.User;
import io.smallrye.jwt.auth.principal.JWTParser;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJB;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.io.IOException;
import java.io.InputStream;

import kafka.KafkaProducerBean;
import org.eclipse.microprofile.config.inject.ConfigProperty;
//import io.smallrye.jwt.build.imp;


import java.io.StringReader;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import utils.ApiResponse;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource{

    @EJB
    private JwtBean JwtBean;

    @EJB
    private AuthBean authBean;

    @EJB
    private UserBean userBean;

    @EJB
    private RoleBean roleBean;

    @Inject
    private KafkaProducerBean kafkaProducer;

    @EJB
    private FileStorageBean fileStorageBean;

    @ConfigProperty(name = "jwt.access.token.expiry", defaultValue = "3600")
    private Long accessTokenExpiry;

    /**
     * Login endpoint POST /api/auth/login
     */
    @POST
    @Path("/login")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
//    @PermitAll
    public Response login(@Valid LoginRequest request, @Context HttpServletRequest httpRequest) {

        try {
            String ipAddress = getClientIp(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");

            AuthBean.LoginResult result = authBean.authenticate(request.getEmail(), request.getPassword(), ipAddress, userAgent);

            if (!result.isSuccess()) {
                return Response.status(Response.Status.UNAUTHORIZED).entity(LoginResponse.failed(result.getMessage())).build();
            }

            // Build user info
            User user = result.getUser();
            LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(), user.getProfileUrl(), user.getOrgId(), result.getRoles());

            LoginResponse response = LoginResponse.success(result.getAccessToken(), result.getRefreshToken(), userInfo, accessTokenExpiry);

            return Response.ok(response).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ApiResponse.error("Login failed: " + e.getMessage())).build();
        }
    }

    /**
     * Register organization endpoint POST /api/auth/register-organization
     */
    @POST
    @Path("/register-organization")
    @PermitAll
    public Response registerOrganization(@Valid RegisterOrgRequest request, @Context HttpServletRequest httpRequest) {
        try {

            AuthBean.RegistrationResult result = authBean.registerOrganization(request.getOwnerEmail(),
                    request.getOwnerPassword(),
                    request.getOwnerName(),
                    request.getPhone(),
                    request.getOrgName(),
                    request.getOrgEmail(),
                    request.getOrgType(),
                    request.getSector(),
                    request.getRegistrationNo(),
                    request.getPhone(),
                    request.getAddress(),
                    request.getCity(),
                    request.getState(),
                    request.getPincode().toString(),
                    request.getWebsite(),
                    getClientIp(httpRequest),
                    httpRequest.getHeader("User-Agent"));

            return Response.status(Response.Status.CREATED).entity(ApiResponse.success(Map.of("userId", result.getUser().getId(), "email", result.getUser().getEmail(), "message", "Registration successful. Awaiting organization approval."))).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ApiResponse.error(e.getMessage())).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ApiResponse.error("Registration failed: " + e.getMessage())).build();
        }
    }

    /**
     * Invite admin endpoint POST /api/auth/invite-admin Only ORG_OWNER and
     * PLATFORM_ADMIN can invite
     */
    @POST
    @Path("/invite-admin")
    @RolesAllowed({"PLATFORM_ADMIN", "ORG_OWNER"})
    public Response inviteAdmin(@Valid InviteAdminRequest request, @Context SecurityContext securityContext) {

        try {
            Long invitedBy = Long.parseLong(securityContext.getUserPrincipal().getName());

            // Get inviter's org ID
            User inviter = userBean.findById(invitedBy).orElseThrow(() -> new IllegalArgumentException("Inviter not found"));

            // Generate invite token
            String inviteToken = authBean.inviteUser(request.getEmail(), request.getFullName(), inviter.getOrgId(), request.getRoles(), invitedBy);

            // TODO: Send email with invite link
            String inviteLink = String.format("http://localhost:5173/accept-invite?token=%s", inviteToken);

            // 4. Publish Kafka event
            kafkaProducer.sendEvent("user.created", Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "email", request.getEmail(),
                    "fullName", request.getFullName(),
                    "roles", request.getRoles(),
                    "inviteToken", inviteToken,
                    "inviteLink",inviteLink,
                    "timestamp", System.currentTimeMillis()
            ));
            return Response.status(Response.Status.CREATED).entity(ApiResponse.success(Map.of("inviteToken", inviteToken, "inviteLink", inviteLink, "message", "Invitation sent successfully"))).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ApiResponse.error(e.getMessage())).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ApiResponse.error("Invite failed: " + e.getMessage())).build();
        }
    }

    /**
     * Accept invite endpoint POST /api/auth/accept-invite
     */
    @POST
    @Path("/accept-invite")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
//    @PermitAll
    public Response acceptInvite(@Valid AcceptInviteRequest request) {
        try {
            String token = request.getToken();
            String[] parts = token.split("\\.");
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));

            JsonObject json = Json.createReader(new StringReader(payloadJson)).readObject();

            String type = json.getString("type");
            long userId = Long.parseLong(json.getString("sub"));



            // Verify token type
            if (!"INVITE".equals(type)) {
                return Response.status(Response.Status.BAD_REQUEST).entity(ApiResponse.error("Invalid invite token")).build();
            }


            System.out.println(userId);

            Optional<User> user = userBean.findById(userId);

            authBean.acceptInvite(userId, request.getPassword());
            // 🔥 PUBLISH user.accepted EVENT
            kafkaProducer.sendEvent("user.accepted", Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "userId", userId,
                    "email", user.get().getEmail(),
                    "fullName", user.get().getFullName(),
                    "timestamp", System.currentTimeMillis()
            ));

            return Response.ok(ApiResponse.success(null, "Account activated successfully. Please login.")).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ApiResponse.error(e.getMessage())).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ApiResponse.error("Activation failed: " + e.getMessage())).build();
        }
    }

    /**
     * Refresh token endpoint POST /api/auth/refresh
     */
    @POST
    @Path("/refresh")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
//    @PermitAll
    public Response refreshToken(@Valid RefreshTokenRequest request) {
        try {
//            System.out.println(request.getRefreshToken());
            String newAccessToken = authBean.refreshAccessToken(request.getRefreshToken());
//            System.out.println(newAccessToken);


            return Response.ok(ApiResponse.success(Map.of("accessToken", newAccessToken, "expiresIn", accessTokenExpiry != null ? accessTokenExpiry : "1 hour"), "Token refreshed successfully")).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ApiResponse.error(e.getMessage())).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ApiResponse.error("Token refresh failed: " + e.getMessage())).build();
        }
    }

    /**
     * Get current user profile GET /api/auth/profile
     */
    @GET
    @Path("/profile")
    @RolesAllowed({"PLATFORM_ADMIN", "ORG_OWNER", "ORG_ADMIN", "DISTRIBUTOR", "CITIZEN"})
    public Response getProfile(@Context SecurityContext securityContext) {
        try {
            Long userId = Long.parseLong(securityContext.getUserPrincipal().getName());

            User user = userBean.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));

            List<String> roles = roleBean.getUserRoles(userId);

            LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(), user.getProfileUrl(), user.getOrgId(), roles);

            return Response.ok(ApiResponse.success(userInfo)).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ApiResponse.error("Failed to fetch profile: " + e.getMessage())).build();
        }
    }

    /**
     * Get current user profile GET /api/auth/user?id=userId
     */
    @GET
    @Path("/user/{id}")
    @RolesAllowed({"PLATFORM_ADMIN", "ORG_OWNER", "ORG_ADMIN", "DISTRIBUTOR", "CITIZEN"})
    public Response getUserById( @PathParam("id") Long userId) {
        try {

            User user = userBean.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));

            List<String> roles = roleBean.getUserRoles(userId);

            LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(), user.getProfileUrl(), user.getOrgId(), roles);

            return Response.ok(ApiResponse.success(userInfo)).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ApiResponse.error("Failed to fetch profile: " + e.getMessage())).build();
        }
    }

    /**
     * Get orgs admin GET /api/auth/org?orgId
     */
    @GET
    @Path("/org")
    @RolesAllowed({"PLATFORM_ADMIN", "ORG_OWNER"})
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getMyAdmins(@QueryParam("orgId") Long orgId) {
        try {
            List<User> users = userBean.findByOrgId(orgId);
            return Response.ok(ApiResponse.success(users)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ApiResponse.error("Failed to fetch org admins: " + e.getMessage())).build();
        }
    }

    /**
     * Get orgs admin GET /api/auth/org?orgId
     */

    @GET
    @Path("/member")
    @RolesAllowed({"PLATFORM_ADMIN", "ORG_OWNER", "ORG_ADMIN"})
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getMember(@QueryParam("memberId") Long memberId) {
        try {
            Optional<User> user = userBean.findById(memberId);
            return Response.ok(ApiResponse.success(user)).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ApiResponse.error("Failed to fetch member: " + e.getMessage())).build();
        }
    }

    /**
     * Update profile with picture PUT /api/auth/profile
     */
    @PUT
    @Path("/profile")
    @RolesAllowed({"PLATFORM_ADMIN", "ORG_OWNER", "ORG_ADMIN", "DISTRIBUTOR", "CITIZEN"})
    public Response updateProfile(@Valid ProfileUpdateRequest request, @Context SecurityContext securityContext) {

        try {
            Long userId = Long.parseLong(securityContext.getUserPrincipal().getName());

            byte[] profilePicture = null;
            if (request.getProfilePictureBase64() != null && !request.getProfilePictureBase64().isEmpty()) {
                // Decode base64 image
                profilePicture = Base64.getDecoder().decode(request.getProfilePictureBase64());
            }

            User updatedUser = authBean.updateProfile(userId, request.getFullName(), request.getPhone(), profilePicture);

            return Response.ok(ApiResponse.success(Map.of("profileUrl", updatedUser.getProfileUrl(), "fullName", updatedUser.getFullName(), "phone", updatedUser.getPhone()), "Profile updated successfully")).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ApiResponse.error("Profile update failed: " + e.getMessage())).build();
        }
    }

    /**
     * Logout endpoint POST /api/auth/logout
     */
    @POST
    @Path("/logout")
    @RolesAllowed({"PLATFORM_ADMIN", "ORG_OWNER", "ORG_ADMIN", "DISTRIBUTOR", "CITIZEN"})
    public Response logout(@Valid RefreshTokenRequest request, @Context SecurityContext securityContext) {

        try {
            // Revoke refresh token
            authBean.logout(request.getRefreshToken());

            return Response.ok(ApiResponse.success(null, "Logged out successfully")).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ApiResponse.error("Logout failed: " + e.getMessage())).build();
        }
    }

    @POST
    @Path("/profile/picture")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed({"PLATFORM_ADMIN", "ORG_OWNER", "ORG_ADMIN", "DISTRIBUTOR", "CITIZEN"})
    public Response uploadProfilePicture(@Context SecurityContext securityContext, @FormDataParam("file") InputStream uploadedInputStream, @FormDataParam("file") FormDataContentDisposition fileDetail) throws IOException {

        Long userId = Long.parseLong(securityContext.getUserPrincipal().getName());

        // Read file bytes
        byte[] fileBytes = uploadedInputStream.readAllBytes();

        // Upload and update user
        User user = authBean.updateProfile(userId, null, null, fileBytes);

        return Response.ok(Map.of("profileUrl", user.getProfileUrl(), "message", "Profile picture updated successfully")).build();
    }


    // Helper methods
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
