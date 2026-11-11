package com.eventflow.budget.dto;

import java.math.BigDecimal;

import com.eventflow.budget.domain.Budget;

public record BudgetResponse(
        Long id,
        Long evenementId,
        BigDecimal montantTotal,
        String statut) {

    public static BudgetResponse from(Budget budget) {
        return new BudgetResponse(
                budget.getId(),
                budget.getEvenementId(),
                budget.getMontantTotal(),
                budget.getStatut());
    }
}
