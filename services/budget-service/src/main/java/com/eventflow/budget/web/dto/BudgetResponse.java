package com.eventflow.budget.web.dto;

import java.math.BigDecimal;

import com.eventflow.budget.domain.Budget;

public record BudgetResponse(
        Long id,
        Long evenementId,
        BigDecimal montantTotal) {

    public static BudgetResponse from(Budget budget) {
        return new BudgetResponse(
                budget.getId(),
                budget.getEvenementId(),
                budget.getMontantTotal());
    }
}
