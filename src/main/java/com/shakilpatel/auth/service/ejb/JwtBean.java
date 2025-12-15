package com.shakilpatel.auth.service.ejb;

import io.smallrye.jwt.build.Jwt;
import jakarta.ejb.Stateless;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Stateless
public class JwtBean {

    
    @ConfigProperty(name = "jwt.access.token.expiry", defaultValue = "3600")
    private Long accessTokenExpiry;

    @ConfigProperty(name = "mp.jwt.verify.issuer", defaultValue = "auth-service")
    private String issuer;

    /**
     * Generate access token (JWT)
     */
    public String generateAccessToken(
            Long userId,
            String email,
            String fullName,
            String profileUrl,
            Long orgId,
            Set<String> roles) {
        System.out.println(accessTokenExpiry);

        Instant now = Instant.now();
        Instant expiryTime = now.plusSeconds(accessTokenExpiry != null ? accessTokenExpiry : 3600);

        return Jwt.claims()
                .subject(userId.toString())
                .claim("email", email)
                .claim("name", fullName)
                .claim("profile", profileUrl != null ? profileUrl : "")
                .claim("orgId", orgId != null ? orgId : "")
                .claim("roles", roles) // Array of role names
                .groups(roles) // MicroProfile JWT standard claim
                .issuer(issuer == null ? ConfigProvider.getConfig().getValue("mp.jwt.verify.issuer", String.class) : issuer)
                .audience("boloindia-services")
                .issuedAt(now)
                .expiresAt(expiryTime)
                .sign();
    }

    /**
     * Generate invite token (for email invitations)
     */
    public String generateInviteToken(Long userId, String email) {
        Instant expiryTime = Instant.now().plusSeconds(172800); // 48 hours

        return Jwt.claims()
                .subject(userId.toString())
                .claim("email", email)
                .claim("type", "INVITE")
                .issuer(issuer == null ? ConfigProvider.getConfig().getValue("mp.jwt.verify.issuer", String.class) : issuer)
                .expiresAt(expiryTime)
                .sign();
    }
}
