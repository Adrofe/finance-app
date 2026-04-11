package es.triana.company.banking.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class TenantContext {

    /**
     * Extrae el tenant_id del JWT autenticado.
     * 
     * @return El tenant_id del usuario autenticado
     * @throws IllegalStateException si no hay autenticacion o no hay tenant_id en el token
     */
    public Long getCurrentTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No hay usuario autenticado");
        }

        if (!(authentication.getPrincipal() instanceof Jwt)) {
            throw new IllegalStateException("El principal no es un JWT valido");
        }

        Jwt jwt = (Jwt) authentication.getPrincipal();
        Object tenantIdClaim = jwt.getClaim("tenant_id");

        if (tenantIdClaim == null) {
            throw new IllegalStateException("El token no contiene claim 'tenant_id'");
        }

        // El claim puede venir como String o como Number
        if (tenantIdClaim instanceof Number) {
            return ((Number) tenantIdClaim).longValue();
        } else if (tenantIdClaim instanceof String) {
            try {
                return Long.parseLong((String) tenantIdClaim);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("El tenant_id no es un numero valido: " + tenantIdClaim);
            }
        } else {
            throw new IllegalStateException("El tenant_id tiene un formato inesperado: " + tenantIdClaim.getClass());
        }
    }

    /**
     * Obtiene el username del usuario autenticado.
     * 
     * @return El preferred_username del token
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
