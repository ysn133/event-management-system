package com.eventflow.budget.dto;

import java.math.BigDecimal;

import com.eventflow.budget.domain.NiveauAlerteBudget;

public record BilanFinancierResponse(
        Long budgetId,
        Long evenementId,
        BigDecimal montantTotal,
        BigDecimal totalDepenses,
        BigDecimal totalPrevu,
        BigDecimal totalEngage,
        BigDecimal totalPaye,
        BigDecimal soldeRestant,
        boolean depassement,
        BigDecimal pourcentageConsommation,
        NiveauAlerteBudget niveauAlerte) {
}
