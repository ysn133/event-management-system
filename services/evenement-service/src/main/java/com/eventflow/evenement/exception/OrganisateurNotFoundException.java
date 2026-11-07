package com.eventflow.evenement.exception;

public class OrganisateurNotFoundException extends RuntimeException {

    public OrganisateurNotFoundException(Long organisateurId) {
        super("Organizer not found: " + organisateurId);
    }
}
