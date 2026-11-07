package com.eventflow.budget.dto;

import java.math.BigDecimal;

public record BilanFinancierResponse(
        Long budgetId,
        Long evenementId,
        BigDecimal montantTotal,
        BigDecimal totalDepenses,
        BigDecimal soldeRestant,
        boolean depassement) {
}
