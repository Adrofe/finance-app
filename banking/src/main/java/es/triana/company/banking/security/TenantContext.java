package es.triana.company.banking.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class TenantContext {

    /**
     * Extracts the tenant_id from the authenticated JWT.
     * 
     * @return The tenant_id of the authenticated user
     * @throws IllegalStateException if there is no authentication or no tenant_id in the token
     */
    public Long getCurrentTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found");
        }

        if (!(authentication.getPrincipal() instanceof Jwt)) {
            throw new IllegalStateException("Authenticated principal is not a valid JWT");
        }

        Jwt jwt = (Jwt) authentication.getPrincipal();
        Object tenantIdClaim = jwt.getClaim("tenant_id");

        if (tenantIdClaim == null) {
            throw new IllegalStateException("Token does not contain 'tenant_id' claim");
        }

        // The claim can come as String or Number
        if (tenantIdClaim instanceof Number) {
            return ((Number) tenantIdClaim).longValue();
        } else if (tenantIdClaim instanceof String) {
            try {
                return Long.parseLong((String) tenantIdClaim);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("tenant_id is not a valid number: " + tenantIdClaim);
            }
        } else {
            throw new IllegalStateException("tenant_id has an unexpected format: " + tenantIdClaim.getClass());
        }
    }

    /**
     * Gets the username of the authenticated user.
     * 
     * @return The preferred_username from the token
     */
    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        if (authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            return jwt.getClaimAsString("preferred_username");
        }

        return authentication.getName();
    }
}
