package dto;

import java.util.List;

public class LoginResponse {

    private boolean success;
    private String message;
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private UserInfo user;

    // Nested class for user info
    public static class UserInfo {

        private Long id;
        private String email;
        private String fullName;
        private String phone;
        private String profileUrl;
        private Long orgId;
        private List<String> roles;

        // Constructors
        public UserInfo() {
        }

        public UserInfo(Long id, String email, String fullName, String phone,
                String profileUrl, Long orgId, List<String> roles) {
            this.id = id;
            this.email = email;
            this.fullName = fullName;
            this.phone = phone;
            this.profileUrl = profileUrl;
            this.orgId = orgId;
            this.roles = roles;
        }

        // Getters and Setters
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getProfileUrl() {
            return profileUrl;
        }

        public void setProfileUrl(String profileUrl) {
            this.profileUrl = profileUrl;
        }

        public Long getOrgId() {
            return orgId;
        }

        public void setOrgId(Long orgId) {
            this.orgId = orgId;
        }

        public List<String> getRoles() {
            return roles;
        }

        public void setRoles(List<String> roles) {
            this.roles = roles;
        }
    }

    // Static factory methods
    public static LoginResponse success(String accessToken, String refreshToken,
            UserInfo user, Long expiresIn) {
        LoginResponse response = new LoginResponse();
        response.success = true;
        response.message = "Login successful";
        response.accessToken = accessToken;
        response.refreshToken = refreshToken;
        response.user = user;
        response.expiresIn = expiresIn;
        return response;
    }

    public static LoginResponse failed(String message) {
        LoginResponse response = new LoginResponse();
        response.success = false;
        response.message = message;
        return response;
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public UserInfo getUser() {
        return user;
    }

    public void setUser(UserInfo user) {
        this.user = user;
    }
}
