package com.example.serviceapp;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class OAuthTokenClient {

    private final RestClient restClient;
    private final ServiceProperties properties;
    private volatile CachedToken cachedToken;

    OAuthTokenClient(RestClient.Builder builder, ServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getTokenResponseTimeout());
        this.restClient = builder.requestFactory(requestFactory).build();
        this.properties = properties;
    }

    String accessToken() {
        CachedToken current = cachedToken;
        if (current != null && current.isUsable()) {
            return current.accessToken();
        }
        synchronized (this) {
            current = cachedToken;
            if (current != null && current.isUsable()) {
                return current.accessToken();
            }
            CachedToken refreshed = requestToken();
            cachedToken = refreshed.cacheable() ? refreshed : null;
            return refreshed.accessToken();
        }
    }

    void invalidate() {
        cachedToken = null;
    }

    private CachedToken requestToken() {
        LinkedMultiValueMap<String, String> request = new LinkedMultiValueMap<>();
        request.add("grant_type", "client_credentials");
        request.add("scope", String.join(" ", properties.getPeer().getRequiredScopes()));
        try {
            TokenResponse token = restClient.post()
                    .uri(properties.getOauth().getTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .headers(headers -> headers.setBasicAuth(
                            properties.getOauth().getClientId(), properties.getOauth().getClientSecret()))
                    .body(request)
                    .retrieve()
                    .body(TokenResponse.class);
            if (token == null || token.accessToken() == null || token.accessToken().isBlank()
                    || token.expiresIn() == null || token.expiresIn() <= 0
                    || token.tokenType() != null && !"bearer".equalsIgnoreCase(token.tokenType())) {
                throw new OutboundDependencyException("Token endpoint returned an invalid OAuth response");
            }
            Instant expiresAt = Instant.now().plusSeconds(token.expiresIn());
            return new CachedToken(token.accessToken(), expiresAt.minus(properties.getOauth().getRefreshSkew()), token.expiresIn());
        } catch (RestClientException exception) {
            throw new OutboundDependencyException("Token endpoint is unavailable", exception);
        }
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Long expiresIn) {
    }

    private record CachedToken(String accessToken, Instant refreshAt, long expiresInSeconds) {
        boolean isUsable() {
            return Instant.now().isBefore(refreshAt);
        }

        boolean cacheable() {
            return expiresInSeconds > 0 && Instant.now().isBefore(refreshAt);
        }
    }
}
