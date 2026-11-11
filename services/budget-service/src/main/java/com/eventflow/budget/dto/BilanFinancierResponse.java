package com.eventflow.budget.dto;

import java.math.BigDecimal;

public record BilanFinancierResponse(
        Long budgetId,
        Long evenementId,
        BigDecimal montantTotal,
        BigDecimal totalDepenses,
        BigDecimal totalPrevu,
        BigDecimal totalEngage,
        BigDecimal totalPaye,
        BigDecimal soldeRestant,
        boolean depassement) {
}
