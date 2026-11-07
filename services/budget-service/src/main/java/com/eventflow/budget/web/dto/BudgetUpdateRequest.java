package com.eventflow.budget.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record BudgetUpdateRequest(
        @NotNull(message = "must not be null")
        @DecimalMin(value = "0.00", inclusive = false, message = "must be strictly positive")
        @Digits(integer = 17, fraction = 2, message = "must have at most two decimal places")
        BigDecimal montantTotal) {
}
