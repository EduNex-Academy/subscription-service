package com.edu.subscription_service.service;

import com.edu.subscription_service.enums.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class AuthService {

    /**
     * Extracts user ID from JWT token
     */
    public String extractUserIdFromToken(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            log.warn("No valid JWT authentication found");
            return null;
        }
        
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("sub");
        log.debug("Extracted user ID: {}", userId);
        return userId;
    }

    /**
     * Extracts user ID from current security context
     */
    public String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return extractUserIdFromToken(authentication);
    }

    /**
     * Extracts user ID as UUID from current security context
     */
    public UUID getCurrentUserIdAsUUID() {
        String userId = getCurrentUserId();
        if (userId == null) {
            return null;
        }
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for user ID: {}", userId);
            return null;
        }
    }

    /**
     * Extracts user ID as UUID from given authentication
     */
    public UUID extractUserIdAsUUID(Authentication authentication) {
        String userId = extractUserIdFromToken(authentication);
        if (userId == null) {
            return null;
        }
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for user ID: {}", userId);
            return null;
        }
    }

    /**
     * Extracts user roles from JWT token
     */
    public List<String> extractUserRoles(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            log.warn("No valid JWT authentication found");
            return List.of();
        }
        
        Jwt jwt = (Jwt) authentication.getPrincipal();
        
        // Try different claim names for roles
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null) {
            roles = jwt.getClaimAsStringList("authorities");
        }
        if (roles == null) {
            String role = jwt.getClaimAsString("role");
            if (role != null) {
                roles = List.of(role);
            }
        }
        
        log.debug("Extracted user roles: {}", roles);
        return roles != null ? roles : List.of();
    }

    /**
     * Gets current user roles from security context
     */
    public List<String> getCurrentUserRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return extractUserRoles(authentication);
    }

    /**
     * Checks if current user has a specific role
     */
    public boolean hasRole(UserRole role) {
        List<String> userRoles = getCurrentUserRoles();
        return userRoles.contains(role.name()) || userRoles.contains("ROLE_" + role.name());
    }

    /**
     * Checks if current user is admin
     */
    public boolean isAdmin() {
        return hasRole(UserRole.ADMIN);
    }

    /**
     * Checks if current user is instructor
     */
    public boolean isInstructor() {
        return hasRole(UserRole.INSTRUCTOR);
    }

    /**
     * Checks if current user is student
     */
    public boolean isStudent() {
        return hasRole(UserRole.STUDENT);
    }

    /**
     * Extracts user email from JWT token
     */
    public String extractUserEmail(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            log.warn("No valid JWT authentication found");
            return null;
        }
        
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String email = jwt.getClaimAsString("email");
        log.debug("Extracted user email: {}", email);
        return email;
    }

    /**
     * Gets current user email from security context
     */
    public String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return extractUserEmail(authentication);
    }

    /**
     * Extracts user name from JWT token
     */
    public String extractUserName(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            log.warn("No valid JWT authentication found");
            return null;
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String name = jwt.getClaimAsString("name");
        if (name == null) {
            name = jwt.getClaimAsString("preferred_username");
        }
        if (name == null) {
            name = jwt.getClaimAsString("given_name");
        }
        log.debug("Extracted user name: {}", name);
        return name;
    }

    /**
     * Gets current user name from security context
     */
    public String getCurrentUserName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return extractUserName(authentication);
    }

    /**
     * Validates if the current user can access resources for the given user ID
     * Admins can access any user's resources, others can only access their own
     */
    public boolean canAccessUserResources(UUID targetUserId) {
        if (isAdmin()) {
            return true;
        }
        
        UUID currentUserId = getCurrentUserIdAsUUID();
        return currentUserId != null && currentUserId.equals(targetUserId);
    }
}
