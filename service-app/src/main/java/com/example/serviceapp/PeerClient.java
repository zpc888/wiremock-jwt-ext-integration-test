package com.example.serviceapp;

import java.lang.reflect.Type;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Component
class PeerClient {
    private static final Logger LOG = LoggerFactory.getLogger(PeerClient.class);

    private final RestClient restClient;
    private final OAuthTokenClient tokenClient;
    private final ServiceProperties properties;
    private final Function<String, Map<String, Object>> fnBase64JsonToMap;

    PeerClient(RestClient.Builder builder, OAuthTokenClient tokenClient, ServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getPeerResponseTimeout());
        this.restClient = builder.requestFactory(requestFactory).build();
        this.tokenClient = tokenClient;
        this.properties = properties;
        JsonMapper jsonMapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        this.fnBase64JsonToMap = s -> {
            byte[] bytes = Base64.getDecoder().decode(s);
            return jsonMapper.readValue(bytes, new TypeReference<Map<String, Object>>() {
                @Override
                public Type getType() {
                    return super.getType();
                }
            });
        };
    }

    private Map<String, Object> parseJwt(String jwt) {
        if (jwt == null || jwt.isEmpty()) {
            return null;
        }
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) {
            return Map.of("raw", jwt);
        } else {
            Map<String, Object> ret = new HashMap<>();
            ret.put("hdr", fnBase64JsonToMap.apply(parts[0]));
            ret.put("pld", fnBase64JsonToMap.apply(parts[1]));
            ret.put("sig", parts[2]);
            return ret;
        }
    }

    Map<String, Object> privateResponse(String requestId) {
        try {
            String accessToken = tokenClient.accessToken();
            LOG.debug("Access Token: {}", accessToken);
            Map<String, Object> ret = new HashMap<>();
            ret.put("jwt_json", parseJwt(accessToken));

            Map<String, Object> privateResp = restClient.get()
                    .uri(properties.getPeer().getBaseUrl().resolve("/api/private"))
                    .headers(headers -> {
                        headers.setBearerAuth(accessToken);
                        headers.set(RequestIdFilter.HEADER, requestId);
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        if (response.getStatusCode().value() == 401 || response.getStatusCode().value() == 403) {
                            tokenClient.invalidate();
                        }
                        throw new OutboundDependencyException("Peer rejected or failed the request");
                    })
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {
                    });
            if (privateResp != null) {
                ret.putAll(privateResp);
            }
            return ret;
        } catch (OutboundDependencyException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new OutboundDependencyException("Peer is unavailable", exception);
        }
    }
}
