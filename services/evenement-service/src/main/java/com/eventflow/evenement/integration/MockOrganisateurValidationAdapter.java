package com.eventflow.evenement.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "integrations.organisateurs.mode",
        havingValue = "mock")
public class MockOrganisateurValidationAdapter implements OrganisateurValidationPort {

    @Override
    public boolean existsById(Long organisateurId) {
        return organisateurId != null && organisateurId > 0;
    }
}
