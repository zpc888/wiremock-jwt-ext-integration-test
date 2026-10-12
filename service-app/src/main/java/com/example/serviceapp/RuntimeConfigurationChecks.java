package com.example.serviceapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RuntimeConfigurationChecks {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeConfigurationChecks.class);

    @Bean
    ApplicationRunner validateRuntimeConfiguration(ServiceProperties properties) {
        return arguments -> {
            if (properties.getInstanceId().equals(properties.getPeer().getInstanceId())) {
                throw new IllegalStateException("service.instance-id and service.peer.instance-id must differ");
            }
            warnForHttp(properties.getPeer().getBaseUrl().toString(), "peer URL");
            warnForHttp(properties.getOauth().getTokenUrl().toString(), "token URL");
            if (properties.getJwt().getVerificationMode() == ServiceProperties.VerificationMode.JWKS) {
                warnForHttp(properties.getJwt().getJwks().getJwkSetUrl().toString(), "JWK set URL");
            }
        };
    }

    private static void warnForHttp(String value, String name) {
        if (value.startsWith("http://")) {
            LOGGER.warn("{} uses HTTP; rely on a trusted mesh or sidecar for transport encryption", name);
        }
    }
}
