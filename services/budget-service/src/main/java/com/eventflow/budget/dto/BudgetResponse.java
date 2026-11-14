package com.eventflow.budget.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.eventflow.budget.domain.Budget;

public record BudgetResponse(
        Long id,
        Long evenementId,
        BigDecimal montantTotal,
        String statut,
        boolean depassementAutorise,
        Instant createdAt,
        Instant updatedAt) {
    public static BudgetResponse from(Budget budget) {
        return new BudgetResponse(
                budget.getId(), budget.getEvenementId(), budget.getMontantTotal(),
                budget.getStatut(), budget.isDepassementAutorise(),
                budget.getCreatedAt(), budget.getUpdatedAt());
    }
}
