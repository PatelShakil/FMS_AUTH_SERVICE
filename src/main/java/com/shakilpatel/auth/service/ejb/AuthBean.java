package com.shakilpatel.auth.service.ejb;

import com.shakilpatel.auth.service.entity.LoginAudit;
import com.shakilpatel.auth.service.entity.Role;
import com.shakilpatel.auth.service.entity.User;
import com.shakilpatel.auth.service.entity.UserRole;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;

import org.eclipse.microprofile.config.ConfigProvider;
import org.mindrot.jbcrypt.BCrypt;
import java.util.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kafka.KafkaProducerBean;
import kafka.events.OrgRegistrationEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Stateless
public class AuthBean {

    @PersistenceContext(unitName = "authPU")
    private EntityManager em;

    @Inject
    private UserBean userBean;
    
    @Inject
    private KafkaProducerBean kafkaProducer;

    @Inject
    private RoleBean roleBean;

    @Inject
    private JwtBean jwtBean;

    @Inject
    private RefreshTokenBean refreshTokenBean;

    @Inject
    private FileStorageBean fileStorageBean;

    @ConfigProperty(name = "kafka.producer.topic.org-registration")
    private String orgRegistrationTopic;
    
    private static final Logger LOGGER = Logger.getLogger(AuthBean.class.getName());


    /**
     * Authenticate user (login)
     */
//    public LoginResult authenticateDeprecated(String email, String password, String ipAddress, String userAgent) {
//        // Find user
//        Optional<User> userOpt = userBean.findByEmail(email);
//
//        
//        if (userOpt.isEmpty()) {
//            logFailedLogin(null, ipAddress, userAgent, "User not found");
//            return LoginResult.failed("Invalid email or password");
//        }
//
//        User user = userOpt.get();
//      
//
//        // Check if active
//        if (!user.getIsActive()) {
//            logFailedLogin(user.getId(), ipAddress, userAgent, "Account inactive");
//            return LoginResult.failed("Account is not active");
//        }
//
//        // Verify password
//        if (user.getPasswordHash() == null || !BCrypt.checkpw(password, user.getPasswordHash())) {
// 
//            logFailedLogin(user.getId(), ipAddress, userAgent, "Invalid password");
//            return LoginResult.failed("Invalid email or password");
//        }
//
//        // Get user roles
//        List<String> roles = roleBean.getUserRoles(user.getId());
//
//        if (roles.isEmpty()) {
//            logFailedLogin(user.getId(), ipAddress, userAgent, "No roles assigned");
//            return LoginResult.failed("Account has no roles assigned");
//        }
//
//        // Generate tokens
//        String accessToken = jwtBean.generateAccessToken(
//                user.getId(),
//                user.getEmail(),
//                user.getFullName(),
//                user.getProfileUrl(),
//                user.getOrgId(),
//                Set.copyOf(roles)
//        );
//
//        String refreshToken = refreshTokenBean.createRefreshToken(
//                user.getId(), ipAddress, userAgent
//        );
//
//        // Log successful login
//        logSuccessfulLogin(user.getId(), ipAddress, userAgent);
//
//        return LoginResult.success(accessToken, refreshToken, user, roles);
//    }

    /**
     * Authenticate user (login) - UPDATED WITH ACCOUNT STATUS CHECK
     */
    public LoginResult authenticate(String email, String password, String ipAddress, String userAgent) {
        // Find user
        Optional<User> userOpt = userBean.findByEmail(email);

        if (userOpt.isEmpty()) {
            logFailedLogin(null, ipAddress, userAgent, "User not found");
            return LoginResult.failed("Invalid email or password");
        }

        User user = userOpt.get();
//        System.out.println(BCrypt.hashpw(password, BCrypt.gensalt()));
//        System.out.println(user.getPasswordHash() + " HASH");

        // ============================================
        // NEW: Check account status FIRST
        // ============================================
        if (!user.getIsActive()) {
            String accountStatus = user.getAccountStatus();

            if ("PENDING".equals(accountStatus)) {
                logFailedLogin(user.getId(), ipAddress, userAgent, "Account pending approval");
                return LoginResult.failed("Your account is pending admin approval. You will be notified via email once approved.");
            } else if ("REJECTED".equals(accountStatus)) {
                logFailedLogin(user.getId(), ipAddress, userAgent, "Account rejected");
                String reason = user.getRejectionReason() != null
                        ? " Reason: " + user.getRejectionReason() : "";
                return LoginResult.failed("Your account application was rejected." + reason);
            } else if ("SUSPENDED".equals(accountStatus)) {
                logFailedLogin(user.getId(), ipAddress, userAgent, "Account suspended");
                String reason = user.getSuspensionReason() != null
                        ? " Reason: " + user.getSuspensionReason() : "";
                return LoginResult.failed("Your account has been suspended." + reason + " Please contact support.");
            }

            logFailedLogin(user.getId(), ipAddress, userAgent, "Account inactive");
            return LoginResult.failed("Account is not active");
        }

        // Verify password
        if (user.getPasswordHash() == null || !BCrypt.checkpw(password, user.getPasswordHash())) {
            logFailedLogin(user.getId(), ipAddress, userAgent, "Invalid password");
            return LoginResult.failed("Invalid email or password");
        }

        // Get user roles
        List<String> roles = roleBean.getUserRoles(user.getId());

        if (roles.isEmpty()) {
            logFailedLogin(user.getId(), ipAddress, userAgent, "No roles assigned");
            return LoginResult.failed("Account has no roles assigned");
        }

        // Generate tokens
        String accessToken = jwtBean.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getProfileUrl(),
                user.getOrgId(),
                Set.copyOf(roles)
        );

        String refreshToken = refreshTokenBean.createRefreshToken(
                user.getId(), ipAddress, userAgent
        );

        // Log successful login
        logSuccessfulLogin(user.getId(), ipAddress, userAgent);

        return LoginResult.success(accessToken, refreshToken, user, roles);
    }

    
    /**
     * Register new user (with password)
     */
    public User registerUser(String email, String password, String fullName,
            String phone, Long orgId, List<String> roleNames) {

        // Check if email exists
        if (userBean.emailExists(email)) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Hash password
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());

        // Create user
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setOrgId(orgId);
        user.setIsActive(false); // Inactive until verified/approved

        user = userBean.create(user);

        // Assign roles
        for (String roleName : roleNames) {
            roleBean.assignRole(user.getId(), roleName, null);
        }

        return user;
    }

    /**
     * Invite user (no password yet)
     */
    public String inviteUser(String email, String fullName, Long orgId,
            List<String> roleNames, Long invitedBy) {

        // Check if email exists
        if (userBean.emailExists(email)) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Create user without password
        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setOrgId(orgId);
        user.setIsActive(false);

        user = userBean.create(user);

        // Assign roles
        for (String roleName : roleNames) {
            roleBean.assignRole(user.getId(), roleName, invitedBy);
        }

        // Generate invite token
        return jwtBean.generateInviteToken(user.getId(), email);
    }

    /**
     * Accept invite and set password
     */
    public void acceptInvite(Long userId, String password) {
        User user = userBean.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
        user.setPasswordHash(passwordHash);
        user.setIsActive(true);
        user.setEmailVerified(true);

        userBean.update(user);
    }

    /**
     * Refresh access token
     */
    public String refreshAccessToken(String refreshToken) {
        var tokenOpt = refreshTokenBean.validateToken(refreshToken);

        if (tokenOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        Long userId = tokenOpt.get().getUserId();
        User user = userBean.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<String> roles = roleBean.getUserRoles(userId);

        return jwtBean.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getProfileUrl(),
                user.getOrgId(),
                Set.copyOf(roles)
        );
    }

    /**
     * Register organization with owner
     */
    public RegistrationResult registerOrganization(
            String email,
            String password,
            String fullName,
            String phone,
            String orgName,
            String orgEmail,
            String orgType,
            String sector,
            String registrationNo,
            String orgPhone,
            String address,
            String city,
            String state,
            String pincode,
            String website,
            String ipAddress,
            String userAgent) {

        try {
            // 1. Check if user already exists
            if (userBean.findByEmail(email).isPresent()) {
                return RegistrationResult.failed("Email already registered");
            }

            // 2. Create user (inactive until org is approved)
            User user = new User();
            user.setEmail(email);
            user.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
            user.setFullName(fullName);
            user.setPhone(phone);
            user.setIsActive(false);  // Will be activated when org is approved
            user.setEmailVerified(false);

            em.persist(user);
            em.flush();

            LOGGER.info("✅ User created: " + user.getId());

            // 3. Assign ORG_OWNER role
            Role ownerRole = roleBean.findByName("ORG_OWNER")
                    .orElseThrow(() -> new RuntimeException("ORG_OWNER role not found"));

            UserRole userRole = new UserRole();
            userRole.setUser(user);
            userRole.setRole(ownerRole);
            userRole.setAssignedBy(user.getId());

            em.persist(userRole);
            em.flush();

            LOGGER.info("✅ ORG_OWNER role assigned to user: " + user.getId());

            // 4. Publish Kafka event for org-service
            OrgRegistrationEvent event = new OrgRegistrationEvent();
            event.setEventId(UUID.randomUUID().toString());
            event.setUserId(user.getId());
            event.setEmail(user.getEmail());

            OrgRegistrationEvent.OrgData orgData = new OrgRegistrationEvent.OrgData();
            orgData.setOrgName(orgName);
            orgData.setOrgEmail(orgEmail);
            orgData.setOrgType(orgType);
            orgData.setSector(sector);
            orgData.setRegistrationNo(registrationNo);
            orgData.setPhone(orgPhone);
            orgData.setAddress(address);
            orgData.setPincode(pincode);
            orgData.setCity(city);
            orgData.setState(state);
            orgData.setPincode(pincode);
            orgData.setWebsite(website);

            event.setOrgData(orgData);

            orgRegistrationTopic = ConfigProvider.getConfig().getValue("kafka.producer.topic.org-registration", String.class);
            kafkaProducer.sendEvent(orgRegistrationTopic, event);

            LOGGER.info("✅ Kafka event published to: " + orgRegistrationTopic);

            // 5. Log registration activity
//            logActivity(user.getId(), "REGISTER_ORGANIZATION", ipAddress, userAgent);

            return RegistrationResult.success(user);

        } catch (Exception e) {
            LOGGER.severe("❌ Organization registration failed: " + e.getMessage());
            e.printStackTrace();
            return RegistrationResult.failed("Registration failed: " + e.getMessage());
        }
    }

    // Helper methods
    private void logSuccessfulLogin(Long userId, String ipAddress, String userAgent) {
        LoginAudit audit = new LoginAudit(userId, "SUCCESS", ipAddress);
        audit.setUserAgent(userAgent);
        em.persist(audit);
    }

    private void logFailedLogin(Long userId, String ipAddress, String userAgent, String reason) {
        LoginAudit audit = new LoginAudit(userId, "FAILED", ipAddress);
        audit.setUserAgent(userAgent);
        audit.setFailureReason(reason);
        em.persist(audit);
    }

    public User updateProfile(Long userId, String fullName, String phone, byte[] profilePicture)
            throws IOException {

        User user = userBean.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Update basic info
        if (fullName != null) {
            user.setFullName(fullName);
        }
        if (phone != null) {
            user.setPhone(phone);
        }

        // Upload profile picture if provided
        if (profilePicture != null && profilePicture.length > 0) {
            // Delete old picture if exists
            if (user.getProfileUrl() != null) {
                try {
                    fileStorageBean.deleteProfilePicture(user.getProfileUrl());
                } catch (Exception e) {
                    // Log but don't fail
                }
            }

            // Upload new picture
            String profileUrl = fileStorageBean.uploadProfilePicture(
                    profilePicture,
                    "profile.jpg",
                    userId
            );
            user.setProfileUrl(profileUrl);
        }

        return userBean.update(user);
    }

    /**
     * Inner class for login result
     */
    public static class LoginResult {

        private boolean success;
        private String message;
        private String accessToken;
        private String refreshToken;
        private User user;
        private List<String> roles;

        public static LoginResult success(String accessToken, String refreshToken,
                User user, List<String> roles) {
            LoginResult result = new LoginResult();
            result.success = true;
            result.accessToken = accessToken;
            result.refreshToken = refreshToken;
            result.user = user;
            result.roles = roles;
            return result;
        }

        public static LoginResult failed(String message) {
            LoginResult result = new LoginResult();
            result.success = false;
            result.message = message;
            return result;
        }

        // Getters
        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public User getUser() {
            return user;
        }

        public List<String> getRoles() {
            return roles;
        }
    }

    /**
     * Logout user
     */
    public void logout(String refreshToken) {
        refreshTokenBean.revokeToken(refreshToken);
    }
    
    // Add to existing AuthBean.java
    /**
     * Register new distributor (self-registration)
     * Allows existing users to register as distributors
     */
    public RegistrationResult registerDistributor(
            String email,
            String password,
            String fullName,
            String phone,
            String address) {

        try {
            // 1. Check if user already exists
            Optional<User> existingUserOpt = userBean.findByEmail(email);

            if (existingUserOpt.isPresent()) {
                // User exists - check if already a distributor
                User existingUser = existingUserOpt.get();

                // Check if user already has DISTRIBUTOR role
                boolean isDistributor = existingUser.getUserRoles().stream()
                        .anyMatch(ur -> "DISTRIBUTOR".equals(ur.getRole().getRoleName()));

                if (isDistributor) {
                    return RegistrationResult.failed("You are already registered as a distributor");
                }

                // User exists but not a distributor - add DISTRIBUTOR role
                LOGGER.info("🔄 Existing user registering as distributor: " + email);

                // Add DISTRIBUTOR role
                Role distributorRole = roleBean.findByName("DISTRIBUTOR")
                        .orElseThrow(() -> new RuntimeException("DISTRIBUTOR role not found"));

                UserRole userRole = new UserRole();
                userRole.setUser(existingUser);
                userRole.setRole(distributorRole);
                userRole.setAssignedBy(existingUser.getId()); // Self-assigned

                em.persist(userRole);
                em.flush();

                // Update account status to PENDING for distributor approval
                existingUser.setAccountStatus("PENDING");
                existingUser.setAppliedAt(LocalDateTime.now());
                existingUser.setPhone(phone);  // Update phone if provided
                em.merge(existingUser);
                em.flush();

                LOGGER.info("✅ DISTRIBUTOR role added to existing user: " + existingUser.getId());

                // Publish Kafka event
                kafkaProducer.sendEvent("distributor.registered", Map.of(
                        "eventId", UUID.randomUUID().toString(),
                        "userId", existingUser.getId(),
                        "email", existingUser.getEmail(),
                        "fullName", existingUser.getFullName(),
                        "phone", phone,
                        "existingUser", true,  // 🔥 Flag to indicate existing user
                        "appliedAt", existingUser.getAppliedAt().toString(),
                        "timestamp", System.currentTimeMillis()
                ));

                LOGGER.info("✅ Kafka event published: distributor.registered (existing user)");

                return RegistrationResult.success(existingUser);

            } else {
                // New user - create account
                LOGGER.info("🆕 New user registering as distributor: " + email);

                // 2. Create user with PENDING status
                User user = new User();
                user.setEmail(email);
                user.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
                user.setFullName(fullName);
                user.setPhone(phone);
                user.setIsActive(false);           // Cannot login until approved
                user.setEmailVerified(false);
                user.setAccountStatus("PENDING");  // Waiting for admin approval
                user.setAppliedAt(LocalDateTime.now());

                em.persist(user);
                em.flush();

                LOGGER.info("✅ New distributor user created: " + user.getId());

                // 3. Assign DISTRIBUTOR role
                Role distributorRole = roleBean.findByName("DISTRIBUTOR")
                        .orElseThrow(() -> new RuntimeException("DISTRIBUTOR role not found"));

                UserRole userRole = new UserRole();
                userRole.setUser(user);
                userRole.setRole(distributorRole);
                userRole.setAssignedBy(user.getId());

                em.persist(userRole);
                em.flush();

                LOGGER.info("✅ DISTRIBUTOR role assigned to new user: " + user.getId());

                // 4. Publish Kafka event
                kafkaProducer.sendEvent("distributor.registered", Map.of(
                        "eventId", UUID.randomUUID().toString(),
                        "userId", user.getId(),
                        "email", user.getEmail(),
                        "fullName", user.getFullName(),
                        "phone", user.getPhone(),
                        "existingUser", false,  // 🔥 Flag for new user
                        "appliedAt", user.getAppliedAt().toString(),
                        "timestamp", System.currentTimeMillis()
                ));

                LOGGER.info("✅ Kafka event published: distributor.registered (new user)");

                return RegistrationResult.success(user);
            }

        } catch (Exception e) {
            LOGGER.severe("❌ Distributor registration failed: " + e.getMessage());
            e.printStackTrace();
            return RegistrationResult.failed("Registration failed: " + e.getMessage());
        }
    }


    /**
     * Approve distributor
     */
    public void approveDistributor(Long userId, Long approvedBy) {
        User user = userBean.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!"PENDING".equals(user.getAccountStatus())) {
            throw new IllegalArgumentException("User is not in PENDING status");
        }

        // Check if user has DISTRIBUTOR role
        List<String> roles = roleBean.getUserRoles(userId);
        if (!roles.contains("DISTRIBUTOR")) {
            throw new IllegalArgumentException("User is not a distributor");
        }

        user.setAccountStatus("ACTIVE");
        user.setIsActive(true);
        user.setEmailVerified(true);
        user.setApprovedAt(LocalDateTime.now());
        user.setApprovedBy(approvedBy);
        user.setRejectionReason(null); // Clear any previous rejection

        userBean.update(user);

        // Publish Kafka event
        kafkaProducer.sendEvent("distributor.approved", Map.of(
                "eventId", UUID.randomUUID().toString(),
                "userId", userId,
                "email", user.getEmail(),
                "approvedBy", approvedBy,
                "approvedAt", user.getApprovedAt().toString(),
                "timestamp", System.currentTimeMillis()
        ));

        LOGGER.info("✅ Distributor approved: " + userId);
    }

    /**
     * Reject distributor
     */
    public void rejectDistributor(Long userId, Long rejectedBy, String reason) {
        User user = userBean.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!"PENDING".equals(user.getAccountStatus())) {
            throw new IllegalArgumentException("User is not in PENDING status");
        }

        user.setAccountStatus("REJECTED");
        user.setIsActive(false);
        user.setRejectionReason(reason);
        user.setApprovedAt(LocalDateTime.now()); // Track when decision was made
        user.setApprovedBy(rejectedBy);

        userBean.update(user);

        // Publish Kafka event
        kafkaProducer.sendEvent("distributor.rejected", Map.of(
                "eventId", UUID.randomUUID().toString(),
                "userId", userId,
                "email", user.getEmail(),
                "rejectedBy", rejectedBy,
                "reason", reason,
                "timestamp", System.currentTimeMillis()
        ));

        LOGGER.info("✅ Distributor rejected: " + userId);
    }

    /**
     * Suspend distributor
     */
    public void suspendDistributor(Long userId, Long suspendedBy, String reason) {
        User user = userBean.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!"ACTIVE".equals(user.getAccountStatus())) {
            throw new IllegalArgumentException("User is not in ACTIVE status");
        }

        user.setAccountStatus("SUSPENDED");
        user.setIsActive(false);
        user.setSuspendedAt(LocalDateTime.now());
        user.setSuspendedBy(suspendedBy);
        user.setSuspensionReason(reason);

        userBean.update(user);

        // Revoke all refresh tokens
        refreshTokenBean.revokeAllUserTokens(userId);

        // Publish Kafka event
        kafkaProducer.sendEvent("distributor.suspended", Map.of(
                "eventId", UUID.randomUUID().toString(),
                "userId", userId,
                "email", user.getEmail(),
                "suspendedBy", suspendedBy,
                "reason", reason,
                "timestamp", System.currentTimeMillis()
        ));

        LOGGER.info("✅ Distributor suspended: " + userId);
    }

    /**
     * Reactivate distributor
     */
    public void reactivateDistributor(Long userId, Long reactivatedBy) {
        User user = userBean.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!"SUSPENDED".equals(user.getAccountStatus())) {
            throw new IllegalArgumentException("User is not in SUSPENDED status");
        }

        user.setAccountStatus("ACTIVE");
        user.setIsActive(true);
        user.setSuspendedAt(null);
        user.setSuspendedBy(null);
        user.setSuspensionReason(null);

        userBean.update(user);

        // Publish Kafka event
        kafkaProducer.sendEvent("distributor.reactivated", Map.of(
                "eventId", UUID.randomUUID().toString(),
                "userId", userId,
                "email", user.getEmail(),
                "reactivatedBy", reactivatedBy,
                "timestamp", System.currentTimeMillis()
        ));

        LOGGER.info("✅ Distributor reactivated: " + userId);
    }

    
    

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class RegistrationResult {

        private boolean success;
        private String message;
        private User user;

        public static RegistrationResult success(User user) {
            return new RegistrationResult(true, "Registration successful. Waiting for admin approval.", user);
        }

        public static RegistrationResult failed(String message) {
            return new RegistrationResult(false, message, null);
        }
    }
}
