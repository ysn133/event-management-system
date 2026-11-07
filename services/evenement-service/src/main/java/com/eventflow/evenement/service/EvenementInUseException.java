package com.eventflow.evenement.service;

public class EvenementInUseException extends RuntimeException {

    public EvenementInUseException(Long evenementId) {
        super("Event is referenced by another resource: " + evenementId);
    }
}
