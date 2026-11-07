package com.eventflow.budget.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.eventflow.budget.domain.Depense;

public record DepenseResponse(
        Long id,
        Long budgetId,
        String description,
        BigDecimal montant,
        String categorie,
        LocalDate dateDepense,
        String statut) {

    public static DepenseResponse from(Depense depense) {
        return new DepenseResponse(
                depense.getId(),
                depense.getBudget().getId(),
                depense.getDescription(),
                depense.getMontant(),
                depense.getCategorie(),
                depense.getDateDepense(),
                depense.getStatut());
    }
}
