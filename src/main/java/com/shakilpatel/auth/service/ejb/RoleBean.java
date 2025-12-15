package com.shakilpatel.auth.service.ejb;

import com.shakilpatel.auth.service.entity.UserRole;
import com.shakilpatel.auth.service.entity.Role;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;

@Stateless
public class RoleBean {

    @PersistenceContext(unitName = "authPU")
    private EntityManager em;

    /**
     * Find role by name
     */
    public Optional<Role> findByName(String roleName) {
        try {
            TypedQuery<Role> query = em.createQuery(
                    "SELECT r FROM Role r WHERE r.roleName = :roleName", Role.class);
            query.setParameter("roleName", roleName);
            return Optional.of(query.getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Get all roles for a user
     */
    public List<String> getUserRoles(Long userId) {
        TypedQuery<String> query = em.createQuery(
                "SELECT r.roleName FROM UserRole ur JOIN ur.role r WHERE ur.user.id = :userId",
                String.class);
        query.setParameter("userId", userId);
        return query.getResultList();
    }

    /**
     * Assign role to user
     */
    public void assignRole(Long userId, String roleName, Long assignedBy) {
        // Check if already assigned
        Long count = em.createQuery(
                "SELECT COUNT(ur) FROM UserRole ur "
                + "WHERE ur.user.id = :userId AND ur.role.roleName = :roleName", Long.class)
                .setParameter("userId", userId)
                .setParameter("roleName", roleName)
                .getSingleResult();

        if (count > 0) {
            return; // Already assigned
        }

        // Create assignment
        Role role = findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));

        UserRole userRole = new UserRole();
        userRole.setUser(em.getReference(com.shakilpatel.auth.service.entity.User.class, userId));
        userRole.setRole(role);
        userRole.setAssignedBy(assignedBy);

        em.persist(userRole);
    }

    /**
     * Remove role from user
     */
    public void removeRole(Long userId, String roleName) {
        em.createQuery(
                "DELETE FROM UserRole ur WHERE ur.user.id = :userId AND ur.role.roleName = :roleName")
                .setParameter("userId", userId)
                .setParameter("roleName", roleName)
                .executeUpdate();
    }
}
