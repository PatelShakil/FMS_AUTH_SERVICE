package com.shakilpatel.auth.service.ejb;

import com.shakilpatel.auth.service.entity.RefreshToken;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Stateless
public class RefreshTokenBean {

    @PersistenceContext(unitName = "authPU")
    private EntityManager em;

    /**
     * Create refresh token
     */
    public String createRefreshToken(Long userId, String ipAddress, String deviceInfo) {
        // Generate random token
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = BCrypt.hashpw(rawToken, BCrypt.gensalt());

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(userId);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(7));
        refreshToken.setIpAddress(ipAddress);
        refreshToken.setDeviceInfo(deviceInfo);

        em.persist(refreshToken);

        return rawToken; // Return plain token to client
    }

    /**
     * Validate refresh token
     */
    public Optional<RefreshToken> validateToken(String rawToken) {
        try {
            // Get all non-revoked tokens (we'll check hash manually)
            var tokens = em.createQuery(
                    "SELECT rt FROM RefreshToken rt WHERE rt.revoked = false "
                    + "AND rt.expiresAt > :now", RefreshToken.class)
                    .setParameter("now", LocalDateTime.now())
                    .getResultList();

            // Find matching token
            for (RefreshToken token : tokens) {
                if (BCrypt.checkpw(rawToken, token.getTokenHash())) {
                    return Optional.of(token);
                }
            }

            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Revoke refresh token
     */
    public void revokeToken(String rawToken) {
        validateToken(rawToken).ifPresent(token -> {
            token.setRevoked(true);
            em.merge(token);
        });
    }

    /**
     * Revoke all tokens for a user (used during suspension)
     */
    public void revokeAllUserTokens(Long userId) {
        em.createQuery("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.userId = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
    }

}
