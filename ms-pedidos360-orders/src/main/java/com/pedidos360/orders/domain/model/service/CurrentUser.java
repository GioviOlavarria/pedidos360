package com.pedidos360.orders.domain.model.service;

import com.pedidos360.orders.config.AzureAdJwtSupport;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Datos del usuario autenticado extraídos del JWT de Azure AD. */
public record CurrentUser(String email, String name, boolean admin, boolean operator) {

    public boolean canManageAllOrders() {
        return admin || operator;
    }

    public static CurrentUser from(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new IllegalStateException("Se requiere un usuario autenticado con JWT");
        }
        String email = AzureAdJwtSupport.resolveEmail(token.getToken()).orElse(null);
        String name = token.getToken().getClaimAsString("name");
        boolean admin = hasRole(authentication, "ROLE_Admin");
        boolean operator = hasRole(authentication, "ROLE_Operator");
        return new CurrentUser(email, name != null && !name.isBlank() ? name : email, admin, operator);
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(role::equals);
    }
}
