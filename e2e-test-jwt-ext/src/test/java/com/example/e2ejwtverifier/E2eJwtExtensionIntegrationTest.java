package com.example.e2ejwtverifier;

import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.text.ParseException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class E2eJwtExtensionIntegrationTest {

    private static final String JWKS_PUBLIC_RESPONSE = """
            {
              "applicationName": "verify-jwt-sig-with-jwks",
              "peerResponse": {
                "applicationName": "verify-jwt-sig-with-keystore",
                "message": "private response"
              }
            }
            """;

    private static final String TRUSTSTORE_PUBLIC_RESPONSE = """
            {
              "applicationName": "verify-jwt-sig-with-keystore",
              "peerResponse": {
                "applicationName": "verify-jwt-sig-with-jwks",
                "message": "private response"
              }
            }
            """;

    private static final String HS256_HEADER = """
            {
              "alg": "HS256",
              "typ": "JWT",
              "cty": "application/json",
              "header": "header-key-in-jwt-header"
            }
            """;

    private static final String HS256_PAYLOAD = """
            {
              "iss": "https://issuer.example.test",
              "aud": "peer-api",
              "sub": "user-123",
              "scope": "peer.private",
              "client_id": "caller-a",
              "user_name": "george",
              "source_application": "e2e-verifier"
            }
            """;

    private static final int ATTEMPTS = 30;
    private static final long RETRY_DELAY_MILLIS = 2_000;

    private final RestClient restClient = RestClient.builder()
            .requestFactory(new SimpleClientHttpRequestFactory())
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void verifiesServiceResponsesAndHs256WireMockToken() {
        verifyWithRetry("JWKS public API", () -> verifyPublicResponse(
                hostUri("e2e.serviceJwksPort", "8181", "/api/public"), JWKS_PUBLIC_RESPONSE));
        verifyWithRetry("truststore public API", () -> verifyPublicResponse(
                hostUri("e2e.serviceKeystorePort", "8182", "/api/public"), TRUSTSTORE_PUBLIC_RESPONSE));
        verifyWithRetry("HS256 WireMock response", this::verifyHs256Token);
    }

    private void verifyPublicResponse(URI url, String expectedJson) {
        Map<String, Object> expected = parseMap(expectedJson);
        Map<String, Object> actual = stablePublicResponse(fetchMap(url));
        if (!expected.equals(actual)) {
            throw new VerificationFailure("Public API response did not match its expected JSON map");
        }
    }

    private Map<String, Object> stablePublicResponse(Map<String, Object> response) {
        Object applicationName = response.get("applicationName");
        Object peerResponse = response.get("peerResponse");
        if (!(applicationName instanceof String)
                || !(peerResponse instanceof Map<?, ?> peer)
                || !(peer.get("applicationName") instanceof String)
                || !(peer.get("message") instanceof String)) {
            throw new VerificationFailure("Public API response did not contain its stable response fields");
        }
        return Map.of(
                "applicationName", applicationName,
                "peerResponse", Map.of(
                        "applicationName", peer.get("applicationName"),
                        "message", peer.get("message")));
    }

    private void verifyHs256Token() {
        SignedJWT token = fetchHs256Token();
        String secret = fetchHs256Secret();
        try {
            if (!token.verify(new MACVerifier(secret))) {
                throw new VerificationFailure("HS256 JWT signature did not match the WireMock secret");
            }
            assertMap(HS256_HEADER, parseMap(token.getHeader().toString()), "HS256 JWT header");
            assertStaticPayload(token);
            assertTimeClaims(token.getJWTClaimsSet());
        } catch (JOSEException | ParseException exception) {
            throw new VerificationFailure("HS256 JWT could not be verified", exception);
        }
    }

    private SignedJWT fetchHs256Token() {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", "peer.private");
        form.add("source_app", "e2e-verifier");
        Map<String, Object> response = postForm(
                hostUri("e2e.wiremockJwtHeaderPort", "8187", "/oauth/token"), form);
        Object accessToken = response.get("access_token");
        if (!(accessToken instanceof String value) || value.isBlank()) {
            throw new VerificationFailure("HS256 token response omitted access_token");
        }
        try {
            return SignedJWT.parse(value);
        } catch (ParseException exception) {
            throw new VerificationFailure("HS256 token response contained a malformed JWT", exception);
        }
    }

    private String fetchHs256Secret() {
        Map<String, Object> settings = fetchMap(
                hostUri("e2e.wiremockJwtHeaderPort", "8187", "/__admin/settings"));
        Object configuredSettings = settings.get("settings");
        Object extended = configuredSettings instanceof Map<?, ?> map ? map.get("extended") : null;
        Object jwt = extended instanceof Map<?, ?> map ? map.get("jwt") : null;
        Object secret = jwt instanceof Map<?, ?> map ? map.get("hs256Secret") : null;
        if (!(secret instanceof String value) || value.isBlank()) {
            throw new VerificationFailure("WireMock admin settings omitted the HS256 secret");
        }
        return value;
    }

    private void assertStaticPayload(SignedJWT token) {
        Map<String, Object> expected = parseMap(HS256_PAYLOAD);
        Map<String, Object> payload = parseMap(token.getPayload().toString());
        Map<String, Object> actual = new LinkedHashMap<>();
        for (String key : expected.keySet()) {
            actual.put(key, payload.get(key));
        }
        if (!expected.equals(actual)) {
            throw new VerificationFailure("HS256 JWT payload did not match its expected JSON map");
        }
    }

    private void assertTimeClaims(JWTClaimsSet claims) {
        if (claims.getIssueTime() == null
                || claims.getExpirationTime() == null
                || !claims.getExpirationTime().toInstant().isAfter(Instant.now())) {
            throw new VerificationFailure("HS256 JWT time claims were invalid");
        }
    }

    private void assertMap(String expectedJson, Map<String, Object> actual, String description) {
        if (!parseMap(expectedJson).equals(actual)) {
            throw new VerificationFailure(description + " did not match its expected JSON map");
        }
    }

    private Map<String, Object> fetchMap(URI url) {
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            return parseMap(body);
        } catch (RestClientException exception) {
            throw new VerificationFailure("Required HTTP endpoint was unavailable", exception);
        }
    }

    private Map<String, Object> postForm(URI url, LinkedMultiValueMap<String, String> form) {
        try {
            String body = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .headers(headers -> headers.setBasicAuth("caller-a",
                            System.getProperty("e2e.oauthClientSecret", "secret")))
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return parseMap(body);
        } catch (RestClientException exception) {
            throw new VerificationFailure("HS256 token endpoint was unavailable", exception);
        }
    }

    private Map<String, Object> parseMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new VerificationFailure("HTTP response was not a JSON object", exception);
        }
    }

    private void verifyWithRetry(String description, Runnable verification) {
        VerificationFailure failure = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                verification.run();
                return;
            } catch (VerificationFailure exception) {
                failure = exception;
                pauseBeforeRetry();
            }
        }
        throw new VerificationFailure(description + " did not pass before its retry deadline", failure);
    }

    private static URI hostUri(String propertyName, String defaultPort, String path) {
        return URI.create("http://localhost:" + System.getProperty(propertyName, defaultPort) + path);
    }

    private static void pauseBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            fail("Verification was interrupted", exception);
        }
    }

    private static final class VerificationFailure extends RuntimeException {
        private VerificationFailure(String message) {
            super(message);
        }

        private VerificationFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
