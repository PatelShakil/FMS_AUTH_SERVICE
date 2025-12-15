package com.shakilpatel.auth.service.ejb;

import com.shakilpatel.auth.service.entity.User;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;

@Stateless
public class UserBean {

    @PersistenceContext(unitName = "authPU")
    private EntityManager em;

    /**
     * Find user by email
     */
    public Optional<User> findByEmail(String email) {
        try {
            TypedQuery<User> query = em.createNamedQuery("User.findByEmail", User.class);
            query.setParameter("email", email);
            return Optional.of(query.getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Find user by ID
     */
    public Optional<User> findById(Long id) {
        User user = em.find(User.class, id);
        return Optional.ofNullable(user);
    }

    /**
     * Find all users by organization ID
     */
    public List<User> findByOrgId(Long orgId) {
        TypedQuery<User> query = em.createNamedQuery("User.findByOrgId", User.class);
        query.setParameter("orgId", orgId);
        return query.getResultList();
    }

    /**
     * Create new user
     */
    public User create(User user) {
        em.persist(user);
        em.flush(); // Force ID generation
        return user;
    }

    /**
     * Update user
     */
    public User update(User user) {
        return em.merge(user);
    }

    /**
     * Delete user
     */
    public void delete(Long userId) {
        User user = em.find(User.class, userId);
        if (user != null) {
            em.remove(user);
        }
    }

    /**
     * Check if email exists
     */
    public boolean emailExists(String email) {
        Long count = em.createQuery(
                "SELECT COUNT(u) FROM User u WHERE u.email = :email", Long.class)
                .setParameter("email", email)
                .getSingleResult();
        return count > 0;
    }

    /**
     * Activate user account
     */
    public void activateUser(Long userId) {
        User user = em.find(User.class, userId);
        if (user != null) {
            user.setIsActive(true);
            user.setEmailVerified(true);
            em.merge(user);
        }
    }
    
    // Add to existing UserBean.java
    /**
     * Find users by account status
     */
    public List<User> findByAccountStatus(String accountStatus) {
        TypedQuery<User> query = em.createNamedQuery("User.findByAccountStatus", User.class);
        query.setParameter("accountStatus", accountStatus);
        return query.getResultList();
    }

    /**
     * Find pending distributors
     */
    public List<User> findPendingDistributors() {
        TypedQuery<User> query = em.createNamedQuery("User.findPendingDistributors", User.class);
        return query.getResultList();
    }

    /**
     * Find active distributors (for assignment dropdown)
     */
    public List<User> findActiveDistributors() {
        return em.createQuery(
                "SELECT u FROM User u "
                + "JOIN u.userRoles ur "
                + "JOIN ur.role r "
                + "WHERE r.roleName = 'DISTRIBUTOR' "
                + "AND u.accountStatus = 'ACTIVE' "
                + "AND u.isActive = TRUE "
                + "ORDER BY u.fullName ASC",
                User.class
        ).getResultList();
    }
    /**
     * Find all distributors
     */
    public List<User> findAllDistributors() {
        return em.createQuery(
                "SELECT u FROM User u "
                + "JOIN u.userRoles ur "
                + "JOIN ur.role r "
                + "WHERE r.roleName = 'DISTRIBUTOR' "
//                + "AND u.accountStatus = 'ACTIVE' "
//                + "AND u.isActive = TRUE "
                + "ORDER BY u.fullName ASC",
                User.class
        ).getResultList();
    }

    /**
     * Count pending distributors
     */
    public Long countPendingDistributors() {
        return em.createQuery(
                "SELECT COUNT(u) FROM User u "
                + "JOIN u.userRoles ur "
                + "JOIN ur.role r "
                + "WHERE r.roleName = 'DISTRIBUTOR' "
                + "AND u.accountStatus = 'PENDING'",
                Long.class
        ).getSingleResult();
    }

}
