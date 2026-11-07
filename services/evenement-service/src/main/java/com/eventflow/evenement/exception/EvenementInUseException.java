package com.eventflow.evenement.exception;

public class EvenementInUseException extends RuntimeException {

    public EvenementInUseException(Long evenementId) {
        super("Event is referenced by another resource: " + evenementId);
    }
}
