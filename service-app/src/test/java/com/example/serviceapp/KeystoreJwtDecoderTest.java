package com.example.serviceapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class KeystoreJwtDecoderTest {

    private static final String KEYSTORE_PASSWORD = "changeit";
    private static final Path KEYSTORE_PATH = Path.of(
            System.getProperty("java.io.tmpdir"), "service-app-jwt-verifier.p12");

    @Test
    void verifiesRs256JwtWithConfiguredPkcs12Certificate() throws Exception {
        KeyPair trusted = keyPair();
        Path keyStore = createKeyStoreAtConfiguredLocation(trusted);
        JwtDecoder decoder = new SecurityConfig().jwtDecoder(properties(keyStore));

        assertThat(keyStore).exists();
        assertThat(decoder.decode(signedToken(trusted.getPrivate(), "trusted-key")).getSubject()).isEqualTo("service-a");
        assertThatThrownBy(() -> decoder.decode(signedToken(keyPair().getPrivate(), "other-key")))
                .isInstanceOf(JwtException.class);
    }

    private static ServiceProperties properties(Path keyStore) {
        ServiceProperties properties = new ServiceProperties();
        ServiceProperties.Jwt jwt = properties.getJwt();
        jwt.setVerificationMode(ServiceProperties.VerificationMode.KEYSTORE);
        jwt.setIssuer("https://issuer.example.test");
        jwt.setAudiences(Set.of("peer-api"));
        jwt.setRequiredScopes(Set.of("peer.private"));
        jwt.setHeaderClaims(java.util.Map.of("typ", "JWT"));
        jwt.setClaimEquals(java.util.Map.of("/client_id", "caller-a", "/user_name", "george"));
        jwt.setClaimContains(java.util.Map.of("/tenants", "blue"));
        jwt.getKeystore().setLocation(new FileSystemResource(keyStore));
        jwt.getKeystore().setType("PKCS12");
        jwt.getKeystore().setPassword(KEYSTORE_PASSWORD);
        jwt.getKeystore().setAlias("jwt");
        return properties;
    }

    private static Path createKeyStoreAtConfiguredLocation(KeyPair keyPair) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, KEYSTORE_PASSWORD.toCharArray());
        keyStore.setCertificateEntry("jwt", certificate(keyPair));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        keyStore.store(output, KEYSTORE_PASSWORD.toCharArray());
        Files.write(KEYSTORE_PATH, output.toByteArray());
        KEYSTORE_PATH.toFile().deleteOnExit();
        return KEYSTORE_PATH;
    }

    private static X509Certificate certificate(KeyPair keyPair) throws Exception {
        X500Name name = new X500Name("CN=JWT verifier");
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        X509CertificateHolder holder = new JcaX509v3CertificateBuilder(
                name,
                BigInteger.ONE,
                Date.from(Instant.now().minusSeconds(60)),
                Date.from(Instant.now().plusSeconds(3600)),
                name,
                keyPair.getPublic()).build(signer);
        return new JcaX509CertificateConverter().getCertificate(holder);
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String signedToken(PrivateKey privateKey, String keyId) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://issuer.example.test")
                .audience("peer-api")
                .subject("service-a")
                .claim("scope", java.util.List.of("peer.private"))
                .claim("client_id", "caller-a")
                .claim("tenants", java.util.List.of("blue"))
                .claim("user_name", "george")
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(com.nimbusds.jose.JOSEObjectType.JWT)
                .keyID(keyId)
                .build(), claims);
        jwt.sign(new RSASSASigner(privateKey));
        return jwt.serialize();
    }
}
