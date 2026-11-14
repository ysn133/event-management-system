package com.eventflow.budget.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record BudgetUpdateRequest(
        @NotNull @DecimalMin(value = "0.00", inclusive = false)
        @Digits(integer = 17, fraction = 2) BigDecimal montantTotal,
        Boolean depassementAutorise) {
    public BudgetUpdateRequest(BigDecimal montantTotal) {
        this(montantTotal, null);
    }
}
