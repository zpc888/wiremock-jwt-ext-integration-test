package com.example.serviceapp;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TwoInstanceJwksIntegrationTest {

    private static final WireMockServer WIREMOCK = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    private static final RSAKey SIGNING_KEY = createKey("trusted-key");
    private static final RSAKey UNTRUSTED_KEY = createKey("untrusted-key");
    private static final ConfigurableApplicationContext INSTANCE_B;
    private static final String INSTANCE_B_URL;

    static {
        WIREMOCK.start();
        INSTANCE_B = startInstance("instance-b", "instance-a", "http://localhost:1");
        INSTANCE_B_URL = "http://localhost:"
                + ((WebServerApplicationContext) INSTANCE_B).getWebServer().getPort();
    }

    @LocalServerPort
    private int instanceAPort;

    private final TestRestTemplate client = new TestRestTemplate();

    @BeforeAll
    static void stubIssuer() throws JOSEException {
        WIREMOCK.stubFor(get(urlEqualTo("/.well-known/jwks.json"))
                .willReturn(okJson(new JWKSet(SIGNING_KEY.toPublicJWK()).toString())));
        WIREMOCK.stubFor(post(urlEqualTo("/oauth/token"))
                .withHeader("Authorization", equalTo("Basic Y2FsbGVyLWE6c2VjcmV0"))
                .willReturn(okJson("""
                        {"access_token":"%s","token_type":"Bearer","expires_in":300}
                        """.formatted(signedToken("peer.private", SIGNING_KEY)))));
    }

    @AfterAll
    static void stopServers() {
        INSTANCE_B.close();
        WIREMOCK.stop();
    }

    @DynamicPropertySource
    static void configureInstanceA(DynamicPropertyRegistry registry) {
        registerCommonProperties(registry, "instance-a", "instance-b", () -> INSTANCE_B_URL);
    }

    @Test
    void publicApiUsesConfiguredPeerAndCachedOAuthToken() {
        ResponseEntity<Map> first = client.getForEntity(instanceAUrl() + "/api/public", Map.class);
        ResponseEntity<Map> second = client.getForEntity(instanceAUrl() + "/api/public", Map.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getHeaders().getFirst(RequestIdFilter.HEADER)).isNotBlank();
        assertThat(first.getBody()).containsEntry("instanceId", "instance-a")
                .containsEntry("peerInstanceId", "instance-b");
        assertThat((Map<String, Object>) first.getBody().get("peerResponse"))
                .containsEntry("instanceId", "instance-b");
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        WIREMOCK.verify(1, postRequestedFor(urlEqualTo("/oauth/token")));
    }

    @Test
    void privateApiRejectsWrongScopeAndInvalidSignature() throws JOSEException {
        assertThat(callPrivate(signedToken("other.scope", SIGNING_KEY)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(callPrivate(signedToken("peer.private", UNTRUSTED_KEY)).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void privateApiRejectsMismatchedConfiguredPayloadClaim() throws JOSEException {
        assertThat(callPrivate(signedToken("peer.private", SIGNING_KEY, "another-client")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<String> callPrivate(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return client.exchange(INSTANCE_B_URL + "/api/private", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private String instanceAUrl() {
        return "http://localhost:" + instanceAPort;
    }

    private static ConfigurableApplicationContext startInstance(String id, String peerId, String peerUrl) {
        return new SpringApplicationBuilder(ServiceApplication.class)
                .run(
                        "--server.port=0",
                        "--management.server.port=-1",
                        "--service.instance-id=" + id,
                        "--service.peer.base-url=" + peerUrl,
                        "--service.peer.instance-id=" + peerId,
                        "--service.peer.required-scopes=peer.private",
                        "--service.oauth.token-url=" + WIREMOCK.baseUrl() + "/oauth/token",
                        "--service.oauth.client-id=caller-a",
                        "--service.oauth.client-secret=secret",
                        "--service.jwt.verification-mode=JWKS",
                        "--service.jwt.issuer=https://issuer.example.test",
                        "--service.jwt.audiences=peer-api",
                        "--service.jwt.required-scopes=peer.private",
                        "--service.jwt.header-claims.typ=JWT",
                        "--service.jwt.claim-equals./client_id=caller-a",
                        "--service.jwt.claim-contains./tenants=blue",
                        "--service.jwt.jwks.jwk-set-url=" + WIREMOCK.baseUrl() + "/.well-known/jwks.json");
    }

    private static void registerCommonProperties(
            DynamicPropertyRegistry registry, String id, String peerId, java.util.function.Supplier<String> peerUrl) {
        registry.add("management.server.port", () -> -1);
        registry.add("service.instance-id", () -> id);
        registry.add("service.peer.base-url", () -> peerUrl.get());
        registry.add("service.peer.instance-id", () -> peerId);
        registry.add("service.peer.required-scopes", () -> "peer.private");
        registry.add("service.oauth.token-url", () -> WIREMOCK.baseUrl() + "/oauth/token");
        registry.add("service.oauth.client-id", () -> "caller-a");
        registry.add("service.oauth.client-secret", () -> "secret");
        registry.add("service.jwt.verification-mode", () -> "JWKS");
        registry.add("service.jwt.issuer", () -> "https://issuer.example.test");
        registry.add("service.jwt.audiences", () -> "peer-api");
        registry.add("service.jwt.required-scopes", () -> "peer.private");
        registry.add("service.jwt.header-claims.typ", () -> "JWT");
        registry.add("service.jwt.claim-equals./client_id", () -> "caller-a");
        registry.add("service.jwt.claim-contains./tenants", () -> "blue");
        registry.add("service.jwt.jwks.jwk-set-url", () -> WIREMOCK.baseUrl() + "/.well-known/jwks.json");
    }

    private static RSAKey createKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Could not generate test key", exception);
        }
    }

    private static String signedToken(String scope, RSAKey signingKey) throws JOSEException {
        return signedToken(scope, signingKey, "caller-a");
    }

    private static String signedToken(String scope, RSAKey signingKey, String clientId) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://issuer.example.test")
                .audience("peer-api")
                .subject("service-a")
                .claim("scope", scope)
                .claim("client_id", clientId)
                .claim("tenants", java.util.List.of("blue"))
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(com.nimbusds.jose.JOSEObjectType.JWT)
                .keyID(signingKey.getKeyID())
                .build(), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }
}
