package com.example.serviceapp;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class JwtItController {

    private final PeerClient peerClient;
    private final String applicationName;

    JwtItController(
            PeerClient peerClient,
            @Value("${spring.application.name}") String applicationName) {
        this.peerClient = peerClient;
        this.applicationName = applicationName;
    }

    @GetMapping("/api/public")
    PublicResponse publicApi(HttpServletRequest request) {
        return new PublicResponse(
                applicationName,
                peerClient.privateResponse(RequestIdFilter.requestId(request)));
    }

    @GetMapping("/api/private")
    PrivateResponse privateApi(
            @RequestHeader(PeerClient.CALLER_APPLICATION_NAME_HEADER) String callerApplicationName) {
        if (applicationName.equals(callerApplicationName)) {
            throw new SelfPrivateApiInvocationException();
        }
        return new PrivateResponse(applicationName, "private response");
    }

    record PublicResponse(String applicationName, Map<String, Object> peerResponse) {
    }

    record PrivateResponse(String applicationName, String message) {
    }
}
