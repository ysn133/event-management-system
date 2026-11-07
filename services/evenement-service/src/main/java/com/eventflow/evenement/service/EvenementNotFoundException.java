package com.eventflow.evenement.service;

public class EvenementNotFoundException extends RuntimeException {

    public EvenementNotFoundException(Long evenementId) {
        super("Event not found: " + evenementId);
    }
}
