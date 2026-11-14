package com.eventflow.budget.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BudgetCreateRequest(
        @NotNull @Positive Long evenementId,
        @NotNull @DecimalMin(value = "0.00", inclusive = false)
        @Digits(integer = 17, fraction = 2) BigDecimal montantTotal,
        Boolean depassementAutorise) {
    public BudgetCreateRequest(Long evenementId, BigDecimal montantTotal) {
        this(evenementId, montantTotal, true);
    }
}
