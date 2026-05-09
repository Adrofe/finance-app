package es.triana.company.wealth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class TenantContext {

    public Long getCurrentTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found");
        }

        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Authenticated principal is not a valid JWT");
        }

        Object tenantIdClaim = jwt.getClaim("tenant_id");
        if (tenantIdClaim == null) {
            throw new IllegalStateException("Token does not contain 'tenant_id' claim");
        }

        if (tenantIdClaim instanceof Number number) {
            return number.longValue();
        }

        if (tenantIdClaim instanceof String value) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ex) {
                throw new IllegalStateException("tenant_id is not a valid number: " + tenantIdClaim);
            }
        }

        throw new IllegalStateException("tenant_id has an unexpected format: " + tenantIdClaim.getClass());
    }
}
