package com.eventflow.budget.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DepenseUpdateRequest(
        @NotBlank(message = "must not be blank")
        @Size(max = 255, message = "must contain at most 255 characters")
        String description,

        @NotNull(message = "must not be null")
        @DecimalMin(value = "0.00", inclusive = false, message = "must be strictly positive")
        @Digits(integer = 17, fraction = 2, message = "must have at most two decimal places")
        BigDecimal montant,

        @NotBlank(message = "must not be blank")
        @Size(max = 100, message = "must contain at most 100 characters")
        String categorie,

        @NotNull(message = "must not be null")
        LocalDate dateDepense,

        @Size(max = 50, message = "must contain at most 50 characters")
        @Pattern(regexp = ".*\\S.*", message = "must not be blank")
        String statut) {
}
