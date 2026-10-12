package com.example.serviceapp;

import java.util.function.Supplier;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

@Component
class PrivateApiAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final ServiceProperties properties;

    PrivateApiAuthorizationManager(ServiceProperties properties) {
        this.properties = properties;
    }

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        Authentication principal = authentication.get();
        boolean granted = principal != null && principal.isAuthenticated()
                && properties.getJwt().getRequiredScopes().stream()
                .allMatch(scope -> principal.getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().equals("SCOPE_" + scope)));
        return new AuthorizationDecision(granted);
    }
}
