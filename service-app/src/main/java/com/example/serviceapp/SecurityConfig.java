package com.example.serviceapp;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Collection;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
class SecurityConfig {

    @Bean
    JwtDecoder jwtDecoder(ServiceProperties properties) {
        NimbusJwtDecoder decoder = switch (properties.getJwt().getVerificationMode()) {
            case JWKS -> NimbusJwtDecoder.withJwkSetUri(properties.getJwt().getJwks().getJwkSetUrl().toString())
                    .restOperations(jwksRestTemplate(properties))
                    .jwsAlgorithm(SignatureAlgorithm.RS256)
                    .build();
            case KEYSTORE -> NimbusJwtDecoder.withPublicKey(loadPublicKey(properties)).build();
        };
        decoder.setJwtValidator(JwtValidation.validator(properties));
        return decoder;
    }

    private static RestTemplate jwksRestTemplate(ServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getJwksResponseTimeout());
        return new RestTemplate(requestFactory);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            PrivateApiAuthorizationManager privateApiAuthorizationManager,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/public").permitAll()
                        .requestMatchers("/api/private").access(privateApiAuthorizationManager)
                        .requestMatchers("/actuator/health/**", "/actuator/metrics/**").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint))
                .build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(ServiceProperties properties) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<org.springframework.security.core.GrantedAuthority> authorities = new java.util.ArrayList<>();
            JwtValidation.scopes(jwt, properties.getJwt().getScopeClaim())
                    .forEach(scope -> authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_" + scope)));
            return authorities;
        });
        return converter;
    }

    @Bean
    AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, exception) -> writeProblem(
                response, objectMapper, HttpStatus.UNAUTHORIZED, "invalid_token", request.getHeader(RequestIdFilter.HEADER));
    }

    @Bean
    AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, exception) -> writeProblem(
                response, objectMapper, HttpStatus.FORBIDDEN, "insufficient_scope", request.getHeader(RequestIdFilter.HEADER));
    }

    private static RSAPublicKey loadPublicKey(ServiceProperties properties) {
        ServiceProperties.Keystore settings = properties.getJwt().getKeystore();
        try (InputStream input = settings.getLocation().getInputStream()) {
            KeyStore keyStore = KeyStore.getInstance(settings.getType());
            keyStore.load(input, settings.getPassword().toCharArray());
            Certificate certificate = keyStore.getCertificate(settings.getAlias());
            if (certificate == null || !(certificate.getPublicKey() instanceof RSAPublicKey rsaPublicKey)) {
                throw new IllegalStateException("Keystore alias must contain an RSA certificate");
            }
            return rsaPublicKey;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load RSA certificate from configured keystore", exception);
        }
    }

    private static void writeProblem(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            String code,
            String requestId) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        if (requestId != null) {
            response.setHeader(RequestIdFilter.HEADER, requestId);
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, status.getReasonPhrase());
        problem.setProperty("code", code);
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
