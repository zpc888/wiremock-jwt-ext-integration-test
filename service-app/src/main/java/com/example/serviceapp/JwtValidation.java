package com.example.serviceapp;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

final class JwtValidation {

    private JwtValidation() {
    }

    static OAuth2TokenValidator<Jwt> validator(ServiceProperties properties) {
        JwtTimestampValidator timestamps = new JwtTimestampValidator(properties.getJwt().getClockSkew());
        return new DelegatingOAuth2TokenValidator<>(
                timestamps,
                new RequiredExpirationValidator(),
                new IssuerValidator(properties.getJwt().getIssuer()),
                new AudienceValidator(properties.getJwt().getAudiences()),
                new ScopeShapeValidator(properties.getJwt().getScopeClaim()),
                new HeaderAndClaimsValidator(properties.getJwt()));
    }

    static Set<String> scopes(Jwt jwt, String claimName) {
        Object value = jwt.getClaim(claimName);
        Set<String> scopes = new LinkedHashSet<>();
        if (value instanceof String stringValue) {
            for (String scope : stringValue.trim().split("\\s+")) {
                if (!scope.isBlank()) {
                    scopes.add(scope);
                }
            }
        } else if (value instanceof Collection<?> collection) {
            for (Object scope : collection) {
                if (scope instanceof String stringValue && !stringValue.isBlank()) {
                    scopes.add(stringValue);
                }
            }
        }
        return scopes;
    }

    private record RequiredExpirationValidator() implements OAuth2TokenValidator<Jwt> {
        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            return token.getExpiresAt() == null ? failure("JWT must contain exp") : success();
        }
    }

    private record IssuerValidator(String issuer) implements OAuth2TokenValidator<Jwt> {
        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            return issuer.equals(token.getIssuer() == null ? null : token.getIssuer().toString())
                    ? success()
                    : failure("JWT issuer does not match");
        }
    }

    private record AudienceValidator(Set<String> audiences) implements OAuth2TokenValidator<Jwt> {
        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            return token.getAudience().stream().anyMatch(audiences::contains)
                    ? success()
                    : failure("JWT audience does not match");
        }
    }

    private record ScopeShapeValidator(String claimName) implements OAuth2TokenValidator<Jwt> {
        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            Object value = token.getClaim(claimName);
            if (value == null || value instanceof String) {
                return success();
            }
            boolean valid = value instanceof Collection<?> values
                    && values.stream().allMatch(String.class::isInstance);
            return valid ? success() : failure("JWT scope claim has an unsupported type");
        }
    }

    private record HeaderAndClaimsValidator(ServiceProperties.Jwt properties)
            implements OAuth2TokenValidator<Jwt> {

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            for (Map.Entry<String, String> expected : properties.getHeaderClaims().entrySet()) {
                if (!expected.getValue().equals(String.valueOf(token.getHeaders().get(expected.getKey())))) {
                    return failure("JWT header " + expected.getKey() + " does not match");
                }
            }

            for (Map.Entry<String, String> expected : properties.getClaimEquals().entrySet()) {
                Object actual = valueAt(token.getClaims(), expected.getKey());
                if (!isScalar(actual) || !expected.getValue().equals(String.valueOf(actual))) {
                    return failure("JWT claim " + expected.getKey() + " does not match");
                }
            }
            for (Map.Entry<String, String> expected : properties.getClaimContains().entrySet()) {
                Object actual = valueAt(token.getClaims(), expected.getKey());
                boolean matches = actual instanceof Collection<?> values
                        && values.stream().anyMatch(value -> expected.getValue().equals(String.valueOf(value)));
                if (!matches) {
                    return failure("JWT claim " + expected.getKey() + " does not contain required value");
                }
            }
            return success();
        }

        private static boolean isScalar(Object value) {
            return value instanceof String || value instanceof Number || value instanceof Boolean;
        }

        private static Object valueAt(Map<String, Object> claims, String configuredPointer) {
            String pointer = configuredPointer.startsWith("/") ? configuredPointer : "/" + configuredPointer;
            Object value = claims;
            for (String segment : pointer.substring(1).split("/")) {
                String key = segment.replace("~1", "/").replace("~0", "~");
                if (value instanceof Map<?, ?> map) {
                    value = map.get(key);
                } else if (value instanceof java.util.List<?> values && key.matches("\\d+")) {
                    int index = Integer.parseInt(key);
                    value = index < values.size() ? values.get(index) : null;
                } else {
                    return null;
                }
            }
            return value;
        }
    }

    private static OAuth2TokenValidatorResult success() {
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }
}
