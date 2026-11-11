package com.eventflow.budget.dto;

import java.math.BigDecimal;

import com.eventflow.budget.domain.Depense;

public record DepensePrincipaleResponse(
        Long id,
        String description,
        BigDecimal montant,
        String categorie,
        String statut) {

    public static DepensePrincipaleResponse from(Depense depense) {
        return new DepensePrincipaleResponse(
                depense.getId(),
                depense.getDescription(),
                depense.getMontant(),
                depense.getCategorie(),
                depense.getStatut());
    }
}
