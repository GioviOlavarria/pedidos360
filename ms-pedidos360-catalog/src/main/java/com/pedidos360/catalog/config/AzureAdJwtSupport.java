package com.pedidos360.catalog.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

import java.util.*;

/**
 * Utilidades de validación de JWT de Microsoft Entra ID (Azure AD) multi-tenant.
 *
 * La app acepta cuentas de cualquier tenant y cuentas personales de Microsoft, por lo que
 * no se puede fijar un único issuer. En su lugar se valida:
 *  - aud: el token debe haber sido emitido para NUESTRA API (API_CLIENT_ID).
 *  - iss: debe ser el issuer de Microsoft correspondiente al tenant del propio token (claim tid).
 *
 * Roles: ROLE_Admin si el correo está en la lista de administradores (o si Azure entrega el
 * App Role "Admin"); cualquier otro usuario autenticado es ROLE_Customer.
 */
public final class AzureAdJwtSupport {

    public static final String JWK_SET_URI = "https://login.microsoftonline.com/common/discovery/v2.0/keys";

    private static final List<String> EMAIL_CLAIMS = List.of("preferred_username", "email", "upn", "unique_name");

    private AzureAdJwtSupport() {
    }

    public static OAuth2TokenValidator<Jwt> tokenValidator(String apiClientId) {
        if (apiClientId == null || apiClientId.isBlank()) {
            throw new IllegalStateException("Falta configurar API_CLIENT_ID (app.security.api-client-id)");
        }
        Set<String> audiences = Set.of(apiClientId, "api://" + apiClientId);

        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            List<String> aud = jwt.getAudience();
            if (aud != null && aud.stream().anyMatch(audiences::contains)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "El token no fue emitido para esta API (aud)", null));
        };

        OAuth2TokenValidator<Jwt> issuerValidator = jwt -> {
            String tid = jwt.getClaimAsString("tid");
            String iss = jwt.getClaimAsString("iss");
            if (tid != null && iss != null && (
                    iss.equals("https://login.microsoftonline.com/" + tid + "/v2.0")
                            || iss.equals("https://sts.windows.net/" + tid + "/"))) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Issuer no válido para el tenant del token", null));
        };

        return new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), audienceValidator, issuerValidator);
    }

    public static Optional<String> resolveEmail(Jwt jwt) {
        for (String claim : EMAIL_CLAIMS) {
            String value = jwt.getClaimAsString(claim);
            if (value != null && value.contains("@")) {
                return Optional.of(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Optional.empty();
    }

    public static Set<String> parseEmails(String csv) {
        Set<String> emails = new HashSet<>();
        if (csv != null) {
            for (String e : csv.split(",")) {
                if (!e.isBlank()) emails.add(e.trim().toLowerCase(Locale.ROOT));
            }
        }
        return emails;
    }

    /**
     * @param adminEmails   correos con rol Admin
     * @param adminTenantId si no está vacío, el Admin por correo además debe pertenecer a este tenant
     */
    public static Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter(Set<String> adminEmails,
                                                                                     String adminTenantId) {
        return jwt -> {
            Set<GrantedAuthority> authorities = new HashSet<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_Customer"));

            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
            }

            boolean tenantOk = adminTenantId == null || adminTenantId.isBlank()
                    || adminTenantId.equalsIgnoreCase(jwt.getClaimAsString("tid"));
            boolean isAdminEmail = resolveEmail(jwt).map(adminEmails::contains).orElse(false);
            if (tenantOk && isAdminEmail) {
                authorities.add(new SimpleGrantedAuthority("ROLE_Admin"));
            }
            return authorities;
        };
    }
}
