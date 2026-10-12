package com.example.serviceapp;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class JwtItController {

    private final ServiceProperties properties;
    private final PeerClient peerClient;

    JwtItController(ServiceProperties properties, PeerClient peerClient) {
        this.properties = properties;
        this.peerClient = peerClient;
    }

    @GetMapping("/api/public")
    PublicResponse publicApi(HttpServletRequest request) {
        return new PublicResponse(
                properties.getInstanceId(),
                properties.getPeer().getInstanceId(),
                peerClient.privateResponse(RequestIdFilter.requestId(request)));
    }

    @GetMapping("/api/private")
    PrivateResponse privateApi() {
        return new PrivateResponse(properties.getInstanceId(), "private response");
    }

    record PublicResponse(String instanceId, String peerInstanceId, Map<String, Object> peerResponse) {
    }

    record PrivateResponse(String instanceId, String message) {
    }
}
