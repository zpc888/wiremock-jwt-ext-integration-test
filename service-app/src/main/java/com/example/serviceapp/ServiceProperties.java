package com.example.serviceapp;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("service")
public class ServiceProperties {

    @Valid
    private Peer peer = new Peer();

    @Valid
    private OAuth oauth = new OAuth();

    @Valid
    private Jwt jwt = new Jwt();

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(2);

    @NotNull
    private Duration peerResponseTimeout = Duration.ofSeconds(10);

    @NotNull
    private Duration tokenResponseTimeout = Duration.ofSeconds(5);

    @NotNull
    private Duration jwksResponseTimeout = Duration.ofSeconds(5);

    public Peer getPeer() {
        return peer;
    }

    public void setPeer(Peer peer) {
        this.peer = peer;
    }

    public OAuth getOauth() {
        return oauth;
    }

    public void setOauth(OAuth oauth) {
        this.oauth = oauth;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getPeerResponseTimeout() {
        return peerResponseTimeout;
    }

    public void setPeerResponseTimeout(Duration peerResponseTimeout) {
        this.peerResponseTimeout = peerResponseTimeout;
    }

    public Duration getTokenResponseTimeout() {
        return tokenResponseTimeout;
    }

    public void setTokenResponseTimeout(Duration tokenResponseTimeout) {
        this.tokenResponseTimeout = tokenResponseTimeout;
    }

    public Duration getJwksResponseTimeout() {
        return jwksResponseTimeout;
    }

    public void setJwksResponseTimeout(Duration jwksResponseTimeout) {
        this.jwksResponseTimeout = jwksResponseTimeout;
    }

    public static class Peer {
        @NotNull
        private URI baseUrl;

        @NotEmpty
        private Set<@NotBlank String> requiredScopes = new LinkedHashSet<>();

        public URI getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(URI baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Set<String> getRequiredScopes() {
            return requiredScopes;
        }

        public void setRequiredScopes(Set<String> requiredScopes) {
            this.requiredScopes = requiredScopes;
        }
    }

    public static class OAuth {
        @NotNull
        private URI tokenUrl;

        @NotBlank
        private String clientId;

        @NotBlank
        private String clientSecret;

        @NotNull
        private Duration refreshSkew = Duration.ofSeconds(30);

        public URI getTokenUrl() {
            return tokenUrl;
        }

        public void setTokenUrl(URI tokenUrl) {
            this.tokenUrl = tokenUrl;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public Duration getRefreshSkew() {
            return refreshSkew;
        }

        public void setRefreshSkew(Duration refreshSkew) {
            this.refreshSkew = refreshSkew;
        }
    }

    public static class Jwt {
        @NotNull
        private VerificationMode verificationMode;

        @NotBlank
        private String issuer;

        @NotEmpty
        private Set<@NotBlank String> audiences = new LinkedHashSet<>();

        @NotEmpty
        private Set<@NotBlank String> requiredScopes = new LinkedHashSet<>();

        @NotBlank
        private String scopeClaim = "scope";

        @NotNull
        private Duration clockSkew = Duration.ofSeconds(60);

        private Map<String, String> headerClaims = new LinkedHashMap<>();

        private Map<String, String> claimEquals = new LinkedHashMap<>();

        private Map<String, String> claimContains = new LinkedHashMap<>();

        @Valid
        private Jwks jwks = new Jwks();

        @Valid
        private Keystore keystore = new Keystore();

        public VerificationMode getVerificationMode() {
            return verificationMode;
        }

        public void setVerificationMode(VerificationMode verificationMode) {
            this.verificationMode = verificationMode;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public Set<String> getAudiences() {
            return audiences;
        }

        public void setAudiences(Set<String> audiences) {
            this.audiences = audiences;
        }

        public Set<String> getRequiredScopes() {
            return requiredScopes;
        }

        public void setRequiredScopes(Set<String> requiredScopes) {
            this.requiredScopes = requiredScopes;
        }

        public String getScopeClaim() {
            return scopeClaim;
        }

        public void setScopeClaim(String scopeClaim) {
            this.scopeClaim = scopeClaim;
        }

        public Duration getClockSkew() {
            return clockSkew;
        }

        public void setClockSkew(Duration clockSkew) {
            this.clockSkew = clockSkew;
        }

        public Map<String, String> getHeaderClaims() {
            return headerClaims;
        }

        public void setHeaderClaims(Map<String, String> headerClaims) {
            this.headerClaims = headerClaims;
        }

        public Map<String, String> getClaimEquals() {
            return claimEquals;
        }

        public void setClaimEquals(Map<String, String> claimEquals) {
            this.claimEquals = claimEquals;
        }

        public Map<String, String> getClaimContains() {
            return claimContains;
        }

        public void setClaimContains(Map<String, String> claimContains) {
            this.claimContains = claimContains;
        }

        public Jwks getJwks() {
            return jwks;
        }

        public void setJwks(Jwks jwks) {
            this.jwks = jwks;
        }

        public Keystore getKeystore() {
            return keystore;
        }

        public void setKeystore(Keystore keystore) {
            this.keystore = keystore;
        }

        @AssertTrue(message = "jwt verification mode requires its corresponding configuration")
        public boolean isVerificationConfigurationValid() {
            return verificationMode == null
                    || verificationMode == VerificationMode.JWKS && jwks.jwkSetUrl != null
                    || verificationMode == VerificationMode.KEYSTORE && keystore.location != null
                    && keystore.password != null && !keystore.password.isBlank()
                    && keystore.alias != null && !keystore.alias.isBlank();
        }
    }

    public static class Jwks {
        private URI jwkSetUrl;

        public URI getJwkSetUrl() {
            return jwkSetUrl;
        }

        public void setJwkSetUrl(URI jwkSetUrl) {
            this.jwkSetUrl = jwkSetUrl;
        }
    }

    public static class Keystore {
        private Resource location;
        private String type = "PKCS12";
        private String password;
        private String alias;

        public Resource getLocation() {
            return location;
        }

        public void setLocation(Resource location) {
            this.location = location;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }
    }

    public enum VerificationMode {
        JWKS,
        KEYSTORE
    }
}
