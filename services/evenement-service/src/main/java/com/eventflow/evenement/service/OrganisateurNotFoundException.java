package com.eventflow.evenement.service;

public class OrganisateurNotFoundException extends RuntimeException {

    public OrganisateurNotFoundException(Long organisateurId) {
        super("Organizer not found: " + organisateurId);
    }
}
