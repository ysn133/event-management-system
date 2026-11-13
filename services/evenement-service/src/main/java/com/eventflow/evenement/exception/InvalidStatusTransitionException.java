package com.eventflow.evenement.exception;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String currentStatus, String requestedStatus) {
        super("Event status cannot change from " + currentStatus + " to " + requestedStatus);
    }
}
