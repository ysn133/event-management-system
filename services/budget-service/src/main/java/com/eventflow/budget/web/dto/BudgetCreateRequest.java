package com.eventflow.budget.web.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BudgetCreateRequest(
        @NotNull(message = "must not be null")
        @Positive(message = "must be positive")
        Long evenementId,

        @NotNull(message = "must not be null")
        @DecimalMin(value = "0.00", inclusive = false, message = "must be strictly positive")
        @Digits(integer = 17, fraction = 2, message = "must have at most two decimal places")
        BigDecimal montantTotal) {
}
