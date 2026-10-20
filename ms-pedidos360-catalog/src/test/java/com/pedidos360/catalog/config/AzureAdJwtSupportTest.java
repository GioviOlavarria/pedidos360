package com.pedidos360.catalog.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AzureAdJwtSupportTest {

    private static final String API = "f72e55f6-3128-43e5-bb85-6359e6cb33bf";
    private static final String TID = "11111111-2222-3333-4444-555555555555";

    private final OAuth2TokenValidator<Jwt> validator = AzureAdJwtSupport.tokenValidator(API);

    @Test
    void acceptsV2TokenForOurApiFromAnyTenant() {
        assertFalse(validator.validate(token(API, "https://login.microsoftonline.com/" + TID + "/v2.0", TID, Map.of())).hasErrors());
    }

    @Test
    void acceptsV1Token() {
        assertFalse(validator.validate(token("api://" + API, "https://sts.windows.net/" + TID + "/", TID, Map.of())).hasErrors());
    }

    @Test
    void rejectsTokenForAnotherApi() {
        assertTrue(validator.validate(token("otra-app", "https://login.microsoftonline.com/" + TID + "/v2.0", TID, Map.of())).hasErrors());
    }

    @Test
    void rejectsIssuerThatDoesNotMatchTenant() {
        assertTrue(validator.validate(token(API, "https://login.microsoftonline.com/otro/v2.0", TID, Map.of())).hasErrors());
    }

    @Test
    void adminEmailGetsAdminRole() {
        assertEquals(Set.of("ROLE_Admin", "ROLE_Customer"),
                roles(Map.of("preferred_username", "Gi.Olavarria@duocuc.cl"), ""));
    }

    @Test
    void otherEmailIsCustomer() {
        assertEquals(Set.of("ROLE_Customer"), roles(Map.of("preferred_username", "cliente@outlook.com"), ""));
    }

    @Test
    void adminTenantIsEnforcedWhenConfigured() {
        assertEquals(Set.of("ROLE_Customer"),
                roles(Map.of("preferred_username", "gi.olavarria@duocuc.cl"), "tenant-de-duoc"));
    }

    private Set<String> roles(Map<String, Object> claims, String adminTenant) {
        Jwt jwt = token(API, "https://login.microsoftonline.com/" + TID + "/v2.0", TID, claims);
        return AzureAdJwtSupport.authoritiesConverter(Set.of("gi.olavarria@duocuc.cl"), adminTenant)
                .convert(jwt).stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    private static Jwt token(String aud, String iss, String tid, Map<String, Object> extra) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256")
                .audience(java.util.List.of(aud)).claim("iss", iss).claim("tid", tid)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
        extra.forEach(b::claim);
        return b.build();
    }
}
