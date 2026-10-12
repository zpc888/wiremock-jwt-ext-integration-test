package com.example.serviceapp;

class OutboundDependencyException extends RuntimeException {

    OutboundDependencyException(String message, Throwable cause) {
        super(message, cause);
    }

    OutboundDependencyException(String message) {
        super(message);
    }
}
