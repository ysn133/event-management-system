package com.eventflow.evenement.exception;

public class EvenementNotFoundException extends RuntimeException {

    public EvenementNotFoundException(Long evenementId) {
        super("Event not found: " + evenementId);
    }
}
