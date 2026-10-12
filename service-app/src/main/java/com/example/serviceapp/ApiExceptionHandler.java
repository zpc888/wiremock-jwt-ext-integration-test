package com.example.serviceapp;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(OutboundDependencyException.class)
    ProblemDetail peerUnavailable(OutboundDependencyException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "A required upstream service is unavailable");
        problem.setProperty("code", "peer_unavailable");
        problem.setProperty("requestId", RequestIdFilter.requestId(request));
        return problem;
    }

    @ExceptionHandler(SelfPrivateApiInvocationException.class)
    ProblemDetail selfPrivateApiInvocation(HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "An application cannot call its own private API");
        problem.setProperty("code", "self_private_call");
        problem.setProperty("requestId", RequestIdFilter.requestId(request));
        return problem;
    }
}
