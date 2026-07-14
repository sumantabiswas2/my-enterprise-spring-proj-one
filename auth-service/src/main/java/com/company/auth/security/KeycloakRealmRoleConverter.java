package com.company.auth.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Maps Keycloak realm and client roles from a JWT into Spring Security authorities with the
 * {@code ROLE_} prefix, enabling RBAC (FR-AUTH-03, NFR-SEC-02).
 *
 * <p>Keycloak places realm roles under {@code realm_access.roles} and client roles under
 * {@code resource_access.<client>.roles}.
 */
public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Stream<String> realmRoles = Stream.empty();
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
            realmRoles = roles.stream().map(Object::toString);
        }

        Stream<String> clientRoles = Stream.empty();
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess != null) {
            clientRoles = resourceAccess.values().stream()
                .filter(v -> v instanceof Map)
                .map(v -> (Map<String, Object>) v)
                .map(m -> m.get("roles"))
                .filter(r -> r instanceof Collection<?>)
                .flatMap(r -> ((Collection<?>) r).stream())
                .map(Object::toString);
        }

        return Stream.concat(realmRoles, clientRoles)
            .distinct()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .map(GrantedAuthority.class::cast)
            .toList();
    }

    static List<GrantedAuthority> empty() {
        return List.of();
    }
}
